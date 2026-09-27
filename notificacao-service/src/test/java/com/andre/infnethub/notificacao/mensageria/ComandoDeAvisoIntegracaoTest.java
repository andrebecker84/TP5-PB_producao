package com.andre.infnethub.notificacao.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.SalaDeEspera;
import com.andre.infnethub.contratos.comando.EnviarAvisoV1;
import com.andre.infnethub.notificacao.model.TipoNotificacao;
import com.andre.infnethub.notificacao.service.CentralDeNotificacoes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * O padrão comando, com o broker de verdade — e o padrão da mensagem com
 * atraso, que é o mesmo comando por um caminho mais longo.
 *
 * <p>O que estes testes de fato provam é a topologia, não o código Java: que a
 * exchange {@code direct} entrega o pedido a uma fila só, e que uma mensagem
 * vencida numa fila sem consumidor <em>reaparece</em> na fila de trabalho em
 * vez de sumir. Nenhuma das duas coisas está escrita em método nenhum; estão
 * nos argumentos das filas, e um erro ali não falharia a compilação nem a
 * subida do serviço — a mensagem simplesmente não chegaria, em silêncio.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@ActiveProfiles("test")
@Import(InfraDeMensageria.class)
@DisplayName("Comando de aviso — direct, agendamento e repetição")
class ComandoDeAvisoIntegracaoTest {

    private static final Duration ESPERA = Duration.ofSeconds(20);

    @Autowired private RabbitTemplate rabbit;
    @Autowired private CentralDeNotificacoes central;

    @Test
    @DisplayName("o comando chega à fila de trabalho e vira notificação para cada endereçado")
    void comandoImediato() {
        long ana = 40_001L;
        long bruno = 40_002L;

        enviar(new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), List.of(ana, bruno),
                "Aula de Arquitetura adiada para quinta", "/agenda", 3L), Canais.ROTA_ENVIAR_AVISO);

        await().atMost(ESPERA).until(() -> central.naoLidas(ana) == 1 && central.naoLidas(bruno) == 1);

        assertThat(central.recentes(ana, 10)).singleElement().satisfies(n -> {
            assertThat(n.tipo()).isEqualTo(TipoNotificacao.AVISO.name());
            assertThat(n.texto()).isEqualTo("Aula de Arquitetura adiada para quinta");
            assertThat(n.link()).isEqualTo("/agenda");
        });
    }

    @Test
    @DisplayName("o comando agendado atravessa a sala de espera e só então é executado")
    void comandoAgendado() {
        long carla = 40_003L;

        // 3 s = níveis 1 e 0: a mensagem passa por duas filas e pula as outras.
        agendar(new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), List.of(carla),
                "Prazo do TP4 termina amanhã", null, 3L), Duration.ofSeconds(3));

        // A afirmação que dá sentido ao teste: logo depois de publicar, nada
        // aconteceu. Sem ela, o teste passaria igual com a sala de espera
        // ligada direto na fila de trabalho — isto é, sem atraso nenhum.
        assertThat(central.naoLidas(carla)).as("não pode ter sido entregue antes do prazo").isZero();

        await().atMost(ESPERA).until(() -> central.naoLidas(carla) == 1);
        assertThat(central.recentes(carla, 10)).singleElement()
                .satisfies(n -> assertThat(n.texto()).isEqualTo("Prazo do TP4 termina amanhã"));
    }

    @Test
    @DisplayName("um aviso curto agendado depois de um longo chega no prazo dele — sem bloqueio de cabeça de fila")
    void semBloqueioDeCabecaDeFila() {
        long elisa = 40_005L;

        // No desenho anterior — uma fila, prazo em cada mensagem —, o de 2 s
        // esperaria o de 1 hora sair da frente. Foi o que o cenário 7 do
        // roteiro de demonstração mostrou, e o que esta cascata resolve.
        agendar(new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), List.of(elisa),
                "Aviso de daqui a uma hora", null, 3L), Duration.ofHours(1));
        agendar(new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), List.of(elisa),
                "Aviso de daqui a dois segundos", null, 3L), Duration.ofSeconds(2));

        long inicio = System.nanoTime();
        await().atMost(ESPERA).until(() -> central.naoLidas(elisa) == 1);
        long decorridoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(central.recentes(elisa, 10)).singleElement()
                .satisfies(n -> assertThat(n.texto()).isEqualTo("Aviso de daqui a dois segundos"));
        assertThat(decorridoMs).as("chegou no prazo dele, e não no do aviso da frente").isLessThan(10_000);
    }

    @Test
    @DisplayName("o mesmo comando entregue duas vezes avisa uma vez só")
    void comandoRepetido() {
        long diego = 40_004L;
        EnviarAvisoV1 comando = new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), List.of(diego),
                "Matrícula confirmada", null, 3L);

        enviar(comando, Canais.ROTA_ENVIAR_AVISO);
        await().atMost(ESPERA).until(() -> central.naoLidas(diego) == 1);

        enviar(comando, Canais.ROTA_ENVIAR_AVISO);

        // Esperar para provar uma ausência é sempre frágil; o que torna este
        // trecho honesto é o teste anterior, que mostra quanto tempo o caminho
        // leva quando funciona. Três segundos é folga sobre isso.
        await().pollDelay(Duration.ofSeconds(3)).atMost(ESPERA).until(() -> true);
        assertThat(central.naoLidas(diego)).isEqualTo(1);
    }

    /** Publica como o relay publica um comando imediato. */
    private void enviar(EnviarAvisoV1 comando, String rota) {
        rabbit.convertAndSend(Canais.EXCHANGE_COMANDOS, rota, comando, mensagem -> {
            mensagem.getMessageProperties().setMessageId(comando.mensagemId().toString());
            return mensagem;
        });
    }

    /**
     * Publica como o relay publica um comando com atraso: na entrada da sala
     * de espera, com o atraso e o destino escritos na chave.
     */
    private void agendar(EnviarAvisoV1 comando, Duration atraso) {
        rabbit.convertAndSend(SalaDeEspera.EXCHANGE_ENTRADA,
                SalaDeEspera.rotaPara(atraso, Canais.ROTA_ENVIAR_AVISO), comando, mensagem -> {
                    mensagem.getMessageProperties().setMessageId(comando.mensagemId().toString());
                    return mensagem;
                });
    }
}
