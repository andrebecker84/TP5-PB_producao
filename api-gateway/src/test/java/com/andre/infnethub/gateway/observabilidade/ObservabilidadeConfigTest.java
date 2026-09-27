package com.andre.infnethub.gateway.observabilidade;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

@DisplayName("Observabilidade do gateway — onde o trace nasce, sem o ruído de operação")
class ObservabilidadeConfigTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/api/v1/usuarios,           true",
            "/api/v1/notificacoes/ao-vivo, true",
            "/oauth2/authorization/keycloak, true",
            "/actuator/health,           false",
            "/actuator/prometheus,       false"
    })
    @DisplayName("requisições recebidas: as das pessoas entram, as de operação não")
    void requisicoesRecebidas(String caminho, boolean entra) {
        var contexto = new ServerRequestObservationContext(
                MockServerHttpRequest.get(caminho).build(), new MockServerHttpResponse(), Map.of());

        assertThat(ObservabilidadeConfig.interessa("http.server.requests", contexto)).isEqualTo(entra);
    }

    @Test
    @DisplayName("chamadas reativas ao Eureka ficam de fora; ao Keycloak, dentro")
    void chamadasReativas() {
        var eureka = new ClientRequestObservationContext(ClientRequest.create(GET, URI.create("https://eureka:21761/eureka/apps/")));
        eureka.setRequest(eureka.getCarrier().build());
        var keycloak = new ClientRequestObservationContext(ClientRequest.create(GET, URI.create("https://keycloak:8443/realms/infnethub")));
        keycloak.setRequest(keycloak.getCarrier().build());

        assertThat(ObservabilidadeConfig.interessa("http.client.requests", eureka)).isFalse();
        assertThat(ObservabilidadeConfig.interessa("http.client.requests", keycloak)).isTrue();
    }

    @Test
    @DisplayName("tarefas agendadas não viram trace")
    void tarefasAgendadas() {
        assertThat(ObservabilidadeConfig.interessa("tasks.scheduled.execution", new Observation.Context())).isFalse();
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
