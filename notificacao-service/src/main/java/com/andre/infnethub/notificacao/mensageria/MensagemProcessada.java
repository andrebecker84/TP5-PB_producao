package com.andre.infnethub.notificacao.mensageria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * O registro de que uma mensagem já produziu efeito neste serviço.
 *
 * <p>{@link Persistable} com {@code isNew() = true}: o id vem da mensagem, não é
 * gerado, e sem isso o Spring Data trataria toda gravação como atualização —
 * faria um SELECT antes do INSERT para decidir. Aqui a gravação é sempre
 * inserção, e é justamente a violação da chave primária, no caso raro de duas
 * cópias simultâneas, que protege contra o efeito duplo.
 */
@Entity
@Table(name = "mensagem_processada")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MensagemProcessada implements Persistable<UUID> {

    @Id
    @Column(name = "mensagem_id")
    private UUID mensagemId;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(name = "processada_em", nullable = false)
    private LocalDateTime processadaEm;

    MensagemProcessada(UUID mensagemId, String tipo) {
        this.mensagemId = mensagemId;
        this.tipo = tipo;
        this.processadaEm = LocalDateTime.now();
    }

    @Override
    public UUID getId() {
        return mensagemId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
