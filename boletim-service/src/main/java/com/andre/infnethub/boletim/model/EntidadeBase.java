package com.andre.infnethub.boletim.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Metadados comuns a todas as entidades do boletim.
 *
 * <p>Mais enxuta que a {@code EntidadeAuditavel} do infnethub-core: aqui não há
 * {@code criadoPor}/{@code atualizadoPor}. Aqueles campos existem lá para
 * alimentar o histórico do Envers, alimentado pelo header {@code X-Usuario-Id}.
 * Este serviço não guarda histórico por revisão, e registrar autoria sem nada
 * que a consulte seria coluna morta.
 *
 * <p>O {@code @Version} fica: é o bloqueio otimista, e ele importa aqui tanto
 * quanto no core. Duas correções de conceito enviadas ao mesmo tempo, sem ele,
 * terminam com a segunda sobrescrevendo a primeira sem que ninguém perceba.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class EntidadeBase {

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
