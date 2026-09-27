package com.andre.infnethub.dto;

import com.andre.infnethub.model.Usuario;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String email,
        String escola,
        String ultimoBloco,
        String classe,
        String papel,
        String papelDescricao,
        LocalDateTime criadoEm
) {
    public static UsuarioResponseDTO fromEntity(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(), u.getNome(), u.getEmail(),
                u.getEscola(), u.getUltimoBloco(), u.getClasse(),
                u.getPapel().name(), u.getPapel().getDescricao(),
                u.getCriadoEm()
        );
    }
}
