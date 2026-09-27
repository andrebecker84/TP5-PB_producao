package com.andre.infnethub.boletim.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate Root do percurso — um aluno cursando uma disciplina num trimestre.
 *
 * <p>É onde catálogo e aluno se encontram, e onde vive tudo o que varia de
 * pessoa para pessoa: frequência, entregas e, através das {@link Avaliacao},
 * os conceitos.
 *
 * <p>O {@code alunoId} é um {@code Long} solto, sem {@code @ManyToOne} e sem
 * chave estrangeira. Não é simplificação: a tabela {@code usuarios} está em
 * outra instância de PostgreSQL, e não há FK que atravesse bancos. Quem
 * confirma que o aluno existe é a aplicação, consultando o infnethub-core antes
 * de gravar.
 */
@Entity
@Table(name = "matricula")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Matricula extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id do Usuario no infnethub-core. Referência por valor — ver o Javadoc da classe. */
    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disciplina_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_matricula_disciplina"))
    private Disciplina disciplina;

    /** Trimestre letivo, no formato da instituição — "25E1", "26E2". */
    @Column(nullable = false, length = 10)
    private String periodo;

    @Column(name = "presenca_percentual", nullable = false)
    private Integer presencaPercentual;

    @Column(name = "tps_total", nullable = false)
    private Integer tpsTotal;

    @Column(name = "tps_atraso", nullable = false)
    private Integer tpsAtraso;

    @Column(name = "tps_pendentes", nullable = false)
    private Integer tpsPendentes;

    @OneToMany(mappedBy = "matricula", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Avaliacao> avaliacoes = new ArrayList<>();
}
