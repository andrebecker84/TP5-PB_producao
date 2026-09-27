package com.andre.infnethub.gateway.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS do gateway, num lugar só.
 *
 * <p>Antes vinha da configuração do Spring Cloud Gateway ({@code globalcors}),
 * que vale para as rotas encaminhadas. Isso deixava de fora as respostas do
 * próprio gateway — inclusive o 401 de quem não está autenticado, que é
 * respondido antes de qualquer roteamento.
 *
 * <p>O efeito era confuso: o front-end recebia um erro de rede, e não um 401. O
 * navegador bloqueia a resposta sem os cabeçalhos de CORS, então o
 * {@code fetch} falha antes de a aplicação conseguir ler o status — e a tela de
 * login dizia "não foi possível falar com o servidor" quando o servidor tinha
 * respondido direitinho.
 *
 * <p>{@code allowCredentials} é o que permite o cookie de sessão atravessar
 * origens (porta 21000 → 21080). Com ele, a origem tem de ser explícita: o
 * curinga {@code *} é recusado pela especificação justamente porque permitiria
 * a qualquer site usar a sessão de quem está logado.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.front-end.url}") String urlDoFrontEnd) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(urlDoFrontEnd));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", cors);
        return fonte;
    }
}
