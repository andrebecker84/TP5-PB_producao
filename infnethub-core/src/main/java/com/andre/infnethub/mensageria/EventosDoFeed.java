package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.feed.PostComentadoV1;
import com.andre.infnethub.contratos.feed.PostCurtidoV1;
import com.andre.infnethub.contratos.feed.PostRemovidoV1;
import com.andre.infnethub.contratos.vaga.VagaPublicadaV1;
import com.andre.infnethub.model.Comentario;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.model.Vaga;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Traduz o que acontece no feed e no mural de vagas para os contratos públicos.
 *
 * <p>Mesma regra de {@link EventosDeUsuario}: chamado dentro da transação da
 * escrita, para que o evento e o dado nasçam ou morram juntos.
 */
@Component
@RequiredArgsConstructor
public class EventosDoFeed {

    /** O bastante para a notificação ser reconhecível, sem copiar o post inteiro. */
    private static final int TAMANHO_RESUMO = 60;
    private static final int TAMANHO_TRECHO = 80;

    private final CaixaDeSaida caixa;

    public void curtido(Post post, Usuario quemCurtiu) {
        caixa.depositar(new PostCurtidoV1(
                UUID.randomUUID(), Instant.now(),
                post.getId(), resumo(post), post.getAutor().getId(),
                quemCurtiu.getId(), quemCurtiu.getNome()),
                Canais.ROTA_POST_CURTIDO);
    }

    public void comentado(Comentario comentario) {
        Post post = comentario.getPost();
        caixa.depositar(new PostComentadoV1(
                UUID.randomUUID(), Instant.now(),
                post.getId(), resumo(post), post.getAutor().getId(),
                comentario.getId(), comentario.getAutor().getId(), comentario.getAutor().getNome(),
                cortar(comentario.getConteudo(), TAMANHO_TRECHO)),
                Canais.ROTA_POST_COMENTADO);
    }

    /**
     * Sem este fato, as notificações de curtida e de comentário sobreviviam ao
     * post: "Fulano curtiu sua publicação", com um link para o nada.
     */
    public void removido(Long postId) {
        caixa.depositar(new PostRemovidoV1(UUID.randomUUID(), Instant.now(), postId),
                Canais.ROTA_POST_REMOVIDO);
    }

    public void vagaPublicada(Vaga vaga) {
        caixa.depositar(new VagaPublicadaV1(
                UUID.randomUUID(), Instant.now(),
                vaga.getId(), vaga.getTitulo(), vaga.getEmpresa(), vaga.getTipo().name(),
                vaga.getCriador().getId()),
                Canais.ROTA_VAGA_PUBLICADA);
    }

    /** O título, ou o começo do texto quando o post não tem título. */
    private static String resumo(Post post) {
        String titulo = post.getTitulo();
        return titulo != null && !titulo.isBlank() ? titulo : cortar(post.getConteudo(), TAMANHO_RESUMO);
    }

    private static String cortar(String texto, int limite) {
        if (texto == null || texto.length() <= limite) {
            return texto;
        }
        return texto.substring(0, limite).strip() + "…";
    }
}
