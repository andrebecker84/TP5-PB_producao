package com.andre.infnethub.boletim.observabilidade;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A ponte do rastreamento sobre a caixa de saída, com o tracer real do
 * OpenTelemetry — o mesmo que roda em produção, sem coletor.
 *
 * <p>O que importa provar é a costura: o que {@link Rastreamento#atual()}
 * captura numa thread, {@link Rastreamento#continuar} retoma em outra, como
 * filho do mesmo trace.
 */
@DisplayName("Rastreamento — o contexto atravessa a caixa de saída")
class RastreamentoTest {

    private final SdkTracerProvider provedor = SdkTracerProvider.builder().build();
    private final io.opentelemetry.api.trace.Tracer otel = provedor.get("teste");
    private final ContextPropagators w3c = ContextPropagators.create(W3CTraceContextPropagator.getInstance());
    private final Tracer tracer = new OtelTracer(otel, new OtelCurrentTraceContext(), evento -> { });
    private final Propagator propagador = new OtelPropagator(w3c, otel);
    private final Rastreamento rastreamento = new Rastreamento(tracer, propagador);

    @AfterEach
    void fechar() {
        provedor.close();
    }

    @Test
    @DisplayName("fora de um rastreamento não há o que guardar")
    void foraDeRastreamento() {
        assertThat(rastreamento.atual()).isNull();
    }

    @Test
    @DisplayName("dentro de uma requisição, captura o traceparent no formato W3C")
    void capturaTraceparent() {
        Span requisicao = tracer.nextSpan().name("POST /usuarios").start();
        try (Tracer.SpanInScope escopo = tracer.withSpan(requisicao)) {
            assertThat(rastreamento.atual())
                    .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
                    .contains(requisicao.context().traceId())
                    .contains(requisicao.context().spanId());
        } finally {
            requisicao.end();
        }
    }

    @Test
    @DisplayName("a publicação, em outra thread, continua o trace da requisição como filha")
    void continuaEmOutraThread() throws InterruptedException {
        Span requisicao = tracer.nextSpan().name("POST /usuarios").start();
        String guardado;
        try (Tracer.SpanInScope escopo = tracer.withSpan(requisicao)) {
            guardado = rastreamento.atual();
        } finally {
            requisicao.end();
        }

        // O relay roda na thread do agendador, sem contexto nenhum.
        AtomicReference<Span> vistoNoRelay = new AtomicReference<>();
        Thread relay = new Thread(() -> rastreamento.continuar(guardado, "outbox publicar", () -> {
            vistoNoRelay.set(tracer.currentSpan());
            return null;
        }));
        relay.start();
        relay.join();

        Span publicacao = vistoNoRelay.get();
        assertThat(publicacao).isNotNull();
        assertThat(publicacao.context().traceId()).isEqualTo(requisicao.context().traceId());
        assertThat(publicacao.context().parentId()).isEqualTo(requisicao.context().spanId());
        assertThat(publicacao.context().spanId()).isNotEqualTo(requisicao.context().spanId());
    }

    @Test
    @DisplayName("sem contexto guardado, o trabalho roda assim mesmo")
    void semContextoGuardado() {
        assertThat(rastreamento.continuar(null, "outbox publicar", () -> "publicado")).isEqualTo("publicado");
        assertThat(rastreamento.continuar("  ", "outbox publicar", () -> "publicado")).isEqualTo("publicado");
    }

    @Test
    @DisplayName("a falha na publicação chega a quem chamou, e o escopo é fechado")
    void falhaPropaga() {
        String guardado = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        assertThatThrownBy(() -> rastreamento.continuar(guardado, "outbox publicar", () -> {
            throw new IllegalStateException("broker recusou");
        })).isInstanceOf(IllegalStateException.class).hasMessage("broker recusou");

        assertThat(tracer.currentSpan()).isNull();
    }
}
