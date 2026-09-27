package com.andre.infnethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VagaRequestDTO(

        @NotBlank(message = "Título é obrigatório")
        @Size(max = 150)
        String titulo,

        @NotBlank(message = "Empresa é obrigatória")
        @Size(max = 150)
        String empresa,

        String descricao,

        @Size(max = 100)
        String localizacao,

        @NotBlank(message = "Tipo é obrigatório")
        String tipo,

        @Size(max = 100)
        String categoria,

        @NotNull(message = "ID do criador é obrigatório")
        Long criadorId

) {}
