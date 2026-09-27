package com.andre.infnethub.auditoria;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Cabeçalho de uma revisão: o "quem, quando e de onde" compartilhado por todas
 * as entidades alteradas na mesma transação.
 *
 * <p>Substitui a tabela {@code REVINFO} padrão do Envers para acrescentar autor
 * e origem. O autor é gravado por extenso (nome e e-mail) em vez de uma chave
 * estrangeira para {@code usuarios}: um registro de auditoria precisa continuar
 * legível mesmo depois que o usuário que originou a mudança for removido.
 */
@Entity
@Table(name = "revisao_auditoria")
@RevisionEntity(RevisaoAuditoriaListener.class)
@Getter
@Setter
public class RevisaoAuditoria {

    @Id
    @RevisionNumber
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_revisao_auditoria")
    @SequenceGenerator(name = "seq_revisao_auditoria", sequenceName = "seq_revisao_auditoria", allocationSize = 1)
    @Column(name = "rev")
    private int rev;

    /** Epoch em milissegundos — formato exigido pelo Envers. */
    @RevisionTimestamp
    @Column(name = "rev_timestamp", nullable = false)
    private long timestamp;

    @Column(name = "autor", nullable = false, length = 150)
    private String autor;

    @Column(name = "origem", length = 100)
    private String origem;

    /** Conveniência de leitura: o epoch acima como data/hora local. */
    @Transient
    public LocalDateTime getDataHora() {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
