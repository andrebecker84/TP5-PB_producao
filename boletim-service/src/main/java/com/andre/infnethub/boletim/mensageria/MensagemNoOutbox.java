package com.andre.infnethub.boletim.mensageria;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Um evento à espera de ser entregue ao broker.
 *
 * <p>Não é entidade de domínio e não é auditada pelo Envers: é infraestrutura de
 * entrega. O histórico que importa — quem alterou o usuário e quando — já está
 * nas revisões do próprio {@code Usuario}.
 */
@Entity
@Table(name = "outbox_mensagem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MensagemNoOutbox {

    private static final int TAMANHO_MAXIMO_ERRO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensagem_id", nullable = false, unique = true)
    private UUID mensagemId;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(nullable = false, length = 100)
    private String rota;

    @Column(nullable = false, length = 100)
    private String chave;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "publicado_em")
    private LocalDateTime publicadoEm;

    /** Quanto esperar no broker antes da entrega; zero entrega imediatamente. */
    @Column(name = "atraso_ms", nullable = false)
    private long atrasoMs;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = TAMANHO_MAXIMO_ERRO)
    private String ultimoErro;

    /**
     * O contexto de rastreamento (W3C {@code traceparent}) de quem gerou o
     * evento. É o que liga a publicação, feita depois pelo relay, à requisição
     * que a originou — ver {@link com.andre.infnethub.boletim.observabilidade.Rastreamento}.
     */
    @Column(length = 55)
    private String rastreamento;

    MensagemNoOutbox(UUID mensagemId, String tipo, String rota, String chave, String payload, long atrasoMs,
                     String rastreamento) {
        this.mensagemId = mensagemId;
        this.tipo = tipo;
        this.rota = rota;
        this.chave = chave;
        this.payload = payload;
        this.atrasoMs = atrasoMs;
        this.rastreamento = rastreamento;
        this.criadoEm = LocalDateTime.now();
    }

    boolean temAtraso() {
        return atrasoMs > 0;
    }

    void marcarPublicada() {
        this.publicadoEm = LocalDateTime.now();
        this.tentativas++;
        this.ultimoErro = null;
    }

    void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro == null || erro.length() <= TAMANHO_MAXIMO_ERRO
                ? erro
                : erro.substring(0, TAMANHO_MAXIMO_ERRO);
    }

    public boolean isPublicada() {
        return publicadoEm != null;
    }
}
