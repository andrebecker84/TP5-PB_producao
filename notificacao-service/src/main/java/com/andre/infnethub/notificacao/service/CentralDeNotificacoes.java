package com.andre.infnethub.notificacao.service;

import com.andre.infnethub.notificacao.dto.NotificacaoDTO;
import com.andre.infnethub.notificacao.model.Notificacao;
import com.andre.infnethub.notificacao.model.PostRemovido;
import com.andre.infnethub.notificacao.model.TipoNotificacao;
import com.andre.infnethub.notificacao.repository.NotificacaoRepository;
import com.andre.infnethub.notificacao.repository.PostRemovidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cria, lista e dá baixa nas notificações de cada pessoa.
 *
 * <p>Toda operação recebe o destinatário e filtra por ele. Não existe "marcar a
 * notificação 42 como lida": existe "marcar a notificação 42 <em>deste
 * usuário</em>". Um id alheio simplesmente não é encontrado.
 */
@Service
@RequiredArgsConstructor
public class CentralDeNotificacoes {

    public static final int LIMITE_PADRAO = 30;
    public static final int LIMITE_MAXIMO = 100;

    private final NotificacaoRepository repositorio;
    private final PostRemovidoRepository postsRemovidos;
    private final ApplicationEventPublisher publicador;

    /**
     * Grava a notificação, a menos que este evento já a tenha gerado para esta
     * pessoa.
     *
     * <p>A checagem prévia é o caminho barato; a chave única
     * {@code (mensagem_origem_id, destinatario_id)} é a garantia, caso duas
     * cópias do mesmo evento sejam processadas ao mesmo tempo por instâncias
     * diferentes.
     *
     * @return {@code true} se a notificação foi criada.
     */
    @Transactional
    public boolean notificar(Long destinatarioId, TipoNotificacao tipo, String texto, String link, UUID origem) {
        return gravar(new Notificacao(destinatarioId, tipo, texto, link, origem));
    }

    /**
     * A notificação que fala de um post: curtida, comentário. Guarda o id do
     * post, para sair junto com ele, e não é criada se o post já foi apagado.
     *
     * @return {@code true} se a notificação foi criada.
     */
    @Transactional
    public boolean notificarSobrePost(Long destinatarioId, TipoNotificacao tipo, String texto, Long postId,
                                      UUID origem) {
        if (postsRemovidos.existsById(postId)) {
            return false;
        }
        return gravar(new Notificacao(destinatarioId, tipo, texto, "/feed#post-" + postId, origem, postId));
    }

    private boolean gravar(Notificacao nova) {
        if (repositorio.existsByMensagemOrigemIdAndDestinatarioId(nova.getMensagemOrigemId(), nova.getDestinatarioId())) {
            return false;
        }
        Notificacao salva = repositorio.save(nova);
        publicador.publishEvent(new NotificacaoCriada(salva.getDestinatarioId(), NotificacaoDTO.de(salva)));
        return true;
    }

    /**
     * O post foi apagado: as notificações sobre ele saem do sino de cada
     * destinatário, também nas abas abertas, e a lápide barra as que ainda
     * estiverem a caminho.
     *
     * @return quantas notificações foram apagadas.
     */
    @Transactional
    public int apagarDoPost(Long postId) {
        if (!postsRemovidos.existsById(postId)) {
            postsRemovidos.save(new PostRemovido(postId));
        }
        List<Notificacao> doPost = repositorio.findByPostId(postId);
        repositorio.deleteAll(doPost);
        avisarApagadas(doPost);
        return doPost.size();
    }

    @Transactional(readOnly = true)
    public List<NotificacaoDTO> recentes(Long usuarioId, int limite) {
        int limiteSeguro = Math.clamp(limite, 1, LIMITE_MAXIMO);
        return repositorio.findByDestinatarioIdOrderByCriadaEmDescIdDesc(usuarioId, Limit.of(limiteSeguro))
                .stream().map(NotificacaoDTO::de).toList();
    }

    @Transactional(readOnly = true)
    public long naoLidas(Long usuarioId) {
        return repositorio.countByDestinatarioIdAndLidaEmIsNull(usuarioId);
    }

    @Transactional
    public int marcarComoLidas(Long usuarioId, Collection<Long> ids) {
        List<Notificacao> doUsuario = repositorio.findByIdInAndDestinatarioId(ids, usuarioId);
        doUsuario.forEach(Notificacao::marcarComoLida);
        return doUsuario.size();
    }

    @Transactional
    public int marcarComoNaoLidas(Long usuarioId, Collection<Long> ids) {
        List<Notificacao> doUsuario = repositorio.findByIdInAndDestinatarioId(ids, usuarioId);
        doUsuario.forEach(Notificacao::marcarComoNaoLida);
        return doUsuario.size();
    }

    @Transactional
    public int marcarTodasComoLidas(Long usuarioId) {
        return repositorio.marcarTodasComoLidas(usuarioId, Instant.now());
    }

    @Transactional
    public int excluir(Long usuarioId, Collection<Long> ids) {
        List<Notificacao> doUsuario = repositorio.findByIdInAndDestinatarioId(ids, usuarioId);
        repositorio.deleteAll(doUsuario);
        // As outras abas da mesma pessoa também deixam de mostrar.
        avisarApagadas(doUsuario);
        return doUsuario.size();
    }

    private void avisarApagadas(List<Notificacao> apagadas) {
        Map<Long, List<Long>> porDestinatario = apagadas.stream().collect(Collectors.groupingBy(
                Notificacao::getDestinatarioId, Collectors.mapping(Notificacao::getId, Collectors.toList())));
        porDestinatario.forEach((destinatario, ids) ->
                publicador.publishEvent(new NotificacoesApagadas(destinatario, ids)));
    }

    /** Quando o usuário deixa de existir, as notificações dele deixam também. */
    @Transactional
    public int apagarTodasDe(Long usuarioId) {
        return repositorio.apagarTodasDe(usuarioId);
    }
}
