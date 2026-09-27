package com.andre.infnethub.gateway.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * A porta de entrada, e o único lugar do sistema que fala com o Keycloak em
 * nome do navegador.
 *
 * <h2>Por que o BFF, e não o token no navegador</h2>
 * <p>Até o TP3 a identidade era um cabeçalho {@code X-Usuario-Id} informado
 * pelo cliente, sem verificação: qualquer um podia dizer que era qualquer um.
 * A correção óbvia seria devolver um JWT ao navegador — e essa é justamente a
 * prática que as recomendações atuais para aplicações de página única
 * desaconselham, porque um token guardado em {@code localStorage} é legível
 * por qualquer script da página.
 *
 * <p>Aqui o gateway faz o papel de <em>Backend for Frontend</em>: ele conduz o
 * login, guarda os tokens na sessão do servidor e devolve ao navegador só um
 * cookie {@code HttpOnly}, que o JavaScript não lê. O token de acesso segue
 * para os serviços de trás pelo filtro {@code TokenRelay}, dentro da rede.
 *
 * <p>Quem não é navegador — um script, a coleção {@code http/} — continua
 * podendo mandar o token no cabeçalho {@code Authorization}, e o gateway
 * também aceita esse caminho. É a mesma porta de entrada para os dois.
 *
 * <h2>Proteção contra CSRF</h2>
 * <p>Sessão por cookie traz de volta o risco que um token em cabeçalho não
 * tem: o navegador envia o cookie sozinho, inclusive numa requisição disparada
 * por outro site. Daí o token anti-CSRF em cookie legível, que o front-end
 * devolve no cabeçalho {@code X-XSRF-TOKEN} — o outro site não consegue ler o
 * cookie e, portanto, não consegue forjar o cabeçalho.
 */
@Configuration
@EnableWebFluxSecurity
public class SegurancaConfig {

    /** Nome do cookie de sessão do WebFlux — a credencial que o navegador envia sozinho. */
    private static final String COOKIE_DE_SESSAO = "SESSION";

    private final String urlDoFrontEnd;

    public SegurancaConfig(@Value("${app.front-end.url}") String urlDoFrontEnd) {
        this.urlDoFrontEnd = urlDoFrontEnd;
    }

    @Bean
    SecurityWebFilterChain cadeiaDeSeguranca(ServerHttpSecurity http, ServerLogoutSuccessHandler saidaDoKeycloak,
                                             ServerOAuth2AuthorizationRequestResolver pedidoDeLogin) {
        return http
                .authorizeExchange(troca -> troca
                        // O preflight do CORS não carrega credenciais; exigir
                        // autenticação nele faria o navegador recusar a
                        // requisição verdadeira antes mesmo de enviá-la.
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // só redireciona para a entrada do front-end (EntradaDoGateway)
                        .pathMatchers(HttpMethod.GET, "/login").permitAll()
                        // As métricas, para o Prometheus. Só pela porta
                        // principal, na rede interna: a porta do navegador
                        // responde 404 a tudo do Actuator além da saúde.
                        .pathMatchers("/actuator/prometheus").permitAll()
                        .anyExchange().authenticated())
                .oauth2Login(login -> login
                        // o pedido de login vai ao Keycloak por trás (PAR), não pela barra de endereço
                        .authorizationRequestResolver(pedidoDeLogin)
                        // Sem "requisição salva": depois do login o destino é
                        // sempre o front-end. A requisição que provocou o 401
                        // veio de uma chamada de API e será refeita por ele.
                        .authenticationSuccessHandler(aposLogin())
                        .authenticationFailureHandler(loginQueFalhou()))
                // Duas formas de provar quem é, para dois tipos de cliente:
                // o navegador manda o cookie de sessão; um script, uma coleção
                // HTTP ou um teste de fumaça mandam o token no cabeçalho
                // Authorization. Sem isto, tudo o que não é navegador teria de
                // falar direto com os serviços, saindo do caminho que o
                // sistema realmente usa — e as verificações provariam pouco.
                .oauth2ResourceServer(oauth -> oauth.jwt(org.springframework.security.config.Customizer.withDefaults()))
                .logout(saida -> saida.logoutSuccessHandler(saidaDoKeycloak))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(tokenSemMascara())
                        .requireCsrfProtectionMatcher(apenasQuemUsaCookie()))
                .exceptionHandling(erros -> erros.authenticationEntryPoint(semSessao()))
                .cors(Customizer.withDefaults())
                .build();
    }

    /**
     * Exige o token anti-CSRF só de quem chega com o cookie de sessão.
     *
     * <p>O ataque de CSRF depende de o navegador anexar a credencial sozinho, e
     * o que ele anexa sozinho é o cookie. Um cabeçalho {@code Authorization}
     * só existe se alguém o escrever, e uma requisição sem credencial nenhuma
     * não tem o que ser explorado — vai ser recusada pela autenticação de
     * qualquer forma.
     *
     * <p>A diferença aparece na resposta: sem este recorte, uma escrita sem
     * credencial era barrada pela verificação de CSRF, que roda antes da
     * autenticação, e o cliente recebia um 403 em texto puro no lugar do 401
     * em JSON que explica o que fazer. Foi o que a coleção {@code http/}
     * apanhou.
     *
     * <p>Leituras ficam de fora porque não mudam estado — é o mesmo critério do
     * padrão do Spring.
     */
    private ServerWebExchangeMatcher apenasQuemUsaCookie() {
        Set<HttpMethod> semEfeito = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);
        return exchange -> {
            var requisicao = exchange.getRequest();
            boolean leitura = semEfeito.contains(requisicao.getMethod());
            boolean temCookieDeSessao = requisicao.getCookies().containsKey(COOKIE_DE_SESSAO);
            return !leitura && temCookieDeSessao
                    ? ServerWebExchangeMatcher.MatchResult.match()
                    : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    /**
     * Aceita o token anti-CSRF como ele está no cookie.
     *
     * <p>Desde o Spring Security 6, o padrão espera o token <em>mascarado</em> —
     * um valor diferente a cada resposta, contra o ataque BREACH, que extrai
     * segredos do corpo de respostas comprimidas. O front-end, porém, lê o
     * cookie e devolve o valor cru, no cabeçalho {@code X-XSRF-TOKEN} ou no
     * campo {@code _csrf} do formulário de saída; com o padrão, nunca batia, e
     * toda escrita do navegador — curtir, comentar, publicar, sair — recebia
     * 403. A coleção HTTP não via, porque usa o cabeçalho Authorization, que
     * dispensa a verificação.
     *
     * <p>Sem a máscara é o arranjo que a documentação do Spring indica para
     * aplicações de página única: aqui o token vive num cookie e volta num
     * cabeçalho, nunca no corpo de uma resposta, e o BREACH não tem o que ler.
     */
    static ServerCsrfTokenRequestAttributeHandler tokenSemMascara() {
        return new ServerCsrfTokenRequestAttributeHandler();
    }

    /**
     * Quando o retorno do provedor não fecha — o pedido de login expirou, a
     * pessoa voltou no navegador, abriu duas abas de login —, o padrão do Spring
     * é mandar para {@code /login?error} aqui no gateway. Esse endereço não é
     * uma página: cai na regra de "sem sessão" e responde o JSON de 401 das
     * chamadas de API. A tela de entrada do front-end explica o que houve e
     * oferece entrar de novo.
     */
    private RedirectServerAuthenticationFailureHandler loginQueFalhou() {
        return new RedirectServerAuthenticationFailureHandler(urlDoFrontEnd + "/login?erro=login");
    }

    /**
     * Depois do login, a página de transição do front-end: é ali, e não no
     * provedor, que aparece o "acesso liberado" — só a esta altura a senha já
     * foi conferida de fato. De lá a pessoa segue para o feed.
     */
    private RedirectServerAuthenticationSuccessHandler aposLogin() {
        RedirectServerAuthenticationSuccessHandler destino =
                new RedirectServerAuthenticationSuccessHandler(urlDoFrontEnd + "/entrando");
        destino.setRequestCache(NoOpServerRequestCache.getInstance());
        return destino;
    }

    /**
     * Sem sessão, responde 401 em JSON — e não um redirecionamento para a tela
     * de login do Keycloak.
     *
     * <p>O redirecionamento é o padrão, e é o certo para uma navegação. Numa
     * chamada de API feita por {@code fetch}, porém, ele seria seguido em
     * silêncio, e o front-end receberia o HTML da tela de login com status 200,
     * sem nunca saber que a sessão tinha acabado. Com 401, ele sabe, e leva a
     * pessoa para a tela de entrada.
     */
    private ServerAuthenticationEntryPoint semSessao() {
        return (exchange, excecao) -> {
            var resposta = exchange.getResponse();
            resposta.setStatusCode(HttpStatus.UNAUTHORIZED);
            resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String corpo = """
                    {"status":401,"error":"Não autenticado","message":"Entre com sua conta Infnet para continuar."}""";
            DataBuffer buffer = resposta.bufferFactory().wrap(corpo.getBytes(StandardCharsets.UTF_8));
            return resposta.writeWith(Mono.just(buffer));
        };
    }

    @Bean
    ServerOAuth2AuthorizationRequestResolver pedidoDeLogin(
            ReactiveClientRegistrationRepository registros,
            @Value("${app.keycloak.url-interna}") String keycloakInterno,
            @Value("${app.keycloak.realm}") String realm,
            @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}") String segredo) {
        String enderecoDoPar = "%s/realms/%s/protocol/openid-connect/ext/par/request".formatted(keycloakInterno, realm);
        return new PedidoDeLoginEmpurrado(registros, WebClient.create(), enderecoDoPar, segredo);
    }

    /**
     * O token anti-CSRF é preguiçoso: só é gerado se alguém o pedir, e sem isso
     * o cookie nunca chega ao navegador. Este filtro o força a existir em toda
     * requisição — é a solução documentada para o WebFlux.
     */
    @Bean
    WebFilter cookieDeCsrf() {
        return (exchange, cadeia) -> {
            Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
            return token == null ? cadeia.filter(exchange) : token.then(cadeia.filter(exchange));
        };
    }
}
