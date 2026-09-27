package com.andre.infnethub.boletim.dto;

/**
 * Frequência numa disciplina, para o painel de desempenho.
 *
 * <p>{@code abaixoDoMinimo} vem calculado do servidor em vez de deixar o
 * cliente comparar com 75: o limite é regra do curso, e um segundo lugar onde
 * ele apareça é um segundo lugar para esquecer de atualizar.
 */
public record PresencaDisciplinaDTO(
        String disciplina,
        String periodo,
        int percentual,
        boolean isentaFrequencia,
        boolean abaixoDoMinimo
) {
}
