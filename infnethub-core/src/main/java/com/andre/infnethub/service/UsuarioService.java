package com.andre.infnethub.service;

import com.andre.infnethub.dto.UsuarioRequestDTO;
import com.andre.infnethub.dto.UsuarioResponseDTO;
import com.andre.infnethub.model.Papel;

import java.util.List;

public interface UsuarioService {

    List<UsuarioResponseDTO> listarTodos();

    List<UsuarioResponseDTO> listarPorPapel(Papel papel);

    /** Busca por nome ou e-mail — alimenta o campo de busca global do front-end. */
    List<UsuarioResponseDTO> buscar(String termo);

    UsuarioResponseDTO buscarPorId(Long id);

    UsuarioResponseDTO criar(UsuarioRequestDTO dto);

    UsuarioResponseDTO atualizar(Long id, UsuarioRequestDTO dto);

    void deletar(Long id);

    /**
     * Anuncia de novo o estado atual de todos os usuários.
     *
     * <p>Para o consumidor que chegou depois: uma fila do RabbitMQ não guarda
     * histórico, e um serviço novo não recebe os cadastros publicados antes de
     * a fila dele existir. Os consumidores são idempotentes e comparam versões,
     * então quem já tinha o estado não muda nada.
     *
     * @return quantos usuários foram reenviados.
     */
    int reenviarEstadoAtual();
}
