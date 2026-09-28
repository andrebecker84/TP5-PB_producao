package com.andre.infnethub.contratos.feed;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma publicação deixou de existir.
 *
 * <p>Só o id, como no {@code UsuarioRemovidoV1}: não há o que transferir de um
 * post apagado. Quem guardou algo sobre ele — as notificações de curtida e de
 * comentário — sabe pelo id o que precisa apagar.
 */
public record PostRemovidoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long postId
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(postId);
    }
}
