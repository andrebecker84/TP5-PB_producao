package com.andre.infnethub.expurgo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpurgoRepository extends JpaRepository<ExpurgoLgpd, Long> {

    Optional<ExpurgoLgpd> findByUsuarioIdAndEstado(Long usuarioId, EstadoDoExpurgo estado);

    List<ExpurgoLgpd> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    List<ExpurgoLgpd> findByEstadoAndSolicitadoEmBefore(EstadoDoExpurgo estado, LocalDateTime limite);

    long countByEstado(EstadoDoExpurgo estado);

    /**
     * {@code SELECT … FOR UPDATE}: a resposta do participante e o vencimento do
     * prazo podem disputar o mesmo processo, e só um deles pode decidir.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ExpurgoLgpd> findComTravaById(Long id);
}
