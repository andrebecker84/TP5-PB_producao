package com.andre.infnethub.notificacao.mensageria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface MensagensProcessadasRepository extends JpaRepository<MensagemProcessada, UUID> {
}
