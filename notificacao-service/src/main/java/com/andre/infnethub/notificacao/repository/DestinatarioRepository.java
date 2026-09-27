package com.andre.infnethub.notificacao.repository;

import com.andre.infnethub.notificacao.model.Destinatario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DestinatarioRepository extends JpaRepository<Destinatario, Long> {

    List<Destinatario> findByPapelAndRemovidoFalse(String papel);
}
