package com.andre.infnethub.gateway.observabilidade;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("X-Trace-Id — o identificador do trace volta na resposta")
class TraceIdNaRespostaTest {

    private final SdkTracerProvider provedor = SdkTracerProvider.builder().build();
    private final OtelTracer tracer = new OtelTracer(provedor.get("teste"), new OtelCurrentTraceContext(), e -> { });
    private final TraceIdNaResposta filtro = new TraceIdNaResposta();

    @AfterEach
    void fechar() {
        provedor.close();
    }

    /** A troca como o WebFlux a entrega aos filtros: com a observação da requisição nos atributos. */
    private MockServerWebExchange trocaRastreada(Span span) {
        MockServerWebExchange troca = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/posts"));
        var contexto = new ServerRequestObservationContext(troca.getRequest(), troca.getResponse(), troca.getAttributes());
        TracingContext rastreamento = new TracingContext();
        rastreamento.setSpan(span);
        contexto.put(TracingContext.class, rastreamento);
        troca.getAttributes().put(ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, contexto);
        return troca;
    }

    @Test
    @DisplayName("requisição rastreada: a resposta leva o traceId do span do gateway")
    void devolveOTraceId() {
        Span span = tracer.nextSpan().name("http get").start();
        MockServerWebExchange troca = trocaRastreada(span);

        filtro.filter(troca, t -> Mono.empty()).block();

        assertThat(troca.getResponse().getHeaders().getFirst(TraceIdNaResposta.CABECALHO))
                .isEqualTo(span.context().traceId())
                .matches("[0-9a-f]{32}");
        span.end();
    }

    @Test
    @DisplayName("requisição sem rastreamento (rotas de operação): nenhum cabeçalho")
    void semRastreamentoSemCabecalho() {
        MockServerWebExchange troca = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));

        filtro.filter(troca, t -> Mono.empty()).block();

        assertThat(troca.getResponse().getHeaders().containsHeader(TraceIdNaResposta.CABECALHO)).isFalse();
    }
}
