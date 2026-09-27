package com.andre.infnethub.boletim.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Lançamento ou correção do conceito de uma competência.
 *
 * <p>O campo aceita nulo, e isso é intencional: enviar {@code null} devolve a
 * competência ao estado "em avaliação", que é o caminho para desfazer um
 * lançamento equivocado. Sem ele, um conceito digitado errado só poderia ser
 * trocado por outro conceito — nunca retirado.
 */
public record ConceitoRequestDTO(

        @Pattern(regexp = "DML|DL|D|ND",
                message = "Conceito inválido. Valores aceitos: DML, DL, D, ND — ou nulo para voltar a 'em avaliação'")
        String conceito
) {
}
