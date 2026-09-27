package com.andre.infnethub.boletim.model;

/**
 * Situação apurada de uma disciplina cursada.
 *
 * <p>Não é coluna: é resultado calculado a partir da frequência, dos conceitos
 * e das entregas, no momento da consulta. Persistir esse valor criaria um dado
 * derivado que precisa ser recalculado a cada conceito lançado — e que fica
 * errado em silêncio quando alguém esquece.
 */
public enum SituacaoDisciplina {

    APROVADO("Aprovado"),
    CURSANDO("Cursando"),
    REPROVADO("Reprovado");

    private final String descricao;

    SituacaoDisciplina(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
