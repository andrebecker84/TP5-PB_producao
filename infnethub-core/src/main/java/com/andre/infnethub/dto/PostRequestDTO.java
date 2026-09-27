package com.andre.infnethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostRequestDTO(

        @Size(max = 200)
        String titulo,

        @NotBlank(message = "Conteúdo é obrigatório")
        String conteudo,

        /** referência da capa (caminho servido pelo front ou URL) — opcional */
        @Size(max = 500)
        String imagemUrl

) {}
