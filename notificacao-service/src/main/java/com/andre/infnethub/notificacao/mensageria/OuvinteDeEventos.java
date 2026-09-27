package com.andre.infnethub.notificacao.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.feed.PostComentadoV1;
import com.andre.infnethub.contratos.feed.PostCurtidoV1;
import com.andre.infnethub.contratos.expurgo.ExpurgoSolicitadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioAtualizadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioCadastradoV1;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import com.andre.infnethub.contratos.vaga.VagaPublicadaV1;
import com.andre.infnethub.notificacao.model.TipoNotificacao;
import com.andre.infnethub.notificacao.service.CadastroDeDestinatarios;
import com.andre.infnethub.notificacao.service.CentralDeNotificacoes;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Decide, para cada fato do sistema, quem deve ser avisado e com que texto.
 *
 * <p>É aqui que mora a regra que no TP3 não existia em lugar nenhum: "uma
 * curtida avisa o autor do post, a não ser que ele tenha curtido o próprio
 * post". O core não sabe dessa regra, e é bom que não saiba — se amanhã a regra
 * mudar, muda este serviço, e só ele é reimplantado.
 *
 * <p><strong>Consumidores concorrentes.</strong> Várias threads, e possivelmente
 * várias instâncias, consomem esta fila ao mesmo tempo. É seguro porque cada
 * mensagem produz efeitos independentes — duas curtidas em posts diferentes não
 * disputam nada. Onde a ordem importaria (os fatos de um mesmo usuário), a
 * versão e a lápide em {@link CadastroDeDestinatarios} cuidam.
 */
@Component
@RabbitListener(queues = Canais.FILA_NOTIFICACAO_EVENTOS, concurrency = "${app.notificacao.consumidores:3-6}")
@RequiredArgsConstructor
class OuvinteDeEventos {

    private static final Logger log = LoggerFactory.getLogger(OuvinteDeEventos.class);

    private final ControleDeDuplicidade duplicidade;
    private final CentralDeNotificacoes central;
    private final CadastroDeDestinatarios destinatarios;

    // ── Usuários ───────────────────────────────────────────────────────────

    @RabbitHandler
    @Transactional
    void cadastrado(UsuarioCadastradoV1 e) {
        // Sem boas-vindas para quem já foi removido: com consumo em paralelo,
        // a remoção pode ter sido processada antes deste cadastro.
        if (!duplicidade.primeiraVez(e) || !destinatarios.registrar(e.usuarioId(), e.papel(), e.versao())) {
            return;
        }
        central.notificar(e.usuarioId(), TipoNotificacao.BOAS_VINDAS,
                "Boas-vindas ao Infnet Hub, %s! Complete seu perfil e acompanhe o feed da sua turma."
                        .formatted(primeiroNome(e.nome())),
                "/perfil", e.mensagemId());
    }

    @RabbitHandler
    @Transactional
    void atualizado(UsuarioAtualizadoV1 e) {
        if (duplicidade.primeiraVez(e)) {
            destinatarios.registrar(e.usuarioId(), e.papel(), e.versao());
        }
    }

    /**
     * Remoção apaga o histórico de notificações da pessoa. Não há por que
     * guardar avisos endereçados a quem não existe mais — e guardar seria reter
     * dado pessoal (quem curtiu o quê de quem) sem finalidade.
     */
    /**
     * O pedido de expurgo — e a decisão deliberada de não fazer nada com ele.
     *
     * <p>Este serviço não é participante da saga: ele não responde, não é
     * esperado, e o processo não depende dele. O que ele faz é esperar o
     * <em>fato consumado</em> ({@link UsuarioRemovidoV1}) para então apagar.
     *
     * <p>A diferença não é sutil. Apagar o histórico de notificações ao receber
     * o pedido destruiria os dados de alguém cuja remoção ainda pode ser
     * revertida — e, quando a compensação acontecesse, a pessoa voltaria a
     * existir sem as notificações dela, sem que nada no sistema explicasse o
     * sumiço.
     *
     * <p>O método existe, em vez de deixar o evento cair no tratador de tipo
     * desconhecido, porque "ignoro isto de propósito" e "não sei o que é isto"
     * merecem registros diferentes — e porque um aviso a cada remoção poluiria
     * o log com algo que está funcionando.
     */
    @RabbitHandler
    void expurgoSolicitado(ExpurgoSolicitadoV1 e) {
        log.debug("expurgo do usuário {} solicitado; aguardando a conclusão da saga", e.usuarioId());
    }

    @RabbitHandler
    @Transactional
    void removido(UsuarioRemovidoV1 e) {
        if (!duplicidade.primeiraVez(e)) {
            return;
        }
        destinatarios.remover(e.usuarioId());
        int apagadas = central.apagarTodasDe(e.usuarioId());
        log.info("usuário {} removido: {} notificação(ões) apagada(s)", e.usuarioId(), apagadas);
    }

    // ── Feed ───────────────────────────────────────────────────────────────

    @RabbitHandler
    @Transactional
    void curtido(PostCurtidoV1 e) {
        if (!duplicidade.primeiraVez(e) || Objects.equals(e.autorDoPostId(), e.curtidoPorId())) {
            return;
        }
        central.notificar(e.autorDoPostId(), TipoNotificacao.CURTIDA,
                "%s curtiu sua publicação \"%s\"".formatted(e.curtidoPorNome(), e.resumoDoPost()),
                "/feed#post-" + e.postId(), e.mensagemId());
    }

    @RabbitHandler
    @Transactional
    void comentado(PostComentadoV1 e) {
        if (!duplicidade.primeiraVez(e) || Objects.equals(e.autorDoPostId(), e.comentadoPorId())) {
            return;
        }
        central.notificar(e.autorDoPostId(), TipoNotificacao.COMENTARIO,
                "%s comentou em \"%s\": %s".formatted(e.comentadoPorNome(), e.resumoDoPost(), e.trechoDoComentario()),
                "/feed#post-" + e.postId(), e.mensagemId());
    }

    // ── Vagas ──────────────────────────────────────────────────────────────

    /**
     * Um evento, muitas notificações: <em>fan-out</em> na escrita.
     *
     * <p>Cada aluno ativo recebe a sua linha. A alternativa — uma notificação
     * "para todos", resolvida na leitura — deixaria sem resposta a pergunta
     * "esta pessoa já leu?", que é por pessoa.
     */
    @RabbitHandler
    @Transactional
    void vagaPublicada(VagaPublicadaV1 e) {
        if (!duplicidade.primeiraVez(e)) {
            return;
        }
        String texto = "Nova vaga: %s — %s (%s)".formatted(e.titulo(), e.empresa(), e.tipo());
        long avisados = destinatarios.alunosAtivos().stream()
                .filter(aluno -> !aluno.equals(e.publicadaPorId()))
                .filter(aluno -> central.notificar(aluno, TipoNotificacao.VAGA, texto, "/vagas", e.mensagemId()))
                .count();
        log.info("vaga {} publicada: {} aluno(s) avisado(s)", e.vagaId(), avisados);
    }

    @RabbitHandler(isDefault = true)
    void desconhecido(Object evento) {
        log.warn("evento não reconhecido pelo notificacao-service: {}", evento.getClass().getSimpleName());
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        return nome.strip().split("\\s+")[0];
    }
}
