package com.andre.infnethub.contratos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A cascata de níveis, percorrida como o broker a percorreria.
 *
 * <p>O teste não confere as strings da topologia uma a uma: ele implementa a
 * regra de casamento de uma exchange {@code topic} ({@code *} = uma palavra,
 * {@code #} = zero ou mais) e faz a chave atravessar os níveis, somando o tempo
 * de cada fila por onde passa. Se o tempo total for o atraso pedido para
 * qualquer atraso, as ligações estão certas — inclusive as que nenhum exemplo
 * escrito à mão lembraria de cobrir.
 *
 * <p>O caminho pelo RabbitMQ de verdade, com o relógio de verdade, é o
 * {@code ComandoDeAvisoIntegracaoTest} do serviço de notificação.
 */
@DisplayName("Sala de espera — cascata de níveis binários")
class SalaDeEsperaTest {

    private static final String DESTINO = Canais.ROTA_ENVIAR_AVISO;

    @Test
    @DisplayName("a chave escreve o atraso em binário, do nível mais alto ao mais baixo, e termina no destino")
    void chaveEmBinario() {
        String chave = SalaDeEspera.rotaPara(Duration.ofSeconds(13), DESTINO);

        assertThat(chave).isEqualTo("0.".repeat(SalaDeEspera.NIVEIS - 4) + "1.1.0.1." + DESTINO);
        assertThat(chave.split("\\.")).hasSize(SalaDeEspera.NIVEIS + 2);
    }

    @ParameterizedTest(name = "{0} s")
    @ValueSource(longs = {1, 2, 3, 5, 13, 59, 60, 3_599, 3_600, 86_400, 2_592_000, 4_194_303})
    @DisplayName("percorrida nível a nível, a mensagem espera exatamente o atraso pedido e sai no destino")
    void percursoSomaOAtraso(long segundos) {
        Percurso p = percorrer(SalaDeEspera.rotaPara(Duration.ofSeconds(segundos), DESTINO));

        assertThat(p.esperaMs).isEqualTo(segundos * 1000);
        assertThat(p.saiuEm).isEqualTo(SalaDeEspera.EXCHANGE_ENTREGA);
        assertThat(casa(SalaDeEspera.padraoDeEntrega(DESTINO), p.chave)).as("a entrega reconhece o destino").isTrue();
        assertThat(p.filas).as("uma fila por bit 1").hasSize(Long.bitCount(segundos));
    }

    @Test
    @DisplayName("um aviso curto publicado depois de um longo chega no prazo dele — sem bloqueio de cabeça de fila")
    void semBloqueioDeCabecaDeFila() {
        // A regra de uma fila: ninguém sai antes de quem está na frente.
        // No desenho antigo — uma fila, prazo em cada mensagem —, essa regra
        // faz o aviso de 5 s esperar o de 1 hora.
        long[] saidaAntiga = simularFilaUnica(new long[]{0, 100}, new long[]{3_600_000, 5_000});
        assertThat(saidaAntiga[1]).as("desenho antigo: o curto espera o longo").isEqualTo(3_600_000);

        // Na cascata, cada fila tem um prazo só, e a mesma regra nunca atrasa
        // ninguém: quem entrou antes também vence antes.
        long[] chegada = simularCascata(new long[]{0, 100}, new Duration[]{Duration.ofHours(1), Duration.ofSeconds(5)});
        assertThat(chegada[0]).as("o longo").isEqualTo(3_600_000);
        assertThat(chegada[1]).as("o curto, publicado 100 ms depois").isEqualTo(5_100);
    }

    /** Uma fila só, prazo por mensagem, saída em ordem de chegada. */
    private static long[] simularFilaUnica(long[] entrada, long[] prazo) {
        long[] saida = new long[entrada.length];
        long anterior = 0;
        for (int m = 0; m < entrada.length; m++) {
            saida[m] = Math.max(entrada[m] + prazo[m], anterior);
            anterior = saida[m];
        }
        return saida;
    }

    /**
     * A cascata inteira, com a mesma regra de fila em cada nível. As mensagens
     * são processadas nível a nível, do mais alto ao mais baixo — o caminho de
     * todas é descendente —, e cada fila guarda o horário da última saída.
     */
    private static long[] simularCascata(long[] entrada, Duration[] atraso) {
        long[] relogio = entrada.clone();
        String[] chave = new String[entrada.length];
        for (int m = 0; m < entrada.length; m++) {
            chave[m] = SalaDeEspera.rotaPara(atraso[m], DESTINO);
        }
        for (int nivel = SalaDeEspera.NIVEIS - 1; nivel >= 0; nivel--) {
            // Quem entra na fila deste nível, em ordem de chegada.
            List<Integer> naFila = new ArrayList<>();
            for (int m = 0; m < entrada.length; m++) {
                if (casa(SalaDeEspera.padraoParaEsperar(nivel), chave[m])) {
                    naFila.add(m);
                }
            }
            naFila.sort((x, y) -> Long.compare(relogio[x], relogio[y]));
            long anterior = 0;
            for (int m : naFila) {
                relogio[m] = Math.max(relogio[m] + SalaDeEspera.ttlDoNivelMs(nivel), anterior);
                anterior = relogio[m];
            }
        }
        return relogio;
    }

    @Test
    @DisplayName("frações de segundo são arredondadas para cima — entregar antes do pedido não é aceitável")
    void arredondaParaCima() {
        assertThat(percorrer(SalaDeEspera.rotaPara(Duration.ofMillis(1_001), DESTINO)).esperaMs).isEqualTo(2_000);
        assertThat(percorrer(SalaDeEspera.rotaPara(Duration.ofMillis(1), DESTINO)).esperaMs).isEqualTo(1_000);
    }

    @Test
    @DisplayName("atraso zero ou acima do máximo não entra na sala de espera")
    void foraDoIntervalo() {
        assertThatThrownBy(() -> SalaDeEspera.rotaPara(Duration.ZERO, DESTINO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SalaDeEspera.rotaPara(SalaDeEspera.ATRASO_MAXIMO.plusSeconds(1), DESTINO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("o máximo cobre o maior atraso que a API de avisos aceita (30 dias)")
    void cobreOLimiteDaApi() {
        assertThat(SalaDeEspera.ATRASO_MAXIMO).isGreaterThanOrEqualTo(Duration.ofDays(30));
    }

    // ── Um broker em miniatura ─────────────────────────────────────────────

    private record Percurso(String chave, long esperaMs, List<String> filas, String saiuEm) {
    }

    /**
     * Entra pela exchange de entrada e segue as ligações declaradas, como o
     * RabbitMQ seguiria. Em cada exchange de nível, exatamente uma das duas
     * ligações pode casar — se nenhuma ou as duas casassem, a topologia estaria
     * errada e o teste falha.
     */
    private static Percurso percorrer(String chave) {
        String exchange = SalaDeEspera.EXCHANGE_ENTRADA;
        long espera = 0;
        List<String> filas = new ArrayList<>();

        for (int nivel = SalaDeEspera.NIVEIS - 1; nivel >= 0; nivel--) {
            assertThat(exchange).isEqualTo(SalaDeEspera.exchangeDoNivel(nivel));
            boolean espera1 = casa(SalaDeEspera.padraoParaEsperar(nivel), chave);
            boolean segue0 = casa(SalaDeEspera.padraoParaSeguir(nivel), chave);
            assertThat(espera1 ^ segue0).as("exatamente uma ligação casa no nível " + nivel).isTrue();

            if (espera1) {
                filas.add(SalaDeEspera.filaDoNivel(nivel));
                espera += SalaDeEspera.ttlDoNivelMs(nivel);
            }
            // Pela fila (dead-letter ao vencer) ou pela ligação direta, o
            // próximo passo é o mesmo.
            exchange = SalaDeEspera.seguinteAo(nivel);
        }
        return new Percurso(chave, espera, filas, exchange);
    }

    /** Regra de casamento de uma exchange topic. */
    private static boolean casa(String padrao, String chave) {
        return casa(padrao.split("\\."), 0, chave.split("\\."), 0);
    }

    private static boolean casa(String[] p, int i, String[] c, int j) {
        if (i == p.length) {
            return j == c.length;
        }
        if (p[i].equals("#")) {
            for (int k = j; k <= c.length; k++) {
                if (casa(p, i + 1, c, k)) {
                    return true;
                }
            }
            return false;
        }
        return j < c.length && (p[i].equals("*") || p[i].equals(c[j])) && casa(p, i + 1, c, j + 1);
    }
}
