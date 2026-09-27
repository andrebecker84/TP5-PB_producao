package com.andre.infnethub.dto;

import com.andre.infnethub.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 100)
        String nome,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 150)
        String email,

        @Size(max = 100)
        String escola,

        @Size(max = 50)
        String ultimoBloco,

        @Size(max = 20)
        String classe

) {
    public Usuario toEntity() {
        return Usuario.builder()
                .nome(this.nome)
                .email(this.email)
                .escola(this.escola)
                .ultimoBloco(this.ultimoBloco)
                .classe(this.classe)
                .build();
    }
}
