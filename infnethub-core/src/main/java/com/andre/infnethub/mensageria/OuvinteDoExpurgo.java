package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.expurgo.AlunoAnonimizadoV1;
import com.andre.infnethub.contratos.expurgo.AnonimizacaoRecusadaV1;
import com.andre.infnethub.expurgo.SagaDeExpurgo;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * O core ouvindo — a peça que a saga acrescentou.
 *
 * <p>Até o TP4 este serviço só publicava. Numa saga coreografada isso não basta:
 * quem abre o processo precisa saber como ele terminou, porque é quem decide
 * entre concluir e compensar. As duas respostas possíveis chegam pela mesma
 * fila, e o {@link RabbitHandler} escolhe o método pelo tipo.
 *
 * <p>A idempotência vale para as duas: uma reentrega não pode concluir duas
 * vezes nem — pior — reverter um expurgo que já foi concluído. A defesa é
 * dupla, de propósito: a tabela de mensagens processadas descarta a repetição,
 * e a própria saga só age sobre um processo que esteja em andamento. A segunda
 * camada cobre o caso em que a mensagem repetida tem outro {@code messageId},
 * que a primeira não pega.
 */
@Component
@RabbitListener(queues = Canais.FILA_CORE_EXPURGO)
@RequiredArgsConstructor
class OuvinteDoExpurgo {

    private static final Logger log = LoggerFactory.getLogger(OuvinteDoExpurgo.class);

    private final ControleDeDuplicidade duplicidade;
    private final SagaDeExpurgo saga;

    @RabbitHandler
    @Transactional
    void anonimizado(AlunoAnonimizadoV1 evento) {
        if (duplicidade.primeiraVez(evento)) {
            saga.concluir(evento.processoId(), evento.usuarioId(), evento.registrosMantidos());
        }
    }

    @RabbitHandler
    @Transactional
    void recusada(AnonimizacaoRecusadaV1 evento) {
        if (duplicidade.primeiraVez(evento)) {
            saga.reverter(evento.processoId(), evento.usuarioId(), evento.motivo());
        }
    }

    @RabbitHandler(isDefault = true)
    void desconhecido(Object evento) {
        log.warn("resposta de saga não reconhecida: {}", evento.getClass().getSimpleName());
    }
}
