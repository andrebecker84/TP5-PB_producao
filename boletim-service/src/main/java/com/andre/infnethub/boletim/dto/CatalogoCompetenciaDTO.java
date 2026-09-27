package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.model.Competencia;

/** Uma competência do plano de ensino, sem resultado de aluno. */
public record CatalogoCompetenciaDTO(
        Long id,
        String nome,
        Integer ordem
) {
    public static CatalogoCompetenciaDTO fromEntity(Competencia competencia) {
        return new CatalogoCompetenciaDTO(competencia.getId(), competencia.getNome(), competencia.getOrdem());
    }
}
