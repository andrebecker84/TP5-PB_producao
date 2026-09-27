package com.andre.infnethub.gateway.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A sessão que o Keycloak não reconhece mais vira 401 e é descartada — e não
 * um 500 que deixa a sessão de pé e prende o navegador entre o feed e a
 * entrada.
 */
class SessaoEncerradaNoKeycloakTest {

    private final SessaoEncerradaNoKeycloak filtro = new SessaoEncerradaNoKeycloak();

    private static ClientAuthorizationException recusa(String codigo) {
        var erro = new OAuth2Error(codigo, "Session not active", null);
        return new ClientAuthorizationException(erro, "keycloak", new OAuth2AuthorizationException(erro));
    }

    @Test
    void invalidGrantDescartaASessaoEResponde401() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/usuarios/1"));
        WebSession sessao = exchange.getSession().block();
        sessao.getAttributes().put("SPRING_SECURITY_CONTEXT", "qualquer");
        sessao.start();

        filtro.filter(exchange, e -> Mono.error(recusa(OAuth2ErrorCodes.INVALID_GRANT))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Sessão encerrada");
        assertThat(sessao.getAttributes()).isEmpty();
    }

    @Test
    void sessaoSemTokensTambemVira401EmVezDeRedirecionamento() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/usuarios/1"));

        filtro.filter(exchange, e -> Mono.error(new ClientAuthorizationRequiredException("keycloak"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getLocation()).isNull();
    }

    @Test
    void outrosErrosSeguemAdiante() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/usuarios/1"));

        assertThatThrownBy(() -> filtro.filter(exchange, e -> Mono.error(recusa(OAuth2ErrorCodes.SERVER_ERROR))).block())
                .isInstanceOf(ClientAuthorizationException.class);
        assertThatThrownBy(() -> filtro.filter(exchange, e -> Mono.error(new IllegalStateException("outro"))).block())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconheceARecusaMesmoEmbrulhada() {
        assertThat(SessaoEncerradaNoKeycloak.sessaoRecusadaPeloProvedor(
                new RuntimeException(recusa(OAuth2ErrorCodes.INVALID_GRANT)))).isTrue();
        assertThat(SessaoEncerradaNoKeycloak.sessaoRecusadaPeloProvedor(new RuntimeException("x"))).isFalse();
    }

    @Test
    void semErroNadaMuda() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/bff/eu"));

        filtro.filter(exchange, e -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
