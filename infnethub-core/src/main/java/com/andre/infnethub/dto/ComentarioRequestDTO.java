package com.andre.infnethub.dto;

import jakarta.validation.constraints.NotBlank;

public record ComentarioRequestDTO(
        @NotBlank(message = "Conteúdo é obrigatório") String conteudo
) {}
