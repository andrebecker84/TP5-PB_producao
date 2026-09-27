package com.andre.infnethub.gateway.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O pedido de login sai da barra de endereço: vai ao Keycloak numa chamada
 * autenticada, e o navegador recebe só o {@code client_id} e a referência.
 */
class PedidoDeLoginEmpurradoTest {

    private static final String PAR = "http://keycloak:8080/realms/infnethub/protocol/openid-connect/ext/par/request";
    private static final String REFERENCIA = "urn:ietf:params:oauth:request_uri:abc123";

    private final AtomicReference<ClientRequest> enviado = new AtomicReference<>();

    private final WebClient keycloakFalso = WebClient.builder()
            .exchangeFunction(requisicao -> {
                enviado.set(requisicao);
                return Mono.just(ClientResponse.create(HttpStatus.CREATED)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"request_uri\":\"" + REFERENCIA + "\",\"expires_in\":60}")
                        .build());
            })
            .build();

    private final PedidoDeLoginEmpurrado resolvedor = new PedidoDeLoginEmpurrado(
            new InMemoryReactiveClientRegistrationRepository(registro()), keycloakFalso, PAR, "segredo");

    private OAuth2AuthorizationRequest resolver() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost:21080/oauth2/authorization/keycloak"));
        return resolvedor.resolve(exchange).block();
    }

    @Test
    void aBarraDeEnderecoLevaSoOClienteEAReferencia() {
        OAuth2AuthorizationRequest pedido = resolver();

        URI endereco = URI.create(pedido.getAuthorizationRequestUri());
        assertThat(endereco.getPath()).isEqualTo("/realms/infnethub/protocol/openid-connect/auth");
        assertThat(URLDecoder.decode(endereco.getRawQuery(), StandardCharsets.UTF_8))
                .isEqualTo("client_id=infnethub-bff&request_uri=" + REFERENCIA);
    }

    @Test
    void osParametrosVaoNoCorpoDaChamadaAutenticada() {
        OAuth2AuthorizationRequest pedido = resolver();

        assertThat(enviado.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(enviado.get().url()).hasToString(PAR);
        assertThat(enviado.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Basic ");
        var form = PedidoDeLoginEmpurrado.parametros(pedido).toSingleValueMap();
        assertThat(form).containsEntry("response_type", "code")
                .containsEntry("client_id", "infnethub-bff")
                .containsEntry("redirect_uri", "http://localhost:21080/login/oauth2/code/keycloak")
                .containsEntry("scope", "openid profile email")
                .containsEntry("state", pedido.getState())
                .containsEntry("code_challenge_method", "S256")
                .containsKeys("nonce", "code_challenge");
    }

    @Test
    void oPedidoGuardadoParaConferirORetornoContinuaCompleto() {
        OAuth2AuthorizationRequest pedido = resolver();

        assertThat(pedido.getState()).isNotBlank();
        assertThat(pedido.getRedirectUri()).isEqualTo("http://localhost:21080/login/oauth2/code/keycloak");
        assertThat(pedido.getAttributes()).containsKey("code_verifier");
    }

    private static ClientRegistration registro() {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("infnethub-bff")
                .clientSecret("segredo")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("http://localhost:21180/realms/infnethub/protocol/openid-connect/auth")
                .tokenUri("http://keycloak:8080/realms/infnethub/protocol/openid-connect/token")
                .jwkSetUri("http://keycloak:8080/realms/infnethub/protocol/openid-connect/certs")
                .build();
    }
}
