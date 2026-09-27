package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Contratos;
import com.andre.infnethub.contratos.MensagemDeIntegracao;
import com.andre.infnethub.contratos.SalaDeEspera;
import com.andre.infnethub.observabilidade.Rastreamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tira as mensagens da caixa de saída e as entrega ao RabbitMQ.
 *
 * <h2>A ordem das três etapas é o padrão inteiro</h2>
 * <ol>
 *   <li>publica e <strong>espera a confirmação do broker</strong>
 *       (<em>publisher confirm</em>);</li>
 *   <li>só então marca a linha como publicada;</li>
 *   <li>o commit grava a marca.</li>
 * </ol>
 * <p>Marcar antes de confirmar reintroduziria, no último passo, a perda que o
 * outbox existe para evitar. Por isso a espera é bloqueante: publicar sem
 * esperar seria dar por entregue algo que ainda pode falhar.
 *
 * <h2>Confirmado não basta: a mensagem também precisa ter destino</h2>
 * <p>O RabbitMQ confirma a publicação mesmo quando nenhuma fila está ligada
 * àquela chave — e descarta a mensagem em seguida. Com {@code mandatory}, ele a
 * devolve em vez de descartar, e o relay trata a devolução como falha: o evento
 * continua pendente até existir quem o receba. É o que impede o core de
 * "entregar" eventos ao vazio antes de o boletim ter declarado a fila dele.
 *
 * <h2>O que isso garante, e o que não garante</h2>
 * <p>Garante que <strong>nada se perde</strong>: qualquer falha deixa a linha
 * pendente para a rodada seguinte. Não garante entrega única: se o processo cair
 * entre a confirmação e o commit, o evento sai de novo. É entrega <em>pelo menos
 * uma vez</em>, por escolha — o consumidor descarta a repetição pelo
 * {@code messageId}.
 *
 * <h2>Ordem</h2>
 * <p>A ordem que importa é a de cada agregado — a {@code chave} —, não uma
 * ordem global. Quando um evento falha, os seguintes <strong>da mesma
 * chave</strong> ficam retidos até a próxima rodada — publicá-los publicaria
 * {@code UsuarioAtualizado} antes do {@code UsuarioCadastrado} que falhou. Os
 * eventos das outras chaves seguem normalmente.
 *
 * <h2>Vazão: ondas, e não uma mensagem de cada vez</h2>
 * <p>Esperar a confirmação de cada mensagem antes de publicar a próxima custa
 * uma ida e volta ao broker por mensagem, e com um lote fixo por rodada o relay
 * tinha teto: cinquenta mensagens a cada meio segundo. Agora cada lote sai em
 * <em>ondas</em>: numa onda vai a mensagem mais antiga pendente de cada chave,
 * todas publicadas em sequência, e só então as confirmações são esperadas — em
 * paralelo, porque o broker as devolve à medida que grava. A mensagem seguinte
 * de uma chave só entra na onda seguinte, depois de a anterior ter sido
 * confirmada: a ordem por chave continua garantida, e o custo de ida e volta
 * passa a ser pago uma vez por onda, e não uma vez por mensagem.
 *
 * <p>E a rodada não para no primeiro lote: enquanto os lotes vierem cheios e
 * nada falhar, ela busca o próximo, cada um na sua transação — um pico de
 * eventos é escoado de uma vez, e não ao ritmo do agendamento. O orçamento de
 * tempo impede que uma rodada monopolize a thread do agendador.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.relay.habilitado", havingValue = "true", matchIfMissing = true)
class RelayDoOutbox {

    private static final Logger log = LoggerFactory.getLogger(RelayDoOutbox.class);

    /**
     * Cabeçalhos no formato do binding AMQP do CloudEvents. Quem inspeciona a
     * fila no painel do RabbitMQ — ou uma ferramenta que fale CloudEvents — lê
     * id, tipo, origem e horário sem abrir o corpo da mensagem.
     */
    private static final String CE = "cloudEvents:";

    private final OutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final Rastreamento rastreamento;
    private final ObjectMapper json;
    private final TransactionTemplate transacao;
    private final String origem;
    private final long esperaConfirmacaoMs;
    private final int tamanhoDoLote;
    private final long orcamentoNanos;

    RelayDoOutbox(OutboxRepository outbox,
                  RabbitTemplate rabbit,
                  Rastreamento rastreamento,
                  ObjectMapper json,
                  TransactionTemplate transacao,
                  @Value("${spring.application.name}") String origem,
                  @Value("${app.outbox.espera-confirmacao-ms:5000}") long esperaConfirmacaoMs,
                  @Value("${app.outbox.lote:500}") int tamanhoDoLote,
                  @Value("${app.outbox.orcamento-ms:2000}") long orcamentoMs) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.rastreamento = rastreamento;
        this.json = json;
        this.transacao = transacao;
        this.origem = origem;
        this.esperaConfirmacaoMs = esperaConfirmacaoMs;
        this.tamanhoDoLote = tamanhoDoLote;
        this.orcamentoNanos = TimeUnit.MILLISECONDS.toNanos(orcamentoMs);
    }

    /** O resultado de um lote, para a rodada decidir se busca o próximo. */
    private record Lote(int lidas, int publicadas, int falhas, boolean brokerInacessivel) {
        boolean valeBuscarOutro(int tamanho) {
            return lidas == tamanho && falhas == 0 && !brokerInacessivel;
        }
    }

    /** Uma mensagem já enviada, esperando a confirmação do broker. */
    private record Envio(MensagemNoOutbox mensagem, CorrelationData confirmacao) {
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:500}")
    public void despachar() {
        long limite = System.nanoTime() + orcamentoNanos;
        int publicadas = 0;
        int lotes = 0;
        Lote lote;
        do {
            lote = transacao.execute(status -> despacharLote());
            publicadas += lote.publicadas();
            lotes++;
        } while (lote.valeBuscarOutro(tamanhoDoLote) && System.nanoTime() < limite);

        if (publicadas > 0) {
            log.info("{} evento(s) publicado(s) a partir do outbox em {} lote(s)", publicadas, lotes);
        }
    }

    private Lote despacharLote() {
        List<MensagemNoOutbox> lidas = outbox.findByPublicadoEmIsNullOrderByIdAsc(Limit.of(tamanhoDoLote));

        // Uma fila por chave, na ordem da tabela. LinkedHashMap: a primeira
        // chave a aparecer é a primeira a ser servida em cada onda.
        Map<String, Deque<MensagemNoOutbox>> porChave = new LinkedHashMap<>();
        for (MensagemNoOutbox m : lidas) {
            porChave.computeIfAbsent(m.getChave(), k -> new ArrayDeque<>()).add(m);
        }

        int publicadas = 0;
        int falhas = 0;
        boolean inacessivel = false;

        while (!porChave.isEmpty() && !inacessivel) {
            List<Envio> onda = new ArrayList<>(porChave.size());

            for (var it = porChave.entrySet().iterator(); it.hasNext(); ) {
                MensagemNoOutbox proxima = it.next().getValue().peek();
                try {
                    onda.add(new Envio(proxima, enviar(proxima)));
                } catch (BrokerInacessivel e) {
                    // Sem conexão, as demais falhariam do mesmo jeito — cada
                    // uma esperando o próprio timeout. A onda para aqui, mas as
                    // já enviadas ainda têm a confirmação esperada abaixo.
                    proxima.registrarFalha(e.getMessage());
                    falhas++;
                    inacessivel = true;
                    log.warn("RabbitMQ inacessível; os pendentes aguardam a próxima rodada: {}", e.getMessage());
                    break;
                } catch (FalhaDePublicacao e) {
                    // Um evento que falhou retém os seguintes da MESMA chave, e
                    // só eles.
                    proxima.registrarFalha(e.getMessage());
                    falhas++;
                    it.remove();
                    avisarFalha(proxima, e);
                }
            }

            for (Envio envio : onda) {
                MensagemNoOutbox mensagem = envio.mensagem();
                try {
                    aguardarConfirmacao(envio.confirmacao());
                    mensagem.marcarPublicada();
                    publicadas++;
                    Deque<MensagemNoOutbox> restantes = porChave.get(mensagem.getChave());
                    restantes.poll();
                    if (restantes.isEmpty()) {
                        porChave.remove(mensagem.getChave());
                    }
                } catch (FalhaDePublicacao e) {
                    mensagem.registrarFalha(e.getMessage());
                    falhas++;
                    porChave.remove(mensagem.getChave());
                    avisarFalha(mensagem, e);
                }
            }
        }
        return new Lote(lidas.size(), publicadas, falhas, inacessivel);
    }

    private static void avisarFalha(MensagemNoOutbox mensagem, FalhaDePublicacao e) {
        log.warn("evento {} ({}) não publicado, tentativa {}: {}",
                mensagem.getMensagemId(), mensagem.getTipo(), mensagem.getTentativas(), e.getMessage());
    }

    /** Publica sem esperar: a confirmação é esperada pela onda inteira. */
    private CorrelationData enviar(MensagemNoOutbox pendente) {
        Class<? extends MensagemDeIntegracao> classe = Contratos.classeDe(pendente.getTipo())
                .orElseThrow(() -> new FalhaDePublicacao("tipo sem contrato conhecido: " + pendente.getTipo()));
        MensagemDeIntegracao conteudo;
        try {
            conteudo = json.readValue(pendente.getPayload(), classe);
        } catch (JacksonException e) {
            throw new FalhaDePublicacao("payload ilegível: " + e.getOriginalMessage());
        }

        CorrelationData confirmacao = new CorrelationData(pendente.getMensagemId().toString());
        // Fato sai pela topic, pedido pela direct. Quem decide é o contrato, e
        // o relay não conhece nenhum deles pelo nome. Com atraso, a mensagem
        // entra na sala de espera, e a chave leva o atraso e o destino — ver
        // SalaDeEspera.
        String exchange = pendente.temAtraso() ? SalaDeEspera.EXCHANGE_ENTRADA : Contratos.exchangeDe(conteudo);
        String rota;
        try {
            rota = pendente.temAtraso()
                    ? SalaDeEspera.rotaPara(Duration.ofMillis(pendente.getAtrasoMs()), pendente.getRota())
                    : pendente.getRota();
        } catch (IllegalArgumentException e) {
            throw new FalhaDePublicacao(e.getMessage());
        }

        // A publicação roda como filha da requisição que gerou o evento: o
        // RabbitTemplate observado grava o traceparent nos cabeçalhos, e o
        // consumidor continua o mesmo trace. Ver Rastreamento.
        try {
            rastreamento.continuar(pendente.getRastreamento(), "outbox publicar " + pendente.getTipo(), () -> {
                publicar(exchange, rota, conteudo, pendente, confirmacao);
                return null;
            });
        } catch (AmqpException e) {
            // Broker fora do ar cai aqui, e não na confirmação: não há conexão
            // para esperar resposta. Convertida para a falha tratada, para que
            // as mensagens já confirmadas deste lote continuem marcadas.
            throw new BrokerInacessivel(e.getMessage());
        }

        return confirmacao;
    }

    private void publicar(String exchange, String rota, MensagemDeIntegracao conteudo,
                          MensagemNoOutbox pendente, CorrelationData confirmacao) {
        rabbit.convertAndSend(exchange, rota, conteudo, mensagem -> {
            MessageProperties p = mensagem.getMessageProperties();
            p.setMessageId(pendente.getMensagemId().toString());
            p.setType(pendente.getTipo());
            p.setAppId(origem);
            p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            p.setTimestamp(Date.from(conteudo.ocorridoEm()));
            p.setHeader(CE + "specversion", "1.0");
            p.setHeader(CE + "id", pendente.getMensagemId().toString());
            p.setHeader(CE + "type", "br.infnet.hub." + pendente.getTipo());
            p.setHeader(CE + "source", "/" + origem);
            p.setHeader(CE + "subject", pendente.getChave());
            p.setHeader(CE + "time", conteudo.ocorridoEm().atZone(ZoneOffset.UTC).toString());
            return mensagem;
        }, confirmacao);
    }

    private void aguardarConfirmacao(CorrelationData confirmacao) {
        CorrelationData.Confirm resposta;
        try {
            resposta = confirmacao.getFuture().get(esperaConfirmacaoMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaDePublicacao("interrompido aguardando o broker");
        } catch (ExecutionException | TimeoutException e) {
            throw new FalhaDePublicacao("broker não confirmou: " + e);
        }

        if (!resposta.ack()) {
            throw new FalhaDePublicacao("broker recusou: " + resposta.reason());
        }
        // A devolução, quando existe, é registrada antes de a confirmação
        // chegar — é garantia do Spring AMQP, e é o que torna esta checagem
        // confiável logo depois do get().
        ReturnedMessage devolvida = confirmacao.getReturned();
        if (devolvida != null) {
            throw new FalhaDePublicacao("sem fila de destino para a rota " + devolvida.getRoutingKey()
                    + " (" + devolvida.getReplyText() + ")");
        }
    }

    private static class FalhaDePublicacao extends RuntimeException {
        FalhaDePublicacao(String mensagem) {
            super(mensagem);
        }
    }

    private static final class BrokerInacessivel extends FalhaDePublicacao {
        BrokerInacessivel(String mensagem) {
            super("broker inacessível: " + mensagem);
        }
    }
}
