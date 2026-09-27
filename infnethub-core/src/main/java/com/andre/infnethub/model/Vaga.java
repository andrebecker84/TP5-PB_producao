package com.andre.infnethub.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/** Aggregate Root — Bounded Context: Oportunidades */
@Entity
@Table(
        name = "vagas",
        indexes = {
                // A listagem sempre filtra por ativo e ordena por data; o filtro
                // por tipo é o segundo acesso mais frequente.
                @Index(name = "idx_vagas_ativo_criado_em", columnList = "ativo, criado_em"),
                @Index(name = "idx_vagas_tipo", columnList = "tipo"),
                @Index(name = "idx_vagas_criador", columnList = "criador_id")
        }
)
@Audited
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vaga extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 150)
    private String empresa;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 100)
    private String localizacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoVaga tipo;

    @Column(length = 100)
    private String categoria;

    /** Encerramento é lógico: a vaga sai da listagem mas continua auditável. */
    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criador_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_vagas_criador"))
    private Usuario criador;
}
