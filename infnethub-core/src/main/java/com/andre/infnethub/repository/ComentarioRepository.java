package com.andre.infnethub.repository;

import com.andre.infnethub.model.Comentario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ComentarioRepository extends JpaRepository<Comentario, Long>,
        RevisionRepository<Comentario, Long, Integer> {

    @Query("SELECT c FROM Comentario c JOIN FETCH c.autor WHERE c.post.id = :postId ORDER BY c.criadoEm ASC")
    List<Comentario> findByPostId(Long postId);

    /** Os comentários de vários posts numa consulta só, em ordem de chegada. */
    @Query("SELECT c FROM Comentario c JOIN FETCH c.autor WHERE c.post.id IN :postIds ORDER BY c.criadoEm ASC, c.id ASC")
    List<Comentario> findByPostIdIn(Collection<Long> postIds);

    long countByPostId(Long postId);

    /**
     * Remove os comentários de um post carregando cada entidade antes de apagar.
     *
     * <p>Um {@code @Query} com {@code DELETE} em lote seria mais rápido, mas
     * passaria ao largo do contexto de persistência — e o Envers, que trabalha
     * sobre os eventos do Hibernate, não registraria as revisões de exclusão.
     * O histórico dos comentários simplesmente terminaria sem explicação. Aqui a
     * rastreabilidade vale mais que as consultas extras.
     */
    void deleteByPostId(Long postId);
}
