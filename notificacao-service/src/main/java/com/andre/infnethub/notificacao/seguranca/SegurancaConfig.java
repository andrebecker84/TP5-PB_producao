package com.andre.infnethub.notificacao.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * O serviço de notificações como <em>resource server</em>.
 *
 * <p>Não há regra por papel aqui, e não é esquecimento: toda notificação é de
 * alguém, e o "alguém" sai do token, não da URL (ver
 * {@code UsuarioAtualResolver}). Não existe endereço que devolva a notificação
 * de outra pessoa para ser protegido.
 */
@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(req -> req
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // As métricas, para o Prometheus, pela rede interna. A porta do
                        // serviço não é publicada, e o gateway não roteia /actuator.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new PapeisDoKeycloak())))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(ErrosDeSeguranca.naoAutenticado())
                        .accessDeniedHandler(ErrosDeSeguranca.semPermissao()))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .build();
    }
}
