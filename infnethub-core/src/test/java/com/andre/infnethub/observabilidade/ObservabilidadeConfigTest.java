package com.andre.infnethub.observabilidade;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Observabilidade — o que vira trace e o que é ruído de operação")
class ObservabilidadeConfigTest {

    private static ServerRequestObservationContext servidor(String caminho) {
        return new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", caminho), new MockHttpServletResponse());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/api/v1/usuarios,           true",
            "/api/v1/posts/7/curtidas,   true",
            "/actuator/health,           false",
            "/actuator/prometheus,       false",
            "/actuator/health/readiness, false"
    })
    @DisplayName("requisições recebidas: as da API entram, as de operação não")
    void requisicoesRecebidas(String caminho, boolean entra) {
        assertThat(ObservabilidadeConfig.interessa("http.server.requests", servidor(caminho))).isEqualTo(entra);
    }

    @Test
    @DisplayName("a renovação do registro no Eureka não vira trace; outra chamada de saída vira")
    void chamadasDeSaida() {
        var eureka = new ClientRequestObservationContext(new MockClientHttpRequest(
                HttpMethod.PUT, URI.create("https://eureka:21761/eureka/apps/INFNETHUB-CORE/x")));
        var keycloak = new ClientRequestObservationContext(new MockClientHttpRequest(
                HttpMethod.GET, URI.create("https://keycloak:8443/realms/infnethub/protocol/openid-connect/certs")));

        assertThat(ObservabilidadeConfig.interessa("http.client.requests", eureka)).isFalse();
        assertThat(ObservabilidadeConfig.interessa("http.client.requests", keycloak)).isTrue();
    }

    @Test
    @DisplayName("tarefas agendadas não viram trace — o relay aparece como filho da requisição de origem")
    void tarefasAgendadas() {
        assertThat(ObservabilidadeConfig.interessa("tasks.scheduled.execution", new Observation.Context())).isFalse();
    }

    @Test
    @DisplayName("a mensageria continua rastreada")
    void mensageriaContinua() {
        assertThat(ObservabilidadeConfig.interessa("spring.rabbit.listener", new Observation.Context())).isTrue();
        assertThat(ObservabilidadeConfig.interessa("spring.rabbit.template", new Observation.Context())).isTrue();
    }

    @Test
    @DisplayName("a cadeia do Spring Security sem requisição-pai (sondas, coletas) não vira trace; dentro de uma requisição, vira")
    void cadeiaDeSeguranca() {
        ObservationRegistry registro = ObservationRegistry.create();
        registro.observationConfig().observationHandler(contexto -> true);
        Observation requisicao = Observation.start("http.server.requests", registro);

        var solta = new Observation.Context();
        var daDescartada = new Observation.Context();
        daDescartada.setParentObservation(Observation.NOOP);
        var filha = new Observation.Context();
        filha.setParentObservation(requisicao);

        assertThat(ObservabilidadeConfig.interessa("spring.security.filterchains", solta)).isFalse();
        assertThat(ObservabilidadeConfig.interessa("spring.security.authorizations", solta)).isFalse();
        assertThat(ObservabilidadeConfig.interessa("spring.security.filterchains", daDescartada)).isFalse();
        assertThat(ObservabilidadeConfig.interessa("spring.security.filterchains", filha)).isTrue();
        requisicao.stop();
    }
}
