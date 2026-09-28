package com.andre.infnethub.dto;

import com.andre.infnethub.model.Post;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Um post como o feed o desenha, já com quem curtiu e os comentários.
 *
 * <p>Até a 1.0.0 o post vinha sem os dois, e cada card da tela buscava os seus
 * ao aparecer: duas requisições por post, cada uma atravessando o gateway até o
 * core. Um feed de 30 posts custava 61 idas e voltas. É o mesmo N+1 que o TP1
 * tinha no banco (ver {@code PostServiceImpl}), só que sobre HTTP. Agora o feed
 * inteiro é uma requisição, e o core o monta com três consultas.
 */
public record PostResponseDTO(
        Long id,
        String titulo,
        String conteudo,
        String imagemUrl,
        Long autorId,
        String autorNome,
        String autorEmail,
        String autorPapel,
        String autorPapelDescricao,
        Integer curtidas,
        Long totalComentarios,
        LocalDateTime criadoEm,
        List<CurtidaResponseDTO> curtidores,
        List<ComentarioResponseDTO> comentarios
) {
    public static PostResponseDTO fromEntity(Post post, List<CurtidaResponseDTO> curtidores,
                                             List<ComentarioResponseDTO> comentarios) {
        return new PostResponseDTO(
                post.getId(), post.getTitulo(), post.getConteudo(), post.getImagemUrl(),
                post.getAutor().getId(), post.getAutor().getNome(), post.getAutor().getEmail(),
                post.getAutor().getPapel().name(), post.getAutor().getPapel().getDescricao(),
                post.getCurtidas(), (long) comentarios.size(), post.getCriadoEm(),
                curtidores, comentarios
        );
    }
}
