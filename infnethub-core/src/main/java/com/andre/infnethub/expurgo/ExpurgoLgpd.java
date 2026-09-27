package com.andre.infnethub.expurgo;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * O registro de um pedido de remoção e do que aconteceu com ele.
 *
 * <p>Sem esta tabela, a saga existiria apenas como mensagens em trânsito, e a
 * pergunta que um titular de dados tem direito de fazer — "o que foi feito com
 * os meus dados?" — não teria onde ser respondida. Aqui ela tem: quem pediu,
 * quando, em que estado parou, por qual motivo, e o que ficou guardado.
 *
 * <p>Não é auditada pelo Envers: já é, ela própria, o registro do processo. O
 * histórico do usuário continua nas revisões de {@code usuarios}.
 */
@Entity
@Table(name = "expurgo_lgpd")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpurgoLgpd {

    private static final int TAMANHO_MAXIMO_MOTIVO = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDoExpurgo estado;

    @Column(name = "solicitado_por")
    private Long solicitadoPor;

    @Column(name = "solicitado_em", nullable = false)
    private LocalDateTime solicitadoEm;

    @Column(name = "encerrado_em")
    private LocalDateTime encerradoEm;

    @Column(length = TAMANHO_MAXIMO_MOTIVO)
    private String motivo;

    @Column(name = "registros_mantidos")
    private Integer registrosMantidos;

    public ExpurgoLgpd(Long usuarioId, Long solicitadoPor) {
        this.usuarioId = usuarioId;
        this.solicitadoPor = solicitadoPor;
        this.estado = EstadoDoExpurgo.SOLICITADO;
        this.solicitadoEm = LocalDateTime.now();
    }

    public void concluir(int registrosMantidos) {
        this.estado = EstadoDoExpurgo.CONCLUIDO;
        this.registrosMantidos = registrosMantidos;
        this.encerradoEm = LocalDateTime.now();
    }

    public void reverter(String motivo) {
        this.estado = EstadoDoExpurgo.REVERTIDO;
        this.motivo = motivo == null || motivo.length() <= TAMANHO_MAXIMO_MOTIVO
                ? motivo
                : motivo.substring(0, TAMANHO_MAXIMO_MOTIVO);
        this.encerradoEm = LocalDateTime.now();
    }

    public boolean emAndamento() {
        return estado.emAndamento();
    }
}
