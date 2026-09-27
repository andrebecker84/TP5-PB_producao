package com.andre.infnethub.boletim.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Atividade fora das disciplinas dos blocos: extensão, eletiva, estágio ou
 * atividade complementar.
 *
 * <p>Uma tabela só para as quatro categorias. Elas têm exatamente os mesmos
 * atributos, e o que as diferencia — a carga exigida, a exigência de presença —
 * é regra do curso, que vive em {@link CategoriaAtividade}. Quatro tabelas
 * idênticas obrigariam a repetir consulta, repositório e mapeamento quatro
 * vezes para separar dados que só diferem numa coluna.
 */
@Entity
@Table(name = "atividade_academica")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtividadeAcademica extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id do Usuario no infnethub-core — referência por valor, sem FK entre bancos. */
    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaAtividade categoria;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(name = "carga_horaria", nullable = false)
    private Integer cargaHoraria;

    /** Trimestre da realização. Nulo em atividade ainda não iniciada. */
    @Column(length = 10)
    private String periodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAtividade status;

    /** Só as eletivas exigem presença; nas demais categorias fica nulo. */
    @Column(name = "presenca_percentual")
    private Integer presencaPercentual;
}
