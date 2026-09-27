package com.andre.infnethub.boletim.dto;

/**
 * Índice do aluno em um bloco.
 *
 * <p>A série destes pontos é o que desenha a evolução no painel de desempenho.
 * O eixo é o bloco, e não o mês: o conceito é lançado ao fim do trimestre, e
 * uma série mensal mostraria degraus artificiais entre lançamentos.
 */
public record EvolucaoBlocoDTO(
        Integer numeroBloco,
        String titulo,
        String periodo,
        int indice,
        String conceitoEquivalente,
        int competenciasAvaliadas
) {
}
