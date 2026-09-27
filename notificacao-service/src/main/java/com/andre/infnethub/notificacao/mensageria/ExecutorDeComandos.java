package com.andre.infnethub.notificacao.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.comando.EnviarAvisoV1;
import com.andre.infnethub.notificacao.model.TipoNotificacao;
import com.andre.infnethub.notificacao.service.CadastroDeDestinatarios;
import com.andre.infnethub.notificacao.service.CentralDeNotificacoes;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Executa os pedidos de aviso — a outra fila, a do padrão comando.
 *
 * <p>É um ouvinte separado do {@link OuvinteDeEventos}, e não um método a mais
 * nele, porque são filas diferentes com ritmos diferentes: um aviso da
 * secretaria para toda a turma escreve centenas de linhas e pode demorar; uma
 * curtida escreve uma. Na mesma fila, o aviso seguraria as curtidas atrás dele.
 * Separadas, cada uma tem sua concorrência, sua fila de mensagens mortas e seu
 * gráfico no painel.
 *
 * <h2>O agendado chega aqui pela mesma porta</h2>
 * <p>Um aviso pedido para daqui a duas horas não tem tratamento próprio: ele
 * espera numa fila sem consumidor e, quando vence, o broker o reencaminha para
 * <em>esta</em> fila. Deste lado não há como saber — nem por que saber — se a
 * mensagem veio direto ou esperou. O atraso é problema de quem publica; a
 * execução é sempre a mesma.
 */
@Component
@RabbitListener(queues = Canais.FILA_NOTIFICACAO_COMANDOS,
        concurrency = "${app.notificacao.executores:1-3}")
@RequiredArgsConstructor
class ExecutorDeComandos {

    private static final Logger log = LoggerFactory.getLogger(ExecutorDeComandos.class);

    private final ControleDeDuplicidade duplicidade;
    private final CentralDeNotificacoes central;
    private final CadastroDeDestinatarios destinatarios;

    /**
     * Um comando, muitas notificações — o mesmo <em>fan-out na escrita</em> da
     * vaga publicada: cada pessoa recebe a sua linha, porque "já li" é uma
     * resposta por pessoa.
     *
     * <p>A idempotência é dupla, e as duas camadas fazem coisas diferentes. A
     * tabela de mensagens processadas descarta o comando repetido inteiro; a
     * chave única por {@code (mensagem de origem, destinatário)} garante que,
     * mesmo se o comando for reentregue no meio da escrita, ninguém receba o
     * aviso duas vezes. Sem a segunda, uma falha na centésima linha faria as
     * noventa e nove primeiras chegarem de novo na retentativa.
     */
    @RabbitHandler
    @Transactional
    void enviarAviso(EnviarAvisoV1 comando) {
        if (!duplicidade.primeiraVez(comando)) {
            return;
        }

        List<Long> alvos = comando.paraTodosOsAlunos()
                ? destinatarios.alunosAtivos()
                : comando.destinatarios();

        long avisados = alvos.stream()
                .filter(pessoa -> central.notificar(pessoa, TipoNotificacao.AVISO,
                        comando.texto(), comando.link(), comando.mensagemId()))
                .count();

        log.info("aviso de {} entregue a {} pessoa(s){}", comando.solicitadoPorId(), avisados,
                comando.paraTodosOsAlunos() ? " (toda a turma)" : "");
    }

    /**
     * Comando que este serviço não sabe executar vai direto para a fila de
     * mensagens mortas.
     *
     * <p>É tratamento diferente do que o {@link OuvinteDeEventos} dá a um
     * evento desconhecido, e a diferença é a mesma de sempre: um evento chega
     * aqui porque a ligação da fila o alcançou, e desinteresse é resposta
     * legítima — o consumidor descarta e segue. Um comando foi <em>endereçado
     * a este serviço</em>; não saber executá-lo é falha, e descartar em
     * silêncio apagaria a prova.
     *
     * <p>A exceção é específica: ela recusa <em>sem devolver à fila</em>. A
     * recusa comum faria a mensagem voltar, ser tentada de novo e falhar do
     * mesmo jeito, até o limite de entregas — cinco voltas para chegar ao
     * mesmo lugar. Nenhuma retentativa faz aparecer um contrato que não existe.
     */
    @RabbitHandler(isDefault = true)
    void desconhecido(Object comando) {
        log.warn("comando não reconhecido pelo notificacao-service: {}", comando.getClass().getSimpleName());
        throw new AmqpRejectAndDontRequeueException(
                "sem executor para " + comando.getClass().getSimpleName());
    }
}
