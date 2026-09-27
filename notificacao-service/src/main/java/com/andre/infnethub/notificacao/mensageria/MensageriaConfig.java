package com.andre.infnethub.notificacao.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.SalaDeEspera;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Base64UrlNamingStrategy;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.ConnectionFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Três topologias, para três padrões de mensagem.
 *
 * <pre>
 *  1. TRABALHO A DIVIDIR — competing consumers
 *
 *     infnethub.eventos (topic) ─usuario.* ┐
 *                               ─post.*    ├─▶ notificacao.eventos ──▶ instância 1 ┐
 *                               ─vaga.*    ┘      (uma fila,            instância 2 ├ cada mensagem
 *                                                  quorum)              instância 3 ┘ vai para UMA
 *
 *  2. PEDIDO COM UM DESTINATÁRIO — comando, imediato ou agendado
 *
 *     infnethub.comandos (direct) ─notificacao.enviar──▶ notificacao.comandos ──▶ executa
 *                                                               ▲
 *     sala de espera: 22 níveis, fila de 2^i s em cada um       │ #.notificacao.enviar
 *     infnethub.espera.nivel.21 ─▶ … ─▶ nível.00 ─▶ infnethub.espera.entrega (topic)
 *     (ver SalaDeEspera)
 *
 *  3. RECADO A ESPALHAR — publish/subscribe por fanout
 *
 *     infnethub.notificacoes.aovivo (fanout) ─┬─▶ fila exclusiva da instância 1
 *                                             ├─▶ fila exclusiva da instância 2   cada mensagem
 *                                             └─▶ fila exclusiva da instância 3   vai para TODAS
 * </pre>
 *
 * <p>No primeiro, escalar é acrescentar instâncias: o RabbitMQ reparte as
 * mensagens entre os consumidores, e a vazão cresce. No terceiro, cada
 * instância precisa ver tudo, porque só ela sabe quem está conectado a ela.
 *
 * <p>O segundo mostra o mesmo recurso — <em>dead lettering</em> — usado para
 * duas coisas opostas. Nas filas de trabalho, é o destino do fracasso: a
 * mensagem que esgotou as tentativas sai de circulação e fica guardada para
 * alguém olhar. Na sala de espera, é o caminho do sucesso: a mensagem "morre"
 * de velha, de propósito, nível após nível, e é justamente essa morte que a
 * leva adiante.
 */
@Configuration
public class MensageriaConfig {

    /** Exchange interna do serviço, para os avisos ao vivo entre instâncias. */
    public static final String EXCHANGE_AVISOS = "infnethub.notificacoes.aovivo";

    private static final int LIMITE_DE_ENTREGAS = 5;

    // ── 1. Eventos de domínio ──────────────────────────────────────────────

    @Bean
    TopicExchange exchangeDeEventos() {
        return ExchangeBuilder.topicExchange(Canais.EXCHANGE_EVENTOS).durable(true).build();
    }

    @Bean
    DirectExchange exchangeDeMensagensMortas() {
        return ExchangeBuilder.directExchange(Canais.EXCHANGE_MORTAS).durable(true).build();
    }

    @Bean
    Queue filaDeEventos() {
        return QueueBuilder.durable(Canais.FILA_NOTIFICACAO_EVENTOS)
                .quorum()
                .deliveryLimit(LIMITE_DE_ENTREGAS)
                .deadLetterExchange(Canais.EXCHANGE_MORTAS)
                .deadLetterRoutingKey(Canais.dlqDe(Canais.FILA_NOTIFICACAO_EVENTOS))
                .build();
    }

    @Bean
    Queue filaDeEventosMortos() {
        return QueueBuilder.durable(Canais.dlqDe(Canais.FILA_NOTIFICACAO_EVENTOS)).quorum().build();
    }

    /**
     * Três ligações para a mesma fila. Os eventos de usuário chegam aqui
     * <em>e</em> na fila do boletim: é a mesma publicação do core, copiada pela
     * exchange para cada interessado. O core publicou uma vez.
     */
    @Bean
    Binding ligacaoUsuarios() {
        return BindingBuilder.bind(filaDeEventos()).to(exchangeDeEventos()).with(Canais.PADRAO_USUARIO);
    }

    @Bean
    Binding ligacaoPosts() {
        return BindingBuilder.bind(filaDeEventos()).to(exchangeDeEventos()).with(Canais.PADRAO_POST);
    }

    @Bean
    Binding ligacaoVagas() {
        return BindingBuilder.bind(filaDeEventos()).to(exchangeDeEventos()).with(Canais.PADRAO_VAGA);
    }

    @Bean
    Binding ligacaoEventosMortos() {
        return BindingBuilder.bind(filaDeEventosMortos())
                .to(exchangeDeMensagensMortas())
                .with(Canais.dlqDe(Canais.FILA_NOTIFICACAO_EVENTOS));
    }

    // ── 2. Comandos de aviso, imediatos e agendados ────────────────────────

    /**
     * A exchange dos pedidos. {@code direct}: a chave é o endereço de um
     * executor, não um assunto que vários possam assinar.
     */
    @Bean
    DirectExchange exchangeDeComandos() {
        return ExchangeBuilder.directExchange(Canais.EXCHANGE_COMANDOS).durable(true).build();
    }

    @Bean
    Queue filaDeComandos() {
        return QueueBuilder.durable(Canais.FILA_NOTIFICACAO_COMANDOS)
                .quorum()
                .deliveryLimit(LIMITE_DE_ENTREGAS)
                .deadLetterExchange(Canais.EXCHANGE_MORTAS)
                .deadLetterRoutingKey(Canais.dlqDe(Canais.FILA_NOTIFICACAO_COMANDOS))
                .build();
    }

    @Bean
    Queue filaDeComandosMortos() {
        return QueueBuilder.durable(Canais.dlqDe(Canais.FILA_NOTIFICACAO_COMANDOS)).quorum().build();
    }

    /**
     * A sala de espera dos avisos agendados — ver {@link SalaDeEspera}.
     *
     * <p>Uma exchange e uma fila por nível, declaradas em laço a partir da
     * mesma definição que o relay do core usa para montar a chave: se as duas
     * pontas lessem de lugares diferentes, bastaria uma divergência para as
     * mensagens sumirem no meio da cascata sem erro nenhum.
     *
     * <p>Cada fila tem o prazo definido <em>nela</em> ({@code x-message-ttl}),
     * e não em cada mensagem: é o que faz a ordem de chegada ser a ordem de
     * vencimento, e o que elimina o bloqueio de cabeça de fila. Ninguém
     * consome estas filas; ao vencer, a mensagem segue por dead-letter para o
     * nível seguinte, com a chave intacta.
     *
     * <p>Dead-letter {@code at-least-once}: o padrão das filas quorum descarta a
     * mensagem se o destino recusar no momento do vencimento. Aqui o destino é
     * o próprio caminho da mensagem, e perder um aviso agendado não é aceitável.
     * A estratégia exige {@code reject-publish} como política de transbordo.
     */
    @Bean
    Declarables salaDeEspera() {
        List<Declarable> topologia = new ArrayList<>();
        TopicExchange entrega = ExchangeBuilder.topicExchange(SalaDeEspera.EXCHANGE_ENTREGA).durable(true).build();
        topologia.add(entrega);
        topologia.add(BindingBuilder.bind(filaDeComandos()).to(entrega)
                .with(SalaDeEspera.padraoDeEntrega(Canais.ROTA_ENVIAR_AVISO)));

        for (int nivel = 0; nivel < SalaDeEspera.NIVEIS; nivel++) {
            TopicExchange exchange = ExchangeBuilder.topicExchange(SalaDeEspera.exchangeDoNivel(nivel))
                    .durable(true).build();
            Queue fila = QueueBuilder.durable(SalaDeEspera.filaDoNivel(nivel))
                    .quorum()
                    .ttl((int) SalaDeEspera.ttlDoNivelMs(nivel))
                    .deadLetterExchange(SalaDeEspera.seguinteAo(nivel))
                    .withArgument("x-dead-letter-strategy", "at-least-once")
                    .overflow(QueueBuilder.Overflow.rejectPublish)
                    .build();
            topologia.add(exchange);
            topologia.add(fila);
            topologia.add(BindingBuilder.bind(fila).to(exchange).with(SalaDeEspera.padraoParaEsperar(nivel)));
            topologia.add(new Binding(SalaDeEspera.seguinteAo(nivel), Binding.DestinationType.EXCHANGE,
                    exchange.getName(), SalaDeEspera.padraoParaSeguir(nivel), null));
        }
        return new Declarables(topologia);
    }

    @Bean
    Binding ligacaoComandos() {
        return BindingBuilder.bind(filaDeComandos()).to(exchangeDeComandos()).with(Canais.ROTA_ENVIAR_AVISO);
    }

    @Bean
    Binding ligacaoComandosMortos() {
        return BindingBuilder.bind(filaDeComandosMortos())
                .to(exchangeDeMensagensMortas())
                .with(Canais.dlqDe(Canais.FILA_NOTIFICACAO_COMANDOS));
    }

    // ── 3. Avisos ao vivo entre instâncias ─────────────────────────────────

    @Bean
    FanoutExchange exchangeDeAvisos() {
        return ExchangeBuilder.fanoutExchange(EXCHANGE_AVISOS).durable(true).build();
    }

    /**
     * Fila anônima: exclusiva desta instância, apagada quando ela sai.
     *
     * <p>Não é durável, e não precisa ser. Um aviso ao vivo só interessa a quem
     * está conectado agora; se a instância cai, as conexões dela caem junto, e
     * os navegadores reconectam em outra — que já tem a sua própria fila. O
     * RabbitMQ 4 aceita filas temporárias justamente quando são exclusivas.
     */
    @Bean
    Queue filaDeAvisosDestaInstancia() {
        return new AnonymousQueue(new Base64UrlNamingStrategy("notificacao.aovivo."));
    }

    @Bean
    Binding ligacaoAvisos() {
        return BindingBuilder.bind(filaDeAvisosDestaInstancia()).to(exchangeDeAvisos());
    }

    /**
     * Desistir depressa de um nó que não responde.
     *
     * <p>Os padrões do cliente AMQP são generosos — um minuto para abrir a
     * conexão TCP, dez segundos para o aperto de mãos —, e com três endereços
     * na lista, uma tentativa contra um cluster em desligamento chegou a
     * prender a thread do agendador por 36 segundos, medidos. Enquanto isso,
     * nem a caixa de saída registrava a falha, nem o prazo das sagas era
     * verificado. Com três segundos por etapa, o nó que não responde é trocado
     * pelo seguinte, e o broker inteiro fora do ar vira falha registrada em
     * poucos segundos. A abertura da conexão TCP tem o mesmo limite, em
     * spring.rabbitmq.connection-timeout.
     */
    @Bean
    ConnectionFactoryCustomizer desistirDepressa() {
        return fabrica -> fabrica.setHandshakeTimeout(3_000);
    }

    /**
     * JSON, com os pacotes aceitos listados um a um: os três de contratos que
     * esta fila recebe e o do aviso interno.
     */
    @Bean
    MessageConverter conversorJson() {
        return new JacksonJsonMessageConverter(
                "com.andre.infnethub.contratos.usuario",
                "com.andre.infnethub.contratos.feed",
                "com.andre.infnethub.contratos.vaga",
                "com.andre.infnethub.contratos.comando",
                "com.andre.infnethub.contratos.expurgo",
                "com.andre.infnethub.notificacao.aovivo",
                "com.andre.infnethub.notificacao.dto");
    }
}
