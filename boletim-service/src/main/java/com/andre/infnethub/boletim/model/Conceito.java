package com.andre.infnethub.boletim.model;

/**
 * Resultado de uma competência no modelo de avaliação da Infnet.
 *
 * <p>Não há nota. O aluno demonstra — ou não — cada competência prevista, e o
 * resultado é um destes quatro conceitos. A ordem da enumeração vai do pior
 * para o melhor e é significativa: {@link #ordinal()} é o que permite comparar
 * dois conceitos e apurar o menor de uma disciplina.
 *
 * <p>O {@code peso} existe só para o painel de desempenho, que precisa de um
 * número para desenhar evolução ao longo do tempo. É indicador derivado, nunca
 * substitui o conceito e não aparece em documento oficial.
 */
public enum Conceito {

    ND("Não Demonstrou", "não atingiu a rubrica mínima ou não entregou algum TP", 0),
    D("Demonstrou", "cumpriu ao menos metade dos itens de rubrica no AT", 60),
    DL("Demonstrou com Louvor", "cumpriu ao menos 75% dos itens de rubrica no AT", 80),
    DML("Demonstrou com Máximo Louvor", "cumpriu todos os itens de rubrica da competência no AT", 100);

    private final String nome;
    private final String regra;
    private final int peso;

    Conceito(String nome, String regra, int peso) {
        this.nome = nome;
        this.regra = regra;
        this.peso = peso;
    }

    public String getNome() { return nome; }

    public String getRegra() { return regra; }

    public int getPeso() { return peso; }

    /**
     * O menor entre dois conceitos.
     *
     * <p>A situação de uma disciplina é ditada pela competência de pior
     * resultado: demonstrar quase todas não aprova.
     */
    public Conceito pior(Conceito outro) {
        return outro == null || this.ordinal() <= outro.ordinal() ? this : outro;
    }
}
