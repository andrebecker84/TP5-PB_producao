package com.andre.infnethub.repository;

import com.andre.infnethub.auditoria.RevisaoAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Leitura da linha do tempo de auditoria.
 *
 * <p>Os {@code RevisionRepository} respondem "o que aconteceu com este
 * registro?". Este responde a pergunta transversal — "o que aconteceu no
 * sistema, e quem fez?" — lendo o cabeçalho das revisões sem passar por
 * nenhuma tabela {@code _aud}.
 *
 * <p>Somente leitura por natureza: revisões são criadas exclusivamente pelo
 * Envers. Um registro de auditoria que a aplicação pudesse editar não serviria
 * como registro de auditoria.
 */
@Repository
public interface RevisaoAuditoriaRepository extends JpaRepository<RevisaoAuditoria, Integer> {

    Page<RevisaoAuditoria> findAllByOrderByRevDesc(Pageable pageable);

    /**
     * Tudo o que um autor alterou — a pergunta clássica de uma auditoria.
     *
     * <p>Busca por conteúdo porque o autor é gravado por extenso ("Nome
     * &lt;email&gt;"): quem investiga costuma ter o nome ou o e-mail, não a
     * string exata. Apoiada em idx_revisao_auditoria_autor.
     */
    @Query("SELECT r FROM RevisaoAuditoria r WHERE LOWER(r.autor) LIKE LOWER(CONCAT('%', :autor, '%')) ORDER BY r.rev DESC")
    Page<RevisaoAuditoria> buscarPorAutor(String autor, Pageable pageable);
}
