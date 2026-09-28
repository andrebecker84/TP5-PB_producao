package com.andre.infnethub.notificacao.repository;

import com.andre.infnethub.notificacao.model.Notificacao;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByDestinatarioIdOrderByCriadaEmDescIdDesc(Long destinatarioId, Limit limite);

    long countByDestinatarioIdAndLidaEmIsNull(Long destinatarioId);

    boolean existsByMensagemOrigemIdAndDestinatarioId(UUID mensagemOrigemId, Long destinatarioId);

    List<Notificacao> findByIdInAndDestinatarioId(Collection<Long> ids, Long destinatarioId);

    List<Notificacao> findByPostId(Long postId);

    @Modifying
    @Query("UPDATE Notificacao n SET n.lidaEm = :agora WHERE n.destinatarioId = :destinatario AND n.lidaEm IS NULL")
    int marcarTodasComoLidas(Long destinatario, Instant agora);

    @Modifying
    @Query("DELETE FROM Notificacao n WHERE n.destinatarioId = :destinatario")
    int apagarTodasDe(Long destinatario);
}
