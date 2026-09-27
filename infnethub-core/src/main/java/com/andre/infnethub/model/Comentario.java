package com.andre.infnethub.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/** Bounded Context: Feed — entidade do agregado {@link Post} */
@Entity
@Table(
        name = "comentarios",
        indexes = {
                // Comentários são sempre lidos por post, em ordem cronológica.
                @Index(name = "idx_comentarios_post_criado_em", columnList = "post_id, criado_em"),
                @Index(name = "idx_comentarios_autor", columnList = "autor_id")
        }
)
@Audited
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comentario extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_comentarios_post"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_comentarios_autor"))
    private Usuario autor;
}
