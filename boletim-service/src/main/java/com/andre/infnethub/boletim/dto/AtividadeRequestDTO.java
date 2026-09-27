package com.andre.infnethub.boletim.dto;

import jakarta.validation.constraints.*;

/**
 * Entrada de criação e atualização de atividade acadêmica.
 *
 * <p>As restrições aqui repetem as CHECK constraints do banco de propósito. A
 * validação na borda devolve 400 com o campo exato que está errado; a do banco
 * devolve 409 sem essa precisão, e existe para o caso de duas requisições
 * concorrentes passarem juntas pela primeira. Uma não substitui a outra.
 */
public record AtividadeRequestDTO(

        @NotNull(message = "A categoria é obrigatória")
        @Pattern(regexp = "EXTENSAO|ELETIVA|ESTAGIO|COMPLEMENTAR",
                message = "Categoria inválida. Valores aceitos: EXTENSAO, ELETIVA, ESTAGIO, COMPLEMENTAR")
        String categoria,

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 200, message = "O nome deve ter no máximo 200 caracteres")
        String nome,

        @NotNull(message = "A carga horária é obrigatória")
        @Positive(message = "A carga horária deve ser maior que zero")
        Integer cargaHoraria,

        @Size(max = 10, message = "O período deve ter no máximo 10 caracteres")
        String periodo,

        @NotNull(message = "O status é obrigatório")
        @Pattern(regexp = "CONCLUIDO|EM_CURSO|NAO_CONCLUIDO",
                message = "Status inválido. Valores aceitos: CONCLUIDO, EM_CURSO, NAO_CONCLUIDO")
        String status,

        @Min(value = 0, message = "A presença não pode ser negativa")
        @Max(value = 100, message = "A presença não pode passar de 100")
        Integer presencaPercentual
) {
}
