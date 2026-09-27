package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.model.Matricula;

/**
 * Situação das entregas de uma disciplina.
 *
 * <p>{@code entregues} é derivado ({@code total - pendentes}) e vai calculado
 * pelo servidor de propósito: é o número que a interface exibe, e deixá-lo para
 * o cliente é convidar cada consumidor a recalcular — e a errar de forma
 * diferente.
 */
public record TpsDTO(
        int total,
        int entregues,
        int atraso,
        int pendentes
) {
    public static TpsDTO fromEntity(Matricula matricula) {
        return new TpsDTO(
                matricula.getTpsTotal(),
                matricula.getTpsTotal() - matricula.getTpsPendentes(),
                matricula.getTpsAtraso(),
                matricula.getTpsPendentes()
        );
    }
}
