package com.andre.infnethub.service.impl;

import com.andre.infnethub.dto.ComentarioResponseDTO;
import com.andre.infnethub.dto.CurtidaResponseDTO;
import com.andre.infnethub.dto.PostRequestDTO;
import com.andre.infnethub.dto.PostResponseDTO;
import com.andre.infnethub.dto.historico.PaginaDTO;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.mensageria.EventosDoFeed;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.ComentarioRepository;
import com.andre.infnethub.repository.CurtidaRepository;
import com.andre.infnethub.repository.PostRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import com.andre.infnethub.seguranca.Solicitante;
import com.andre.infnethub.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository       postRepository;
    private final UsuarioRepository    usuarioRepository;
    private final ComentarioRepository comentarioRepository;
    private final CurtidaRepository    curtidaRepository;
    private final EventosDoFeed        eventos;

    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> listarTodos() {
        return montar(postRepository.findAllWithAutorOrderByDataDesc());
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDTO<PostResponseDTO> listarPaginado(Pageable pageable) {
        Page<Post> pagina = postRepository.findAllByOrderByCriadoEmDesc(pageable);
        Map<Long, PostResponseDTO> montados = montar(pagina.getContent()).stream()
                .collect(Collectors.toMap(PostResponseDTO::id, Function.identity()));
        return PaginaDTO.de(pagina, p -> montados.get(p.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> listarPorAutor(Long autorId) {
        return montar(postRepository.findByAutorId(autorId));
    }

    /**
     * Os posts com quem curtiu e os comentários, em três consultas no total,
     * independentemente do tamanho do feed: os posts (com o autor via JOIN
     * FETCH), as curtidas de todos e os comentários de todos.
     *
     * <p>O TP1 chamava {@code countByPostId} dentro do laço que montava o feed —
     * um SELECT por post, o problema N+1. A 1.0.0 resolveu isso no banco, mas a
     * tela ainda buscava curtidas e comentários card a card. Aqui as duas coisas
     * saem juntas.
     */
    private List<PostResponseDTO> montar(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();
        Map<Long, List<CurtidaResponseDTO>> curtidas = curtidaRepository.findByPostIdIn(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getPost().getId(),
                        Collectors.mapping(CurtidaResponseDTO::fromEntity, Collectors.toList())));
        Map<Long, List<ComentarioResponseDTO>> comentarios = comentarioRepository.findByPostIdIn(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getPost().getId(),
                        Collectors.mapping(ComentarioResponseDTO::fromEntity, Collectors.toList())));
        return posts.stream()
                .map(p -> PostResponseDTO.fromEntity(p,
                        curtidas.getOrDefault(p.getId(), List.of()),
                        comentarios.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponseDTO buscarPorId(Long id) {
        Post post = postRepository.findByIdWithAutor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado com id: " + id));
        return montar(List.of(post)).getFirst();
    }

    @Override
    @Transactional
    public PostResponseDTO criar(PostRequestDTO dto, Solicitante solicitante) {
        Usuario autor = usuarioRepository.findById(solicitante.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com id: " + solicitante.id()));

        Post post = Post.builder()
                .titulo(dto.titulo())
                .conteudo(dto.conteudo())
                .imagemUrl(dto.imagemUrl())
                .autor(autor)
                .curtidas(0)
                .build();

        Post saved = postRepository.save(post);
        return PostResponseDTO.fromEntity(saved, List.of(), List.of());
    }

    @Override
    @Transactional
    public PostResponseDTO atualizar(Long id, PostRequestDTO dto, Solicitante solicitante) {
        Post post = postRepository.findByIdWithAutor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado com id: " + id));
        solicitante.exigirQuePossaAlterar(post.getAutor().getId());
        post.setTitulo(dto.titulo());
        post.setConteudo(dto.conteudo());
        post.setImagemUrl(dto.imagemUrl());
        return montar(List.of(postRepository.save(post))).getFirst();
    }

    @Override
    @Transactional
    public void deletar(Long id, Solicitante solicitante) {
        Post post = postRepository.findByIdWithAutor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado com id: " + id));
        solicitante.exigirQuePossaAlterar(post.getAutor().getId());
        curtidaRepository.deleteByPostId(id);
        comentarioRepository.deleteByPostId(id);
        postRepository.deleteById(id);
        eventos.removido(id);
    }

}
