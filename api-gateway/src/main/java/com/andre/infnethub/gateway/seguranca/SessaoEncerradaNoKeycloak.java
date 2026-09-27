package com.andre.infnethub.gateway.seguranca;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Quando a sessão acaba no Keycloak, acaba aqui também.
 *
 * <p>A sessão do gateway guarda o token de acesso e o de atualização. O de
 * acesso dura minutos; vencido, o gateway usa o de atualização para pedir
 * outro. Se nesse meio-tempo a sessão do Keycloak deixou de existir — alguém
 * entrou com outra conta no mesmo navegador (o Grafana pede login a cada
 * acesso), a sessão expirou por inatividade, o provedor foi reiniciado —, o
 * Keycloak recusa com {@code invalid_grant}.
 *
 * <p>Sem este filtro, a recusa virava um 500, e a sessão do gateway
 * continuava de pé: {@code /bff/eu} a declarava válida, a tela de entrada
 * mandava para o feed, o feed recebia 500 e mandava de volta para a entrada,
 * sem fim. Aqui a sessão morta é descartada e a resposta é o mesmo 401 de quem
 * nunca entrou — o front-end já sabe o que fazer com ele.
 *
 * <p>Há um segundo sintoma da mesma causa. O Spring guarda os tokens por
 * usuário, e não por sessão: quando uma sessão do lucas falha e os tokens
 * dele são apagados, outra sessão do lucas — outra aba, outro navegador —
 * fica sem token nenhum. O Spring então lança
 * {@link ClientAuthorizationRequiredException}, e o filtro de segurança
 * responde com um redirecionamento para o Keycloak. Numa navegação isso
 * serviria; num {@code fetch}, o redirecionamento esbarra no CORS e a tela de
 * entrada só consegue dizer que "não foi possível falar com o servidor". O
 * tratamento é o mesmo: sessão descartada, 401.
 *
 * <p>Por isso a ordem 0: depois da cadeia do Spring Security (-100), para ver
 * a exceção antes do filtro que a transformaria em redirecionamento, e antes
 * do {@code DispatcherHandler}, onde rodam o {@code TokenRelay} das rotas e o
 * {@code /bff/eu} — os dois lugares onde o token é renovado.
 */
@Component
@Order(0)
class SessaoEncerradaNoKeycloak implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain cadeia) {
        return cadeia.filter(exchange)
                .onErrorResume(SessaoEncerradaNoKeycloak::sessaoRecusadaPeloProvedor,
                        erro -> exchange.getSession()
                                .flatMap(WebSession::invalidate)
                                .then(Mono.defer(() -> naoAutenticado(exchange))));
    }

    /**
     * {@code invalid_grant}: o token de atualização não vale mais — a sessão do
     * Keycloak acabou. Ou a sessão existe aqui mas os tokens já foram apagados.
     */
    static boolean sessaoRecusadaPeloProvedor(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof ClientAuthorizationRequiredException) {
                return true;
            }
            if (t instanceof OAuth2AuthorizationException recusa
                    && OAuth2ErrorCodes.INVALID_GRANT.equals(recusa.getError().getErrorCode())) {
                return true;
            }
        }
        return false;
    }

    private static Mono<Void> naoAutenticado(ServerWebExchange exchange) {
        var resposta = exchange.getResponse();
        if (resposta.isCommitted()) {
            return Mono.empty();
        }
        resposta.setStatusCode(HttpStatus.UNAUTHORIZED);
        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String corpo = """
                {"status":401,"error":"Sessão encerrada","message":"Sua sessão terminou. Entre novamente com sua conta Infnet."}""";
        DataBuffer buffer = resposta.bufferFactory().wrap(corpo.getBytes(StandardCharsets.UTF_8));
        return resposta.writeWith(Mono.just(buffer));
    }
}
