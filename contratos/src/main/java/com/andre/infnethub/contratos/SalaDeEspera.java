package com.andre.infnethub.contratos;

import java.time.Duration;
import java.util.StringJoiner;

/**
 * Mensagem com atraso sem bloqueio de cabeça de fila — só com recursos do
 * RabbitMQ, sem plugin e sem agendador em serviço nenhum.
 *
 * <h2>O problema que isto resolve</h2>
 * <p>A forma mais simples de atrasar uma mensagem é publicá-la com
 * {@code expiration} numa fila sem consumidor e com dead-letter: quando o prazo
 * vence, o broker a reencaminha. Mas fila é fila, e o RabbitMQ só expira a
 * mensagem <em>da frente</em>. Um aviso para daqui a 5 segundos atrás de um para
 * daqui a 1 hora espera a hora inteira. Com prazos diferentes na mesma fila, o
 * padrão está simplesmente errado.
 *
 * <h2>A solução: uma cascata de níveis binários</h2>
 * <p>Se todas as mensagens de uma fila têm o <strong>mesmo</strong> prazo, a
 * ordem de chegada é a ordem de vencimento, e o bloqueio desaparece. Então há
 * uma fila por potência de dois — o nível {@code i} segura cada mensagem por
 * exatamente 2<sup>i</sup> segundos, definido na fila ({@code x-message-ttl}),
 * e não na mensagem. Um atraso qualquer é a soma dos níveis dos seus bits: 13 s
 * = 8 + 4 + 1, e a mensagem passa pelos níveis 3, 2 e 0, pulando o 1.
 *
 * <p>Quem decide o caminho é a <em>chave de roteamento</em>, que leva o atraso
 * escrito em binário, um bit por palavra, seguido do destino final:
 * <pre>
 *   13 s, 22 níveis  →  0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.1.1.0.1.notificacao.enviar
 *                       └── nível 21 ─────────────────────────── nível 0 ┘
 * </pre>
 * <p>Cada nível tem uma exchange {@code topic} com duas ligações: bit 1 leva à
 * fila do nível (espera 2<sup>i</sup> s e segue, por dead-letter, para o nível
 * seguinte); bit 0 leva direto à exchange do nível seguinte, sem esperar. O
 * último nível entrega na {@link #EXCHANGE_ENTREGA}, que roteia pelo destino
 * escrito no fim da chave.
 * <pre>
 *   entrada ─▶ [nível 21] ─bit 0─────────────────────────▶ [nível 20] ─▶ … ─▶ [nível 0] ─▶ entrega ─▶ fila de trabalho
 *                  └─bit 1─▶ fila 2²¹ s ─(vence)─▶ [nível 20]
 * </pre>
 *
 * <p>É o mesmo desenho usado por frameworks de mensageria em produção sobre o
 * RabbitMQ. O custo é topológico — um par exchange/fila por nível —, e é pago
 * uma vez, na declaração.
 *
 * <p>Vinte e dois níveis cobrem até 2<sup>22</sup> − 1 segundos (48 dias), acima
 * do máximo de 30 dias que a API de avisos aceita. A resolução é de um segundo.
 */
public final class SalaDeEspera {

    /** Quantos níveis — e, portanto, quantos bits tem a chave. */
    public static final int NIVEIS = 22;

    /** O maior atraso representável. */
    public static final Duration ATRASO_MAXIMO = Duration.ofSeconds((1L << NIVEIS) - 1);

    /** Onde as mensagens saem da cascata, roteadas pelo destino final. */
    public static final String EXCHANGE_ENTREGA = "infnethub.espera.entrega";

    /** Onde o relay publica: o nível mais alto. */
    public static final String EXCHANGE_ENTRADA = exchangeDoNivel(NIVEIS - 1);

    private SalaDeEspera() {
    }

    public static String exchangeDoNivel(int nivel) {
        return "infnethub.espera.nivel.%02d".formatted(nivel);
    }

    public static String filaDoNivel(int nivel) {
        return "espera.nivel.%02d".formatted(nivel);
    }

    /** O tempo que a fila do nível segura cada mensagem: 2<sup>nivel</sup> segundos. */
    public static long ttlDoNivelMs(int nivel) {
        return (1L << nivel) * 1000L;
    }

    /** Para onde vai a mensagem que sai deste nível — o nível abaixo, ou a entrega. */
    public static String seguinteAo(int nivel) {
        return nivel == 0 ? EXCHANGE_ENTREGA : exchangeDoNivel(nivel - 1);
    }

    /** Ligação da exchange do nível à própria fila: o bit deste nível é 1. */
    public static String padraoParaEsperar(int nivel) {
        return prefixoAte(nivel) + "1.#";
    }

    /** Ligação da exchange do nível ao nível seguinte: o bit deste nível é 0. */
    public static String padraoParaSeguir(int nivel) {
        return prefixoAte(nivel) + "0.#";
    }

    /** Ligação da exchange de entrega à fila de trabalho do destino. */
    public static String padraoDeEntrega(String destino) {
        return "#." + destino;
    }

    /**
     * A chave que faz a mensagem esperar {@code atraso} e sair em {@code destino}.
     *
     * @param atraso arredondado para cima ao segundo: entregar um pouco depois é
     *               aceitável, entregar antes do pedido não é.
     */
    public static String rotaPara(Duration atraso, String destino) {
        long segundos = Math.ceilDiv(atraso.toMillis(), 1000L);
        if (segundos < 1 || segundos > ATRASO_MAXIMO.toSeconds()) {
            throw new IllegalArgumentException(
                    "atraso fora do intervalo da sala de espera (1 s a %d s): %s"
                            .formatted(ATRASO_MAXIMO.toSeconds(), atraso));
        }
        StringJoiner chave = new StringJoiner(".");
        for (int nivel = NIVEIS - 1; nivel >= 0; nivel--) {
            chave.add(((segundos >> nivel) & 1) == 1 ? "1" : "0");
        }
        return chave + "." + destino;
    }

    /**
     * Uma palavra coringa para cada nível acima deste: a posição do bit na
     * chave é contada do nível mais alto para o mais baixo.
     */
    private static String prefixoAte(int nivel) {
        return "*.".repeat(NIVEIS - 1 - nivel);
    }
}
