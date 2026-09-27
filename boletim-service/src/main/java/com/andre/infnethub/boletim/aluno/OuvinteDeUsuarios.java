package com.andre.infnethub.boletim.aluno;

import com.andre.infnethub.boletim.mensageria.ControleDeDuplicidade;
import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.expurgo.ExpurgoSolicitadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioAtualizadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioCadastradoV1;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Escuta o que acontece com os usuários no core e mantém a réplica.
 *
 * <p>Uma fila, quatro tipos de evento, um método para cada. É o conversor JSON
 * que lê o tipo da mensagem e o {@link RabbitHandler} que escolhe o método pelo
 * tipo do parâmetro — nenhum {@code if} sobre cabeçalho escrito à mão.
 *
 * <p>Desde a saga de expurgo, nem tudo o que chega aqui é notícia a aplicar:
 * {@code ExpurgoSolicitadoV1} é um pedido que este serviço precisa
 * <strong>responder</strong>. É o ponto em que o boletim deixa de ser só
 * consumidor.
 *
 * <p>Esta classe é também a <strong>camada anticorrupção</strong> do boletim: é
 * o único lugar que conhece o formato dos eventos do core. Daqui para dentro só
 * circula {@link AlunoReplica}, o modelo deste serviço. Se o core publicar um
 * {@code UsuarioAtualizadoV2}, muda esta classe e nenhuma outra.
 *
 * <p><strong>A transação abre aqui</strong> e envolve as duas gravações: o
 * registro da mensagem como processada e a mudança na réplica. Acontecem as
 * duas ou nenhuma. Se a transação falhar, a exceção sobe, o Spring AMQP tenta
 * de novo com espera crescente e, esgotadas as tentativas, rejeita a mensagem
 * — que o RabbitMQ desvia para {@code boletim.usuarios.dlq}.
 */
@Component
@RabbitListener(queues = Canais.FILA_BOLETIM_USUARIOS)
@RequiredArgsConstructor
class OuvinteDeUsuarios {

    private static final Logger log = LoggerFactory.getLogger(OuvinteDeUsuarios.class);

    private final ReplicaDeAlunos replica;
    private final ControleDeDuplicidade duplicidade;
    private final ParticipacaoNoExpurgo expurgo;

    @RabbitHandler
    @Transactional
    void cadastrado(UsuarioCadastradoV1 evento) {
        if (duplicidade.primeiraVez(evento)) {
            replica.aplicar(new AlunoReplica(evento.usuarioId(), evento.nome(), evento.escola(),
                    evento.ultimoBloco(), evento.classe(), evento.papel(), evento.papelDescricao(),
                    evento.versao(), LocalDateTime.now()));
        }
    }

    @RabbitHandler
    @Transactional
    void atualizado(UsuarioAtualizadoV1 evento) {
        if (duplicidade.primeiraVez(evento)) {
            replica.aplicar(new AlunoReplica(evento.usuarioId(), evento.nome(), evento.escola(),
                    evento.ultimoBloco(), evento.classe(), evento.papel(), evento.papelDescricao(),
                    evento.versao(), LocalDateTime.now()));
        }
    }

    /**
     * O pedido de remoção — primeiro passo da saga de expurgo.
     *
     * <p>Este serviço é <strong>participante</strong>: decide se pode cumprir e
     * responde. Ver {@link ParticipacaoNoExpurgo} para a regra e para as duas
     * respostas possíveis.
     */
    @RabbitHandler
    @Transactional
    void expurgoSolicitado(ExpurgoSolicitadoV1 evento) {
        if (duplicidade.primeiraVez(evento)) {
            expurgo.decidirSobre(evento.processoId(), evento.usuarioId());
        }
    }

    /**
     * O fato consumado — a saga terminou e a pessoa não existe mais.
     *
     * <p>Chega depois da confirmação que este serviço mesmo enviou, então a
     * réplica já saiu. O método continua existindo porque o evento é público e
     * pode alcançar este serviço por outros caminhos — um reenvio, uma
     * reprocessagem da fila de mensagens mortas —, e porque remover o que já foi
     * removido não custa nada.
     */
    @RabbitHandler
    @Transactional
    void removido(UsuarioRemovidoV1 evento) {
        if (duplicidade.primeiraVez(evento)) {
            replica.remover(evento.usuarioId());
        }
    }

    /**
     * Evento de tipo que este serviço ainda não conhece.
     *
     * <p>Sem este método, um tipo novo publicado pelo core viraria exceção,
     * esgotaria as tentativas e iria para a fila de mensagens mortas — o boletim
     * trataria como defeito o que é só evolução do outro lado. Com ele, o
     * serviço registra e segue: quem não foi atualizado ignora o que não
     * entende.
     */
    @RabbitHandler(isDefault = true)
    void desconhecido(Object evento) {
        log.warn("evento não reconhecido pelo boletim-service: {}", evento.getClass().getSimpleName());
    }
}
