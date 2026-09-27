package com.andre.infnethub.boletim.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Competência prevista no plano de ensino de uma disciplina.
 *
 * <p>Guarda só o enunciado. O resultado do aluno não está aqui — está em
 * {@link Avaliacao}, ligado à matrícula. É essa separação que permite ao mesmo
 * texto de competência valer para todas as turmas sem se repetir por aluno.
 */
@Entity
@Table(name = "competencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Competencia extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disciplina_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_competencia_disciplina"))
    private Disciplina disciplina;

    @Column(nullable = false, length = 200)
    private String nome;

    /** Posição no plano de ensino — mantém a listagem estável entre consultas. */
    @Column(nullable = false)
    private Integer ordem;
}
