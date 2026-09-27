package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Disciplina;

import java.util.Comparator;
import java.util.List;

/**
 * Um bloco do catálogo — a estrutura do curso, sem vínculo com aluno.
 *
 * <p>Alimenta o mapa do curso, que precisa mostrar também os blocos que o aluno
 * ainda não cursou. Esses não aparecem no boletim, que parte das matrículas.
 */
public record CatalogoBlocoDTO(
        Long id,
        Integer numero,
        String titulo,
        List<CatalogoDisciplinaDTO> disciplinas
) {
    public static CatalogoBlocoDTO fromEntity(Bloco bloco) {
        return new CatalogoBlocoDTO(
                bloco.getId(),
                bloco.getNumero(),
                bloco.getTitulo(),
                bloco.getDisciplinas().stream()
                        .sorted(Comparator.comparing(Disciplina::getId))
                        .map(CatalogoDisciplinaDTO::fromEntity)
                        .toList()
        );
    }
}
