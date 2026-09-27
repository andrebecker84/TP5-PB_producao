package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.model.AtividadeAcademica;

public record AtividadeDTO(
        Long id,
        String categoria,
        String categoriaDescricao,
        String nome,
        Integer cargaHoraria,
        String periodo,
        String status,
        String statusDescricao,
        Integer presencaPercentual
) {
    public static AtividadeDTO fromEntity(AtividadeAcademica atividade) {
        return new AtividadeDTO(
                atividade.getId(),
                atividade.getCategoria().name(),
                atividade.getCategoria().getDescricao(),
                atividade.getNome(),
                atividade.getCargaHoraria(),
                atividade.getPeriodo(),
                atividade.getStatus().name(),
                atividade.getStatus().getDescricao(),
                atividade.getPresencaPercentual()
        );
    }
}
