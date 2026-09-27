package com.andre.infnethub.gateway.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Sair de verdade: encerra a sessão do gateway <em>e</em> a do Keycloak.
 *
 * <p>Sem o segundo passo, "sair" apagaria só o cookie daqui. Como a sessão do
 * Keycloak continuaria aberta, o próximo clique em "Entrar" traria a pessoa de
 * volta sem pedir senha — o que parece defeito, e num computador compartilhado
 * é falha de segurança.
 *
 * <p>É escrito à mão, e não com o {@code OidcClientInitiatedServerLogoutSuccessHandler}
 * do Spring, por uma razão concreta: aquele handler lê o endereço de saída dos
 * metadados de descoberta do provedor, e aqui a descoberta automática não é
 * usada — o Keycloak tem um endereço para o navegador (localhost) e outro para
 * os contêineres, e só o público serve para este redirecionamento.
 */
@Component
class SaidaDoKeycloak implements ServerLogoutSuccessHandler {

    private final String urlDeSaida;
    private final String urlDoFrontEnd;

    SaidaDoKeycloak(@Value("${app.keycloak.url-publica}") String keycloakPublico,
                    @Value("${app.keycloak.realm}") String realm,
                    @Value("${app.front-end.url}") String urlDoFrontEnd) {
        this.urlDeSaida = "%s/realms/%s/protocol/openid-connect/logout".formatted(keycloakPublico, realm);
        this.urlDoFrontEnd = urlDoFrontEnd;
    }

    @Override
    public Mono<Void> onLogoutSuccess(WebFilterExchange troca, Authentication autenticacao) {
        UriComponentsBuilder destino = UriComponentsBuilder.fromUriString(urlDeSaida)
                .queryParam("post_logout_redirect_uri", urlDoFrontEnd);

        // O id_token_hint diz ao Keycloak QUEM está saindo. Sem ele, o Keycloak
        // exibe uma tela de confirmação em vez de encerrar direto.
        if (autenticacao != null && autenticacao.getPrincipal() instanceof OidcUser usuario) {
            destino.queryParam("id_token_hint", usuario.getIdToken().getTokenValue());
        }

        var resposta = troca.getExchange().getResponse();
        resposta.setStatusCode(HttpStatus.FOUND);
        resposta.getHeaders().setLocation(URI.create(destino.toUriString()));
        return resposta.setComplete();
    }
}
