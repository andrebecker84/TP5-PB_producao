package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.model.Conceito;

/**
 * Um degrau da escala de conceitos, com o que ele significa.
 *
 * <p>Existe para que a legenda do boletim não precise manter sua própria cópia
 * dos nomes e das regras. Enquanto essa tabela viveu na página, havia duas
 * descrições do mesmo conceito no sistema — e nada garantia que continuassem
 * iguais depois da primeira correção de texto.
 *
 * <p>Vem ordenada do pior para o melhor, que é a ordem em que a escala é lida.
 */
public record ConceitoDTO(
        String codigo,
        String nome,
        String regra,
        int peso
) {
    public static ConceitoDTO fromEnum(Conceito conceito) {
        return new ConceitoDTO(conceito.name(), conceito.getNome(), conceito.getRegra(), conceito.getPeso());
    }
}
