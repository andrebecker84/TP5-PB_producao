package com.andre.infnethub.contratos.feed;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * Alguém comentou uma publicação.
 *
 * @param trechoDoComentario os primeiros caracteres do comentário, para a
 *                           notificação. O texto integral fica no core: quem
 *                           quiser ler tudo abre o post.
 */
public record PostComentadoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long postId,
        String resumoDoPost,
        Long autorDoPostId,
        Long comentarioId,
        Long comentadoPorId,
        String comentadoPorNome,
        String trechoDoComentario
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(postId);
    }
}
