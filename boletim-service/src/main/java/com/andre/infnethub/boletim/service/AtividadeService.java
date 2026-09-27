package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.dto.AtividadeDTO;
import com.andre.infnethub.boletim.dto.AtividadeRequestDTO;
import com.andre.infnethub.boletim.dto.CargaCategoriaDTO;

import java.util.List;

/** Extensão, eletivas, estágio e atividades complementares de um aluno. */
public interface AtividadeService {

    List<AtividadeDTO> listarPorAluno(Long alunoId);

    List<AtividadeDTO> listarPorCategoria(Long alunoId, String categoria);

    /** Progresso de integralização por categoria — quanto falta de cada carga. */
    List<CargaCategoriaDTO> apurarCargaHoraria(Long alunoId);

    AtividadeDTO registrar(Long alunoId, AtividadeRequestDTO dto);

    AtividadeDTO atualizar(Long alunoId, Long id, AtividadeRequestDTO dto);

    void remover(Long alunoId, Long id);
}
