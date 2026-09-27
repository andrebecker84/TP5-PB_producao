package com.andre.infnethub.gateway.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O token anti-CSRF que o navegador devolve é o valor cru do cookie — no
 * cabeçalho, nas chamadas de API, e no campo do formulário, na saída. O
 * gateway precisa aceitá-lo assim. Com o tratador padrão do Spring Security 6,
 * que espera o token mascarado, toda escrita do navegador recebia 403, e
 * "sair" não saía.
 */
class TokenCsrfDoNavegadorTest {

    private static final CsrfToken TOKEN = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "valor-do-cookie");

    @Test
    void aceitaOValorDoCookieNoCabecalho() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/posts/1/comentarios")
                .header("X-XSRF-TOKEN", "valor-do-cookie"));

        String resolvido = SegurancaConfig.tokenSemMascara().resolveCsrfTokenValue(exchange, TOKEN).block();

        assertThat(resolvido).isEqualTo(TOKEN.getToken());
    }

    @Test
    void aceitaOValorDoCookieNoFormularioDeSaida() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/logout")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("_csrf=valor-do-cookie"));

        String resolvido = SegurancaConfig.tokenSemMascara().resolveCsrfTokenValue(exchange, TOKEN).block();

        assertThat(resolvido).isEqualTo(TOKEN.getToken());
    }

    @Test
    void oPadraoMascaradoRecusariaOMesmoValor() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/logout")
                .header("X-XSRF-TOKEN", "valor-do-cookie"));

        String resolvido = new XorServerCsrfTokenRequestAttributeHandler().resolveCsrfTokenValue(exchange, TOKEN).block();

        // o que o front-end manda não é o que o tratador padrão espera: a causa do 403
        assertThat(resolvido).isNotEqualTo(TOKEN.getToken());
    }
}
