package com.andre.infnethub.model;

/** Bounded Context: Oportunidades — tipos de contratação */
public enum TipoVaga {
    ESTAGIO("Estágio"),
    CLT("Emprego (CLT)"),
    PJ("PJ (Terceirizado)"),
    TRAINEE("Emprego Trainee (CLT)"),
    AUTONOMO("Autônomo"),
    EXTERIOR("Emprego no Exterior");

    private final String descricao;

    TipoVaga(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
