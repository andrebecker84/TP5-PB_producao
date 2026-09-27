package com.andre.infnethub.boletim.mensageria;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<MensagemNoOutbox, Long> {

    /**
     * O próximo lote a publicar, travado para esta instância.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} — o tempo de espera {@code -2} é como o
     * Hibernate pede o {@code SKIP LOCKED} ao PostgreSQL. Com duas instâncias do
     * core, a segunda não espera nem relê o lote da primeira: pula as linhas
     * travadas e pega as seguintes. Sem isso, as duas publicariam o mesmo
     * evento. O consumidor idempotente absorveria a duplicata, mas é melhor não
     * produzi-la.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<MensagemNoOutbox> findByPublicadoEmIsNullOrderByIdAsc(Limit tamanhoDoLote);

    List<MensagemNoOutbox> findByPublicadoEmIsNullOrderByIdAsc();

    List<MensagemNoOutbox> findByChaveOrderByIdAsc(String chave);

    long countByPublicadoEmIsNull();

    Optional<MensagemNoOutbox> findFirstByPublicadoEmIsNullOrderByIdAsc();

    boolean existsByMensagemId(UUID mensagemId);
}
