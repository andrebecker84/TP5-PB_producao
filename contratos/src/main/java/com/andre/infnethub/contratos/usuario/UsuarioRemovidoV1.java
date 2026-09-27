package com.andre.infnethub.contratos.usuario;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * Um usuário deixou de existir no Infnet Hub.
 *
 * <p>Só o id: depois da remoção não há estado a transferir, e repetir aqui o
 * nome de quem foi removido seria espalhar pelo broker justamente o dado que
 * acabou de ser apagado.
 */
public record UsuarioRemovidoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long usuarioId
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(usuarioId);
    }
}
