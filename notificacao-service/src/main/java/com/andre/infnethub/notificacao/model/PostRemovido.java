package com.andre.infnethub.notificacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A lápide de um post apagado.
 *
 * <p>Com consumidores em paralelo, a curtida de um post pode ser processada
 * depois da remoção dele — uma reentrega, uma fila atrasada. Sem a lápide, essa
 * curtida recriaria a notificação que a remoção acabou de apagar.
 */
@Entity
@Table(name = "post_removido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostRemovido {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "removido_em", nullable = false)
    private Instant removidoEm;

    public PostRemovido(Long postId) {
        this.postId = postId;
        this.removidoEm = Instant.now();
    }
}
