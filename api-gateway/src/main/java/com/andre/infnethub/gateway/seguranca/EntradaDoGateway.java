package com.andre.infnethub.gateway.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * {@code /login} no gateway leva à entrada da plataforma.
 *
 * <p>O gateway não tem tela: quem entra é o front-end, e a senha é digitada no
 * provedor de identidade. Mas {@code /login} é o endereço que o Spring usa por
 * padrão — e {@code /login?error} fica no histórico de quem já teve um login
 * interrompido. Sem esta rota, o endereço caía na regra de "sem sessão" e o
 * navegador mostrava o JSON de 401 das chamadas de API.
 */
@RestController
class EntradaDoGateway {

    private final String urlDoFrontEnd;

    EntradaDoGateway(@Value("${app.front-end.url}") String urlDoFrontEnd) {
        this.urlDoFrontEnd = urlDoFrontEnd;
    }

    @GetMapping("/login")
    Mono<Void> login(ServerWebExchange exchange) {
        boolean falhou = exchange.getRequest().getQueryParams().containsKey("error");
        var resposta = exchange.getResponse();
        resposta.setStatusCode(HttpStatus.FOUND);
        resposta.getHeaders().setLocation(URI.create(urlDoFrontEnd + (falhou ? "/login?erro=login" : "/login")));
        return resposta.setComplete();
    }
}
