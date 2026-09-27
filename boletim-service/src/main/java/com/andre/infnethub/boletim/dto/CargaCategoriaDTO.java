package com.andre.infnethub.boletim.dto;

/** Progresso de integralização de uma categoria de atividade. */
public record CargaCategoriaDTO(
        String categoria,
        String descricao,
        int concluida,
        int exigida,
        int percentual
) {
}
