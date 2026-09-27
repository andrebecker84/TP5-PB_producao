package com.andre.infnethub.notificacao.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Um aviso para uma pessoa.
 *
 * <p>O texto é gravado pronto, no momento em que o evento chega, e não montado a
 * cada leitura. Se o autor da curtida trocar de nome amanhã, a notificação de
 * hoje continua dizendo quem curtiu naquele dia — como uma mensagem já enviada,
 * e não como uma consulta.
 */
@Entity
@Table(name = "notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "destinatario_id", nullable = false)
    private Long destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 300)
    private String texto;

    @Column(length = 200)
    private String link;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "lida_em")
    private Instant lidaEm;

    @Column(name = "mensagem_origem_id", nullable = false)
    private UUID mensagemOrigemId;

    public Notificacao(Long destinatarioId, TipoNotificacao tipo, String texto, String link, UUID mensagemOrigemId) {
        this.destinatarioId = destinatarioId;
        this.tipo = tipo;
        this.texto = texto;
        this.link = link;
        this.mensagemOrigemId = mensagemOrigemId;
        this.criadaEm = Instant.now();
    }

    public void marcarComoLida() {
        if (lidaEm == null) {
            lidaEm = Instant.now();
        }
    }

    /** Volta a destacar no sino: a pessoa quer lembrar de ver depois. */
    public void marcarComoNaoLida() {
        lidaEm = null;
    }

    public boolean isLida() {
        return lidaEm != null;
    }
}
