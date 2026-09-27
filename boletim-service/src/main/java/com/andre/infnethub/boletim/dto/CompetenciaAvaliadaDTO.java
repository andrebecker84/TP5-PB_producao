package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.model.Avaliacao;

/**
 * Uma competência com o resultado do aluno.
 *
 * <p>{@code conceito} nulo significa "ainda em avaliação" — é o que o boletim
 * mostra como travessão. O nome e a regra acompanham o conceito para que o
 * cliente não precise manter sua própria tabela de significados: hoje essa
 * tabela está duplicada na página, e mudá-la exigiria mexer nos dois lados.
 */
public record CompetenciaAvaliadaDTO(
        Long avaliacaoId,
        Long competenciaId,
        String nome,
        Integer ordem,
        String conceito,
        String conceitoNome,
        String conceitoRegra
) {
    public static CompetenciaAvaliadaDTO fromEntity(Avaliacao avaliacao) {
        var competencia = avaliacao.getCompetencia();
        var conceito = avaliacao.getConceito();
        return new CompetenciaAvaliadaDTO(
                avaliacao.getId(),
                competencia.getId(),
                competencia.getNome(),
                competencia.getOrdem(),
                conceito == null ? null : conceito.name(),
                conceito == null ? null : conceito.getNome(),
                conceito == null ? null : conceito.getRegra()
        );
    }
}
