package com.andre.infnethub.dto;

import com.andre.infnethub.model.Vaga;

import java.time.LocalDateTime;

public record VagaResponseDTO(
        Long id,
        String titulo,
        String empresa,
        String descricao,
        String localizacao,
        String tipo,
        String tipoDescricao,
        String categoria,
        boolean ativo,
        Long criadorId,
        String criadorNome,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static VagaResponseDTO fromEntity(Vaga v) {
        return new VagaResponseDTO(
                v.getId(), v.getTitulo(), v.getEmpresa(),
                v.getDescricao(), v.getLocalizacao(),
                v.getTipo().name(), v.getTipo().getDescricao(),
                v.getCategoria(), v.isAtivo(),
                v.getCriador().getId(), v.getCriador().getNome(),
                v.getCriadoEm(), v.getAtualizadoEm()
        );
    }
}
