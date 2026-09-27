package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.dto.CompetenciaAvaliadaDTO;

/** Lançamento e correção dos conceitos das competências. */
public interface AvaliacaoService {

    /**
     * Lança ou corrige o conceito de uma competência numa matrícula.
     *
     * <p>{@code conceito} nulo devolve a competência ao estado "em avaliação" —
     * é o caminho para desfazer um lançamento equivocado.
     */
    CompetenciaAvaliadaDTO registrarConceito(Long matriculaId, Long competenciaId, String conceito);
}
