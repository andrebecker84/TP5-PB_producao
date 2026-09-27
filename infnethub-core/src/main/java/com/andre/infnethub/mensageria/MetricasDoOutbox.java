package com.andre.infnethub.mensageria;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * O estado da caixa de saída, para o painel de operação.
 *
 * <p>Duas medidas, porque respondem a perguntas diferentes. <em>Quantas</em>
 * mensagens esperam diz o tamanho do represamento; <em>há quanto tempo</em> a
 * mais antiga espera diz se ele está andando. Mil pendentes com a mais antiga de
 * meio segundo é um pico sendo escoado; três pendentes com a mais antiga de dez
 * minutos é o broker fora do ar, ou uma mensagem sem destino — o que nenhuma
 * contagem mostraria.
 *
 * <p>Lidas no momento da coleta, direto do banco: o Prometheus pergunta a cada
 * quinze segundos, e as duas consultas usam o índice parcial das pendentes.
 */
@Component
class MetricasDoOutbox implements MeterBinder {

    private final OutboxRepository outbox;

    MetricasDoOutbox(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public void bindTo(MeterRegistry registro) {
        Gauge.builder("infnethub.outbox.pendentes", outbox, OutboxRepository::countByPublicadoEmIsNull)
                .description("Mensagens gravadas e ainda não confirmadas pelo broker")
                .register(registro);

        Gauge.builder("infnethub.outbox.espera.maxima", outbox, MetricasDoOutbox::esperaDaMaisAntiga)
                .description("Há quanto tempo espera a mensagem pendente mais antiga")
                .baseUnit("seconds")
                .register(registro);
    }

    private static double esperaDaMaisAntiga(OutboxRepository outbox) {
        return outbox.findFirstByPublicadoEmIsNullOrderByIdAsc()
                .map(m -> (double) Duration.between(m.getCriadoEm(), LocalDateTime.now()).toMillis() / 1000)
                .orElse(0.0);
    }
}
