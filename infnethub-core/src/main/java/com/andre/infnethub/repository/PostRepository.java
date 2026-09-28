package com.andre.infnethub.repository;

import com.andre.infnethub.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

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
}
