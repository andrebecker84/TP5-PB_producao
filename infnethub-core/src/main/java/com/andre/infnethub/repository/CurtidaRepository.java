package com.andre.infnethub.repository;

import com.andre.infnethub.model.Curtida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acesso a {@link Curtida}.
 *
 * <p>Único repositório do domínio sem {@code RevisionRepository}: a entidade não
 * é auditada, e expor consultas de histórico aqui prometeria dados que não
 * existem.
 */
@Repository
public interface CurtidaRepository extends JpaRepository<Curtida, Long> {

    /**
     * Curtida de um usuário em um post, se houver.
     *
     * <p>O toggle precisa da entidade para removê-la, não apenas saber se
     * existe — por isso não há um {@code existsBy...} equivalente aqui.
     */
    Optional<Curtida> findByPostIdAndUsuarioId(Long postId, Long usuarioId);

    @Query("SELECT c FROM Curtida c JOIN FETCH c.usuario WHERE c.post.id = :postId")
    List<Curtida> findByPostId(Long postId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);
}
