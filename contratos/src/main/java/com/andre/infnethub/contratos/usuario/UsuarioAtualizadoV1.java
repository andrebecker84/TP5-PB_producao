package com.andre.infnethub.contratos.usuario;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * Os dados de um usuário mudaram.
 *
 * <p>Traz o estado completo depois da mudança, e não a diferença. Um consumidor
 * que perdeu fatos intermediários — ou que acabou de nascer e só vê este —
 * chega ao estado certo aplicando apenas o mais recente. Com diferenças, ele
 * precisaria de todos, na ordem, sem falhar nenhum.
 *
 * @param versao ver {@link UsuarioCadastradoV1#versao()}.
 */
public record UsuarioAtualizadoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long usuarioId,
        long versao,
        String nome,
        String escola,
        String ultimoBloco,
        String classe,
        String papel,
        String papelDescricao
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(usuarioId);
    }
}
