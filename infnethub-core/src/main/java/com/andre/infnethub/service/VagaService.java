package com.andre.infnethub.service;

import com.andre.infnethub.dto.VagaRequestDTO;
import com.andre.infnethub.dto.VagaResponseDTO;
import com.andre.infnethub.dto.historico.PaginaDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Porta de serviço — Bounded Context: Oportunidades (SRP + DIP) */
public interface VagaService {

    List<VagaResponseDTO> listarAtivas();

    /** Listagem paginada de vagas ativas — apoiada em idx_vagas_ativo_criado_em. */
    PaginaDTO<VagaResponseDTO> listarAtivasPaginado(Pageable pageable);

    List<VagaResponseDTO> listarPorTipo(String tipo);

    VagaResponseDTO buscarPorId(Long id);

    VagaResponseDTO criar(VagaRequestDTO dto);

    VagaResponseDTO atualizar(Long id, VagaRequestDTO dto);

    void deletar(Long id);
}
