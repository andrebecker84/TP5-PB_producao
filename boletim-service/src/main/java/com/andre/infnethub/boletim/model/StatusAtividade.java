package com.andre.infnethub.boletim.model;

/**
 * Andamento de uma atividade acadêmica. Só CONCLUIDO conta carga horária.
 *
 * <p>Serve também para descrever o progresso de um bloco no boletim: são os
 * mesmos três estados, com o mesmo significado para quem lê a tela. Uma segunda
 * enumeração idêntica só acrescentaria conversão entre as duas.
 */
public enum StatusAtividade {

    CONCLUIDO("Concluído"),
    EM_CURSO("Em curso"),
    NAO_CONCLUIDO("Não concluído");

    private final String descricao;

    StatusAtividade(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
