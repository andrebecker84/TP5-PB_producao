package com.andre.infnethub.boletim.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * O boletim como <em>resource server</em>.
 *
 * <p>Aqui as regras não cabem todas na URL: "o aluno vê o próprio boletim"
 * depende de comparar o id do caminho com o do token. Por isso
 * {@link EnableMethodSecurity} — a regra fica ao lado do endpoint que ela
 * protege, em {@code @PreAuthorize}, e não espalhada numa lista de caminhos
 * que ninguém relê. Ver {@link Acesso}.
 *
 * <p>Lançar conceito é outra coisa: é ato de professor, e a URL basta para
 * dizer isso.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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

                        // Quem lança e corrige conceito é quem ensina.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/boletim/matriculas/**")
                        .hasAnyRole("PROFESSOR", "SECRETARIA", "COORDENADOR")

                        // O catálogo do curso — blocos, disciplinas, escala de
                        // conceitos — é informação institucional, igual para
                        // todos. Basta estar autenticado.
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
