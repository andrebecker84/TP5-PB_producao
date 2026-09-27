package com.andre.infnethub.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Metadados de auditoria comuns a todas as entidades do domínio.
 *
 * <p>No TP1 cada entidade repetia o próprio {@code @PrePersist} para carimbar
 * {@code criadoEm}. Aqui isso vira responsabilidade do
 * {@link AuditingEntityListener}, que também registra o autor — o carimbo
 * deixa de ser copiado entidade a entidade e passa a ter um único ponto de
 * mudança.
 *
 * <p>Estes campos descrevem o <b>estado atual</b> da linha e ficam de fora das
 * tabelas {@code _aud} — o Envers não audita os campos de uma
 * {@code @MappedSuperclass} que não seja ela própria {@code @Audited}, e aqui
 * isso é o comportamento desejado: quem alterou e quando já são gravados por
 * revisão em {@code revisao_auditoria}. Duplicá-los em cada linha de auditoria
 * só aumentaria o volume sem responder nenhuma pergunta nova.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class EntidadeAuditavel {

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @CreatedBy
    @Column(name = "criado_por", nullable = false, updatable = false, length = 150)
    private String criadoPor;

    @LastModifiedBy
    @Column(name = "atualizado_por", nullable = false, length = 150)
    private String atualizadoPor;

    /**
     * Bloqueio otimista: o UPDATE carrega a versão lida e não afeta nenhuma
     * linha se outra transação já tiver gravado por cima, resultando em
     * {@code OptimisticLockException} em vez de uma atualização perdida
     * silenciosamente.
     */
    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
