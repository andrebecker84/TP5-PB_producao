package com.andre.infnethub.boletim.observabilidade;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Leva o rastreamento de uma requisição através da caixa de saída.
 *
 * <h2>O problema que a caixa de saída cria para o rastreamento</h2>
 * <p>Numa chamada síncrona, o traceId passa de um serviço ao outro sozinho: o
 * cliente HTTP o põe no cabeçalho {@code traceparent}, e o servidor do outro
 * lado o continua. Com o outbox, não há "outro lado" no momento da requisição —
 * o evento é gravado numa tabela e quem o publica é o relay, meio segundo
 * depois, numa thread do agendador que não sabe de requisição nenhuma. Sem
 * nada a mais, o trace do cadastro terminaria no {@code INSERT}, e a entrega ao
 * boletim e à notificação apareceria como um trace novo, sem pai — justamente o
 * trecho que mais interessa acompanhar numa arquitetura de eventos.
 *
 * <h2>A solução: o contexto viaja junto com o evento</h2>
 * <p>{@link #atual()} captura o {@code traceparent} (formato W3C) da requisição
 * em curso, e a caixa de saída o grava na mesma linha do evento. Na publicação,
 * o relay o lê de volta e abre um span <em>filho</em> daquele contexto com
 * {@link #continuar}; a observação do {@code RabbitTemplate}, já dentro desse
 * span, escreve o {@code traceparent} nos cabeçalhos da mensagem, e o ouvinte
 * do consumidor o continua. O resultado é um trace só, do clique no navegador
 * ao {@code INSERT} no banco do outro serviço — com o intervalo em que o evento
 * esperou na tabela visível no próprio desenho.
 *
 * <p>Sem tracer no contexto (um teste de fatia, por exemplo), os dois métodos
 * viram operações neutras: {@code null} na captura, execução direta na
 * continuação.
 */
@Component
public class Rastreamento {

    static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagador;

    @Autowired
    public Rastreamento(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagador) {
        this(tracer.getIfAvailable(() -> Tracer.NOOP), propagador.getIfAvailable(() -> Propagator.NOOP));
    }

    Rastreamento(Tracer tracer, Propagator propagador) {
        this.tracer = tracer;
        this.propagador = propagador;
    }

    /** O {@code traceparent} do span corrente, ou {@code null} fora de um rastreamento. */
    public String atual() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> portador = new HashMap<>();
        propagador.inject(span.context(), portador, Map::put);
        return portador.get(TRACEPARENT);
    }

    /**
     * Executa o trabalho num span filho do contexto gravado.
     *
     * @param traceparent o contexto capturado por {@link #atual()}; sem ele, o
     *                    trabalho roda como está, e quem estiver observado lá
     *                    dentro abre um trace novo
     * @param nome        o nome do span, como aparece no Grafana
     */
    public <T> T continuar(String traceparent, String nome, Supplier<T> trabalho) {
        if (traceparent == null || traceparent.isBlank()) {
            return trabalho.get();
        }
        Span span = propagador.extract(Map.of(TRACEPARENT, traceparent), Map::get)
                .name(nome)
                .start();
        try (Tracer.SpanInScope escopo = tracer.withSpan(span)) {
            return trabalho.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
