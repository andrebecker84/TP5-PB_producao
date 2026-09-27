package com.andre.infnethub.dto;

import com.andre.infnethub.model.Comentario;

import java.time.LocalDateTime;

public record ComentarioResponseDTO(
        Long id,
        String conteudo,
        Long autorId,
        String autorNome,
        LocalDateTime criadoEm
) {
    public static ComentarioResponseDTO fromEntity(Comentario c) {
        return new ComentarioResponseDTO(
                c.getId(), c.getConteudo(),
                c.getAutor().getId(), c.getAutor().getNome(),
                c.getCriadoEm()
        );
    }
}
