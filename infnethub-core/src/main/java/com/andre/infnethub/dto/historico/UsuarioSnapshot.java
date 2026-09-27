package com.andre.infnethub.dto.historico;

import com.andre.infnethub.model.Usuario;

/** Estado de um {@link Usuario} em uma revisão. */
public record UsuarioSnapshot(
        Long id,
        String nome,
        String email,
        String escola,
        String ultimoBloco,
        String classe,
        String papel,
        String papelDescricao
) {
    public static UsuarioSnapshot de(Usuario u) {
        return new UsuarioSnapshot(
                u.getId(), u.getNome(), u.getEmail(),
                u.getEscola(), u.getUltimoBloco(), u.getClasse(),
                u.getPapel() != null ? u.getPapel().name() : null,
                u.getPapel() != null ? u.getPapel().getDescricao() : null
        );
    }
}
