package com.andre.infnethub.service;

import com.andre.infnethub.dto.CurtidaResponseDTO;

import java.util.List;

/** Porta de serviço — Bounded Context: Feed (agregado {@link com.andre.infnethub.model.Post}) */
public interface CurtidaService {

    List<CurtidaResponseDTO> listarPorPost(Long postId);

    /**
     * Curte ou descurte, conforme o estado atual.
     *
     * @return o resultado da alternância e o total de curtidas do post
     */
    ResultadoCurtida alternar(Long postId, Long usuarioId);

    record ResultadoCurtida(boolean curtido, long total) {
    }
}
