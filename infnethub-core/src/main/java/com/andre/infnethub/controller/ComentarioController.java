package com.andre.infnethub.controller;

import com.andre.infnethub.dto.ComentarioRequestDTO;
import com.andre.infnethub.dto.ComentarioResponseDTO;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.mensageria.EventosDoFeed;
import com.andre.infnethub.model.Comentario;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.ComentarioRepository;
import com.andre.infnethub.repository.PostRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import com.andre.infnethub.seguranca.Solicitante;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts/{postId}/comentarios")
@RequiredArgsConstructor
public class ComentarioController {

    private final ComentarioRepository comentarioRepository;
    private final PostRepository postRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventosDoFeed eventos;

    @GetMapping
    public ResponseEntity<List<ComentarioResponseDTO>> listar(@PathVariable Long postId) {
        return ResponseEntity.ok(
                comentarioRepository.findByPostId(postId)
                        .stream().map(ComentarioResponseDTO::fromEntity).toList()
        );
    }

    /**
     * {@code @Transactional} aqui, e não só no repositório: o comentário e o
     * evento que o anuncia precisam da mesma transação. Sem ela, o
     * {@code save} fecharia a sua antes de o evento ser depositado — e a
     * caixa de saída recusaria o depósito, que exige transação ativa.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ComentarioResponseDTO> criar(
            @PathVariable Long postId,
            @Valid @RequestBody ComentarioRequestDTO dto,
            @AuthenticationPrincipal Jwt token) {

        Long autorId = Solicitante.de(token).id();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado: " + postId));
        Usuario autor = usuarioRepository.findById(autorId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + autorId));

        Comentario comentario = Comentario.builder()
                .conteudo(dto.conteudo())
                .post(post)
                .autor(autor)
                .build();

        Comentario salvo = comentarioRepository.save(comentario);
        eventos.comentado(salvo);
        return ResponseEntity.status(HttpStatus.CREATED).body(ComentarioResponseDTO.fromEntity(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ComentarioResponseDTO> atualizar(
            @PathVariable Long postId,
            @PathVariable Long id,
            @Valid @RequestBody ComentarioRequestDTO dto,
            @AuthenticationPrincipal Jwt token) {

        Comentario comentario = doPost(postId, id);
        Solicitante.de(token).exigirQuePossaAlterar(comentario.getAutor().getId());
        comentario.setConteudo(dto.conteudo());
        return ResponseEntity.ok(ComentarioResponseDTO.fromEntity(comentarioRepository.save(comentario)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long postId, @PathVariable Long id,
                                        @AuthenticationPrincipal Jwt token) {
        Comentario comentario = doPost(postId, id);
        Solicitante.de(token).exigirQuePossaAlterar(comentario.getAutor().getId());
        comentarioRepository.delete(comentario);
        return ResponseEntity.noContent().build();
    }

    /** O comentário, desde que pertença ao post da rota; senão, para quem pede, ele não existe. */
    private Comentario doPost(Long postId, Long id) {
        return comentarioRepository.findById(id)
                .filter(c -> c.getPost().getId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado: " + id));
    }
}
