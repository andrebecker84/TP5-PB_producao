package com.andre.infnethub.boletim.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Disciplina do catálogo, pertencente a um {@link Bloco}. */
@Entity
@Table(name = "disciplina")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disciplina extends EntidadeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bloco_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_disciplina_bloco"))
    private Bloco bloco;

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDisciplina tipo;

    @Column(name = "carga_horaria", nullable = false)
    private Integer cargaHoraria;

    /** Disciplinas que não reprovam por frequência — ver {@code ck_disciplina_tipo}. */
    @Builder.Default
    @Column(name = "isenta_frequencia", nullable = false)
    private boolean isentaFrequencia = false;

    @OneToMany(mappedBy = "disciplina", fetch = FetchType.LAZY)
    @OrderBy("ordem ASC")
    @Builder.Default
    private List<Competencia> competencias = new ArrayList<>();
}
