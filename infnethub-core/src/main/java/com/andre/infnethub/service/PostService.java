package com.andre.infnethub.service;

import com.andre.infnethub.dto.PostRequestDTO;
import com.andre.infnethub.dto.PostResponseDTO;
import com.andre.infnethub.dto.historico.PaginaDTO;
import com.andre.infnethub.seguranca.Solicitante;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostService {

    List<PostResponseDTO> listarTodos();

    /**
     * Feed paginado.
     *
     * <p>O feed cresce sem limite; carregá-lo inteiro degrada de forma linear
     * com o volume. Existe ao lado de {@link #listarTodos()} em vez de
     * substituí-lo para não quebrar o contrato do TP1 nem o front-end atual.
     */
    PaginaDTO<PostResponseDTO> listarPaginado(Pageable pageable);

    List<PostResponseDTO> listarPorAutor(Long autorId);

    PostResponseDTO buscarPorId(Long id);

    PostResponseDTO criar(PostRequestDTO dto, Solicitante autor);

    void deletar(Long id, Solicitante solicitante);

    PostResponseDTO atualizar(Long id, PostRequestDTO dto, Solicitante solicitante);

    // Curtir não vive aqui: é responsabilidade de CurtidaService, o único que
    // consegue manter a linha em `curtidas` e o contador de `posts` coerentes.
}
