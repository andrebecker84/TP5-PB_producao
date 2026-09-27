package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.model.Disciplina;

import java.util.Comparator;
import java.util.List;

public record CatalogoDisciplinaDTO(
        Long id,
        String nome,
        String tipo,
        String tipoDescricao,
        Integer cargaHoraria,
        boolean isentaFrequencia,
        List<CatalogoCompetenciaDTO> competencias
) {
    public static CatalogoDisciplinaDTO fromEntity(Disciplina disciplina) {
        return new CatalogoDisciplinaDTO(
                disciplina.getId(),
                disciplina.getNome(),
                disciplina.getTipo().name(),
                disciplina.getTipo().getDescricao(),
                disciplina.getCargaHoraria(),
                disciplina.isIsentaFrequencia(),
                disciplina.getCompetencias().stream()
                        .sorted(Comparator.comparing(com.andre.infnethub.boletim.model.Competencia::getOrdem))
                        .map(CatalogoCompetenciaDTO::fromEntity)
                        .toList()
        );
    }
}
