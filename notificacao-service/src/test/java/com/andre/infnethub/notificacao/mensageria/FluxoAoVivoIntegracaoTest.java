package com.andre.infnethub.notificacao.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.feed.PostCurtidoV1;
import com.andre.infnethub.notificacao.service.CentralDeNotificacoes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * O caminho inteiro, com peças de verdade: a curtida entra no RabbitMQ, o
 * ouvinte grava a notificação, o aviso sai pelo fanout depois do commit, volta
 * pela fila exclusiva desta instância e chega ao navegador pela conexão SSE.
 *
 * <p>O "navegador" é um cliente HTTP lendo o fluxo linha a linha, numa porta
 * real — sem MockMvc, porque o que está em teste é justamente a conexão aberta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@ActiveProfiles("test")
@Import({InfraDeMensageria.class, FluxoAoVivoIntegracaoTest.TokenDeTeste.class})
@DisplayName("Fluxo ao vivo — do RabbitMQ até a conexão SSE")
class FluxoAoVivoIntegracaoTest {

    /**
     * Um decodificador de token para o teste, no lugar do Keycloak.
     *
     * <p>A requisição continua atravessando a cadeia de segurança inteira — é
     * isso que se quer provar, já que a conexão SSE depende de o token chegar
     * até o serviço. O que o teste evita é subir um provedor de identidade só
     * para assinar um token; o valor do "token" é o id do usuário.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TokenDeTeste {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .claim("usuario_id", Long.valueOf(token))
                    .claim("preferred_username", "usuario" + token)
                    .build();
        }
    }

    private static final Duration ESPERA = Duration.ofSeconds(15);

    @LocalServerPort private int porta;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private CentralDeNotificacoes central;

    @Test
    @DisplayName("uma curtida publicada no broker aparece na conexão ao vivo do autor do post")
    void curtidaChegaAoVivo() throws Exception {
        long autor = 30_001L;
        CountDownLatch conectado = new CountDownLatch(1);
        AtomicReference<String> notificacaoRecebida = new AtomicReference<>();

        // Fechado no fim: a conexão SSE não termina sozinha, e um cliente aberto
        // seguraria a JVM dos testes depois do último teste.
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpRequest pedido = HttpRequest.newBuilder(
                            URI.create("http://localhost:%d/api/v1/notificacoes/ao-vivo".formatted(porta)))
                    .header("Accept", "text/event-stream")
                // O "token" é o id do usuário — ver TokenDeTeste.
                .header("Authorization", "Bearer " + autor)
                .build();

            CompletableFuture<Void> leitura = cliente.sendAsync(pedido, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(resposta -> {
                        try (var linhas = new BufferedReader(new InputStreamReader(resposta.body(), StandardCharsets.UTF_8))) {
                            String evento = null;
                            String linha;
                            while ((linha = linhas.readLine()) != null) {
                                if (linha.startsWith("event:")) {
                                    evento = linha.substring("event:".length()).strip();
                                } else if (linha.startsWith("data:") && "conectado".equals(evento)) {
                                    conectado.countDown();
                                } else if (linha.startsWith("data:") && "notificacao".equals(evento)) {
                                    notificacaoRecebida.set(linha.substring("data:".length()));
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });

            assertThat(conectado.await(10, TimeUnit.SECONDS)).as("conexão SSE aberta").isTrue();

            rabbit.convertAndSend(Canais.EXCHANGE_EVENTOS, Canais.ROTA_POST_CURTIDO,
                    new PostCurtidoV1(UUID.randomUUID(), Instant.now(), 501L, "Aula ao vivo — Microsserviços",
                            autor, 30_002L, "Rafael Azevedo"));

            await().atMost(ESPERA).until(() -> notificacaoRecebida.get() != null);
            assertThat(notificacaoRecebida.get())
                    .contains("Rafael Azevedo curtiu sua publicação")
                    .contains("\"tipo\":\"CURTIDA\"");
            // E ficou gravada: o aviso ao vivo é só a antecipação do que a leitura mostra.
            assertThat(central.naoLidas(autor)).isEqualTo(1);

            leitura.cancel(true);
            cliente.shutdownNow();
        }
    }
}
