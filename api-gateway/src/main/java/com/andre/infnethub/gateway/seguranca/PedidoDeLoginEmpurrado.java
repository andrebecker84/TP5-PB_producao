package com.andre.infnethub.gateway.seguranca;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * O pedido de login vai ao Keycloak por trás, e não pela barra de endereço
 * (<em>Pushed Authorization Requests</em>, RFC 9126).
 *
 * <p>No fluxo comum, todos os parâmetros do pedido — para onde voltar, o
 * {@code state}, o {@code nonce}, o desafio do PKCE — seguem na URL que o
 * navegador abre. Nenhum deles é segredo, mas ficam à vista, no histórico e
 * nos registros de acesso, e quem quiser pode alterá-los antes de o Keycloak
 * os ler. Aqui o gateway entrega o pedido completo ao Keycloak numa chamada
 * autenticada com o segredo do cliente, e recebe de volta uma referência de
 * uso único, que vence em segundos. A barra de endereço mostra só o
 * {@code client_id} e essa referência.
 *
 * <p>O Keycloak está configurado para exigir esse caminho no cliente
 * {@code infnethub-bff}: um pedido montado à mão na barra é recusado.
 *
 * <p>O pedido em si continua sendo o do Spring — com {@code state},
 * {@code nonce} e PKCE — e é ele que fica guardado na sessão para conferir o
 * retorno; só muda o endereço para onde o navegador é mandado.
 */
class PedidoDeLoginEmpurrado implements ServerOAuth2AuthorizationRequestResolver {

    private final ServerOAuth2AuthorizationRequestResolver padrao;
    private final WebClient keycloak;
    private final String enderecoDoPar;
    private final String clientSecret;

    PedidoDeLoginEmpurrado(ReactiveClientRegistrationRepository registros, WebClient keycloak,
                           String enderecoDoPar, String clientSecret) {
        this.padrao = new DefaultServerOAuth2AuthorizationRequestResolver(registros);
        this.keycloak = keycloak;
        this.enderecoDoPar = enderecoDoPar;
        this.clientSecret = clientSecret;
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange) {
        return padrao.resolve(exchange).flatMap(this::empurrar);
    }

    @Override
    public Mono<OAuth2AuthorizationRequest> resolve(ServerWebExchange exchange, String registrationId) {
        return padrao.resolve(exchange, registrationId).flatMap(this::empurrar);
    }

    private Mono<OAuth2AuthorizationRequest> empurrar(OAuth2AuthorizationRequest pedido) {
        return keycloak.post()
                .uri(enderecoDoPar)
                .headers(h -> h.setBasicAuth(pedido.getClientId(), clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(parametros(pedido)))
                .retrieve()
                .bodyToMono(Map.class)
                .map(resposta -> {
                    String referencia = String.valueOf(resposta.get("request_uri"));
                    String endereco = UriComponentsBuilder.fromUriString(pedido.getAuthorizationUri())
                            .queryParam("client_id", pedido.getClientId())
                            .queryParam("request_uri", referencia)
                            .encode()
                            .toUriString();
                    return OAuth2AuthorizationRequest.from(pedido).authorizationRequestUri(endereco).build();
                });
    }

    /** Os mesmos parâmetros que iriam na URL, agora no corpo da chamada. */
    static MultiValueMap<String, String> parametros(OAuth2AuthorizationRequest pedido) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("response_type", pedido.getResponseType().getValue());
        form.add("client_id", pedido.getClientId());
        form.add("redirect_uri", pedido.getRedirectUri());
        form.add("scope", String.join(" ", pedido.getScopes()));
        form.add("state", pedido.getState());
        pedido.getAdditionalParameters().forEach((nome, valor) -> form.add(nome, String.valueOf(valor)));
        return form;
    }
}
