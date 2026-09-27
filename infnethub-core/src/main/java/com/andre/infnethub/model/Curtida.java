package com.andre.infnethub.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bounded Context: Feed — entidade do agregado {@link Post}.
 *
 * <p>Diferente das demais entidades do domínio, esta <b>não</b> é
 * {@code @Audited}. Uma curtida não tem estado mutável: ela é criada e removida,
 * nunca editada — a própria linha já é o registro do fato, e sua ausência é o
 * registro da remoção. Auditá-la produziria um par de revisões por clique,
 * inflando o histórico com o evento de maior volume e menor valor de
 * rastreabilidade da plataforma. O que se audita aqui é a intenção editorial
 * (posts, comentários, vagas, identidade), não o engajamento.
 */
@Entity
@Table(
        name = "curtidas",
        uniqueConstraints = {
                // Barra a curtida duplicada no banco, e não apenas na aplicação:
                // é o que impede o contador de divergir sob requisições concorrentes.
                @UniqueConstraint(name = "uk_curtidas_post_usuario", columnNames = {"post_id", "usuario_id"})
        },
        indexes = {
                @Index(name = "idx_curtidas_post", columnList = "post_id"),
                @Index(name = "idx_curtidas_usuario", columnList = "usuario_id")
        }
)
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Curtida extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_curtidas_post"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_curtidas_usuario"))
    private Usuario usuario;
}
