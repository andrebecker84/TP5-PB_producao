package com.andre.infnethub.boletim.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate Root do catálogo — um bloco do curso.
 *
 * <p>Estrutura, não percurso: descreve o que o bloco é para qualquer aluno. O
 * trimestre em que cada aluno o cursou fica na {@link Matricula}, porque varia
 * de pessoa para pessoa.
 */
@Entity
@Table(name = "bloco")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bloco extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 150)
    private String titulo;

    /**
     * LAZY como padrão de segurança: o boletim carrega as disciplinas por
     * consulta explícita com JOIN FETCH, e não por navegação. Com EAGER, listar
     * blocos traria a árvore inteira mesmo quando só os títulos são usados.
     */
    @OneToMany(mappedBy = "bloco", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<Disciplina> disciplinas = new ArrayList<>();
}
