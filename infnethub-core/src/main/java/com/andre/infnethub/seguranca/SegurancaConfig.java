package com.andre.infnethub.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A aplicação central como <em>resource server</em>: nada entra sem um token
 * válido, emitido pelo Keycloak.
 *
 * <h2>Por que validar de novo, se o gateway já autenticou</h2>
 * <p>Porque a porta 21081 existe. Em desenvolvimento ela está publicada para
 * diagnóstico, e numa rede comprometida qualquer processo vizinho alcançaria o
 * serviço direto, sem passar pelo gateway. Confiar em "veio do gateway" é
 * confiar na rede — a premissa que as arquiteturas atuais recusam. A validação
 * é barata: a assinatura é conferida com a chave pública do Keycloak, obtida
 * uma vez e guardada em cache.
 *
 * <h2>Sem sessão</h2>
 * <p>{@code STATELESS}: cada requisição carrega o próprio token, e o serviço
 * não guarda nada entre uma e outra. Quem tem sessão é o gateway, e só ele. Com
 * isso, acrescentar uma segunda instância deste serviço não exige compartilhar
 * sessão nenhuma.
 *
 * <h2>CSRF desligado, e por quê</h2>
 * <p>O ataque de CSRF depende de o navegador anexar a credencial sozinho, o que
 * acontece com cookie. Aqui a credencial é um cabeçalho {@code Authorization}
 * que só o gateway escreve; nenhum navegador o envia por conta própria. A
 * proteção que importa está no gateway, onde o cookie existe.
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

                        // ── Regras por papel ──────────────────────────────
                        // Publicar vaga é ato institucional: quem faz é a
                        // secretaria ou a coordenação, não qualquer aluno com
                        // uma conta válida. Autenticado não é o mesmo que
                        // autorizado.
                        .requestMatchers(HttpMethod.POST, "/api/v1/vagas/**").hasAnyRole("SECRETARIA", "COORDENADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/vagas/**").hasAnyRole("SECRETARIA", "COORDENADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/vagas/**").hasAnyRole("SECRETARIA", "COORDENADOR")

                        // Avisar a turma inteira é poder: sem esta regra,
                        // qualquer aluno autenticado mandaria uma notificação
                        // para todos os outros. O professor entra porque
                        // "aula cancelada hoje" é recado dele, não da
                        // secretaria.
                        .requestMatchers(HttpMethod.POST, "/api/v1/avisos/**")
                        .hasAnyRole("PROFESSOR", "SECRETARIA", "COORDENADOR")

                        // O histórico de auditoria mostra o que cada pessoa
                        // fez e como os registros eram antes. Não é leitura
                        // de aluno.
                        .requestMatchers("/api/v1/historico/**").hasAnyRole("PROFESSOR", "SECRETARIA", "COORDENADOR")

                        // A situação acadêmica é dado do boletim aparecendo no
                        // core. Quem não pode ver o boletim de outra pessoa lá
                        // também não pode vê-lo aqui — uma regra frouxa nesta
                        // rota abriria por fora o que a outra fecha por dentro.
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/*/situacao-academica")
                        .hasAnyRole("PROFESSOR", "SECRETARIA", "COORDENADOR")

                        // O histórico de expurgo mostra quem pediu a remoção de
                        // quem, e por que ela não foi adiante. É registro de
                        // processo administrativo, não leitura de aluno.
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/*/expurgo")
                        .hasAnyRole("SECRETARIA", "COORDENADOR")

                        // Operações sobre o cadastro de pessoas e o reenvio de
                        // eventos são administrativas. Sem as regras de POST e
                        // PUT, qualquer conta autenticada cadastraria pessoas
                        // ou trocaria o nome e o e-mail de outra — e a troca
                        // ainda seguiria, por evento, para o boletim.
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios/**").hasAnyRole("SECRETARIA", "COORDENADOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/**").hasAnyRole("SECRETARIA", "COORDENADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/usuarios/**").hasAnyRole("SECRETARIA", "COORDENADOR")

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
