package com.andre.infnethub.repository;

import com.andre.infnethub.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>,
        RevisionRepository<Post, Long, Integer> {

    /**
     * Feed completo. O {@code JOIN FETCH} traz o autor na mesma consulta —
     * necessário porque {@code open-in-view=false} fecha a sessão antes da
     * serialização, e um autor LAZY não carregado quebraria a montagem do DTO.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.autor ORDER BY p.criadoEm DESC")
    List<Post> findAllWithAutorOrderByDataDesc();

    /**
     * Variante paginada. Usa {@code @EntityGraph} em vez de {@code JOIN FETCH}
     * porque paginar uma consulta com fetch join obrigaria o Hibernate a trazer
     * todas as linhas para a memória antes de recortar a página.
     */
    @EntityGraph(attributePaths = "autor")
    Page<Post> findAllByOrderByCriadoEmDesc(Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.autor WHERE p.id = :id")
    Optional<Post> findByIdWithAutor(Long id);

    @Query("SELECT p FROM Post p JOIN FETCH p.autor WHERE p.autor.id = :autorId ORDER BY p.criadoEm DESC")
    List<Post> findByAutorId(Long autorId);

    /**
     * Total de comentários de vários posts em uma única consulta.
     *
     * <p>O TP1 chamava {@code countByPostId} dentro do laço que montava o feed,
     * gerando um SELECT por post (problema N+1): 50 posts custavam 51 consultas.
     * Agregando com {@code IN}, o feed inteiro custa duas — uma para os posts,
     * outra para as contagens.
     */
    @Query("""
            SELECT c.post.id AS postId, COUNT(c.id) AS total
            FROM Comentario c
            WHERE c.post.id IN :postIds
            GROUP BY c.post.id
            """)
    List<ContagemPorPost> contarComentariosPorPost(Collection<Long> postIds);

    /** Projeção de interface: o Spring Data implementa os getters a partir dos alias. */
    interface ContagemPorPost {
        Long getPostId();

        Long getTotal();
    }
}
