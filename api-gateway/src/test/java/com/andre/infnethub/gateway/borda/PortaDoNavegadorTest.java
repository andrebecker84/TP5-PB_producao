package com.andre.infnethub.gateway.borda;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A porta do navegador, aberta de verdade numa porta livre.
 *
 * <p>No lugar do gateway, um tratamento que responde 200 a tudo: o que o teste
 * prova é o que a porta deixa passar até ele, e não o que o gateway faz depois.
 */
@DisplayName("Porta do navegador — o que o navegador alcança")
class PortaDoNavegadorTest {

    private PortaDoNavegador porta;
    private int numero;

    @BeforeEach
    void abrir() throws IOException {
        try (ServerSocket livre = new ServerSocket(0)) {
            numero = livre.getLocalPort();
        }
        HttpHandler gateway = (requisicao, resposta) -> {
            resposta.setStatusCode(HttpStatus.OK);
            return resposta.setComplete();
        };
        porta = new PortaDoNavegador(gateway, numero);
        porta.start();
    }

    @AfterEach
    void fechar() {
        porta.stop();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/api/v1/posts,                 200",
            "/oauth2/authorization/keycloak, 200",
            "/actuator/health,              200",
            "/actuator/health/readiness,    200",
            "/actuator/info,                200",
            "/actuator/prometheus,          404",
            "/actuator/gateway/routes,      404",
            "/actuator,                     404",
            "/actuator/env,                 404"
    })
    @DisplayName("a aplicação e a saúde passam; o resto do Actuator não existe por aqui")
    void filtra(String caminho, int esperado) throws Exception {
        HttpResponse<Void> resposta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + numero + caminho)).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(resposta.statusCode()).isEqualTo(esperado);
    }
}
