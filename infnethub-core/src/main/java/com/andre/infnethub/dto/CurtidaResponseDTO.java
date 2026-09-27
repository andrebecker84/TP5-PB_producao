package com.andre.infnethub.dto;

import com.andre.infnethub.model.Curtida;

public record CurtidaResponseDTO(Long usuarioId, String usuarioNome) {
    public static CurtidaResponseDTO fromEntity(Curtida c) {
        return new CurtidaResponseDTO(c.getUsuario().getId(), c.getUsuario().getNome());
    }
}
