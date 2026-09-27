package com.andre.infnethub.service.impl;

import com.andre.infnethub.dto.PostRequestDTO;
import com.andre.infnethub.dto.PostResponseDTO;
import com.andre.infnethub.dto.historico.PaginaDTO;
import com.andre.infnethub.exception.ResourceNotFoundException;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository       postRepository;
    private final UsuarioRepository    usuarioRepository;
    private final ComentarioRepository comentarioRepository;
    private final CurtidaRepository    curtidaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> listarTodos() {
        // Duas consultas no total, independentemente do tamanho do feed: os posts
        // (com o autor via JOIN FETCH) e as contagens de comentários agregadas.
        List<Post> posts = postRepository.findAllWithAutorOrderByDataDesc();
        Map<Long, Long> comentariosPorPost = contarComentarios(posts);

        return posts.stream()
                .map(p -> PostResponseDTO.fromEntity(p, comentariosPorPost.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDTO<PostResponseDTO> listarPaginado(Pageable pageable) {
        Page<Post> pagina = postRepository.findAllByOrderByCriadoEmDesc(pageable);
        Map<Long, Long> comentariosPorPost = contarComentarios(pagina.getContent());
        return PaginaDTO.de(pagina,
                p -> PostResponseDTO.fromEntity(p, comentariosPorPost.getOrDefault(p.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> listarPorAutor(Long autorId) {
        List<Post> posts = postRepository.findByAutorId(autorId);
        Map<Long, Long> comentariosPorPost = contarComentarios(posts);
        return posts.stream()
                .map(p -> PostResponseDTO.fromEntity(p, comentariosPorPost.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    /** Contagens de comentários de uma lista de posts em uma única consulta. */
    private Map<Long, Long> contarComentarios(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        return postRepository
                .contarComentariosPorPost(posts.stream().map(Post::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        PostRepository.ContagemPorPost::getPostId,
                        PostRepository.ContagemPorPost::getTotal));
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponseDTO buscarPorId(Long id) {
        Post post = postRepository.findByIdWithAutor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado com id: " + id));
        return PostResponseDTO.fromEntity(post, comentarioRepository.countByPostId(id));
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
        return PostResponseDTO.fromEntity(saved, 0L);
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
        return PostResponseDTO.fromEntity(postRepository.save(post), comentarioRepository.countByPostId(id));
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
    }

}
