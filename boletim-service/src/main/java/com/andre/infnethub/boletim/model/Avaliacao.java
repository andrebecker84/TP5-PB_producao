package com.andre.infnethub.boletim.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * O resultado de uma competência para um aluno numa matrícula.
 *
 * <p>O {@code conceito} nulo é estado legítimo do domínio: significa
 * "competência ainda em avaliação". É diferente de {@link Conceito#ND}, que é
 * um resultado apurado e reprova. A distinção é o que separa uma disciplina
 * <em>Cursando</em> de uma <em>Reprovada</em>, e por isso o campo é anulável em
 * vez de ganhar um valor de enumeração para "pendente" — um estado de
 * preenchimento não pertence à mesma escala dos resultados.
 */
@Entity
@Table(name = "avaliacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avaliacao extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_avaliacao_matricula"))
    private Matricula matricula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competencia_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_avaliacao_competencia"))
    private Competencia competencia;

    /** Nulo enquanto a competência não foi avaliada — ver o Javadoc da classe. */
    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    private Conceito conceito;

    /** Momento do lançamento. Nulo enquanto não há conceito. */
    @Column(name = "avaliado_em")
    private LocalDateTime avaliadoEm;

    /** Lança ou corrige o conceito, mantendo a data coerente com o valor. */
    public void registrar(Conceito novo) {
        this.conceito = novo;
        this.avaliadoEm = novo == null ? null : LocalDateTime.now();
    }
}
