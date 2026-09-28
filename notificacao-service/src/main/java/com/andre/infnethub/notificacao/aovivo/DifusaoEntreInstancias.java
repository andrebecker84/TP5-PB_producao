package com.andre.infnethub.notificacao.aovivo;

import com.andre.infnethub.notificacao.mensageria.MensageriaConfig;
import com.andre.infnethub.notificacao.service.NotificacaoCriada;
import com.andre.infnethub.notificacao.service.NotificacoesApagadas;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Leva o aviso ao vivo até a instância onde o destinatário está conectado.
 *
 * <h2>O problema</h2>
 * <p>Com três instâncias deste serviço, a curtida pode ser consumida pela
 * instância 1 enquanto o navegador do autor do post está conectado à instância
 * 3. A instância 1 grava a notificação — mas não tem a conexão para avisar.
 *
 * <h2>A solução: fanout</h2>
 * <p>Depois do commit, a instância que criou a notificação publica um
 * {@link AvisoAoVivo} numa exchange {@code fanout}. Cada instância tem a sua
 * própria fila, exclusiva e temporária, ligada a essa exchange — e o fanout
 * entrega uma cópia a cada uma. Quem tem a conexão entrega; as outras não
 * encontram ninguém e descartam.
 *
 * <p>É o oposto da fila {@code notificacao.eventos}: lá, cada mensagem vai para
 * <em>um</em> consumidor (trabalho a dividir); aqui, vai para <em>todos</em>
 * (recado a espalhar). Mesmo broker, dois padrões, dois propósitos.
 *
 * <h2>Por que o aviso pode se perder, e está tudo bem</h2>
 * <p>Sem outbox e sem confirmação, de propósito. A notificação já está gravada,
 * e o sino busca as não lidas sempre que a tela carrega. O aviso ao vivo só
 * antecipa o que a próxima leitura mostraria. Proteger com outbox um dado que
 * já é durável seria pagar duas vezes pela mesma garantia.
 */
@Component
@RabbitListener(queues = "#{filaDeAvisosDestaInstancia.name}", concurrency = "1")
@RequiredArgsConstructor
class DifusaoEntreInstancias {

    private static final Logger log = LoggerFactory.getLogger(DifusaoEntreInstancias.class);

    private final RabbitTemplate rabbit;
    private final ConexoesAoVivo conexoes;

    /**
     * {@code AFTER_COMMIT}: só avisa o que foi gravado. Avisar antes do commit
     * poderia mostrar na tela uma notificação que, um instante depois, deixaria
     * de existir porque a transação voltou atrás.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void difundir(NotificacaoCriada criada) {
        publicar(new AvisoAoVivo(criada.destinatarioId(), criada.notificacao()));
    }

    /** Mesmo caminho para a remoção: a aba aberta tira da lista o que já não existe. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void difundir(NotificacoesApagadas apagadas) {
        publicar(new RemocaoAoVivo(apagadas.destinatarioId(), apagadas.ids()));
    }

    private void publicar(Object aviso) {
        try {
            rabbit.convertAndSend(MensageriaConfig.EXCHANGE_AVISOS, "", aviso);
        } catch (AmqpException e) {
            log.warn("aviso ao vivo não difundido (o banco já está certo e a próxima leitura mostra): {}",
                    e.getMessage());
        }
    }

    /**
     * Um consumidor só, declarado na classe: a fila é desta instância, e a entrega ao navegador é
     * rápida — não há trabalho a dividir.
     */
    @RabbitHandler
    void receber(AvisoAoVivo aviso) {
        conexoes.entregar(aviso.destinatarioId(), aviso.notificacao());
    }

    @RabbitHandler
    void receber(RemocaoAoVivo remocao) {
        conexoes.retirar(remocao.destinatarioId(), remocao.ids());
    }
}
