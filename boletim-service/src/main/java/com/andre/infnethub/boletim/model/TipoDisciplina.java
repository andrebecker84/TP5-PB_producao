package com.andre.infnethub.boletim.model;

/**
 * Papel da disciplina dentro do bloco.
 *
 * <p>A distinção importa para a aprovação: em blocos iniciados a partir de
 * 2025, ser aprovado no Projeto de Bloco é requisito à parte, somado às
 * competências, à frequência e às entregas.
 */
public enum TipoDisciplina {

    REGULAR("Disciplina regular"),
    PROJETO_BLOCO("Projeto de Bloco");

    private final String descricao;

    TipoDisciplina(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
