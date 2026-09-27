package com.andre.infnethub.controller;

import com.andre.infnethub.dto.PostRequestDTO;
import com.andre.infnethub.dto.PostResponseDTO;
import com.andre.infnethub.dto.historico.PaginaDTO;
import com.andre.infnethub.seguranca.Solicitante;
import com.andre.infnethub.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * Feed completo, opcionalmente filtrado por autor.
     *
     * <p>Mantém o contrato do TP1 (array JSON) — o front-end existente depende
     * dele. Para volumes grandes, use {@code /paginado}.
     */
    @GetMapping
    public ResponseEntity<List<PostResponseDTO>> listarTodos(@RequestParam(required = false) Long autorId) {
        return ResponseEntity.ok(autorId == null
                ? postService.listarTodos()
                : postService.listarPorAutor(autorId));
    }

    /**
     * Feed paginado — o caminho recomendado conforme o volume cresce.
     *
     * <p>Rota separada em vez de parâmetro opcional em {@code GET /posts}: o tipo
     * da resposta mudaria conforme a presença do parâmetro, o que faria a mesma
     * rota devolver ora um array, ora um objeto.
     */
    @GetMapping("/paginado")
    public ResponseEntity<PaginaDTO<PostResponseDTO>> listarPaginado(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok(postService.listarPaginado(
                PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, 100))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(postService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<PostResponseDTO> criar(@Valid @RequestBody PostRequestDTO dto,
                                                 @AuthenticationPrincipal Jwt token) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.criar(dto, Solicitante.de(token)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody PostRequestDTO dto,
            @AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok(postService.atualizar(id, dto, Solicitante.de(token)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        postService.deletar(id, Solicitante.de(token));
        return ResponseEntity.noContent().build();
    }
}
