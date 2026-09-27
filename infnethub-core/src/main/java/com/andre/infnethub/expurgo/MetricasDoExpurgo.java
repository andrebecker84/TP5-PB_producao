package com.andre.infnethub.expurgo;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Quantas sagas de expurgo estão abertas agora.
 *
 * <p>Em operação normal o número vive perto de zero: uma saga dá a volta em
 * cerca de um segundo. Um valor que sobe e não desce é o sinal de um
 * participante que parou de responder — e de pessoas bloqueadas esperando o
 * prazo vencer. É a mesma informação que o prazo usa para agir, mostrada antes
 * de ele precisar agir.
 */
@Component
class MetricasDoExpurgo implements MeterBinder {

    private final ExpurgoRepository expurgos;

    MetricasDoExpurgo(ExpurgoRepository expurgos) {
        this.expurgos = expurgos;
    }

    @Override
    public void bindTo(MeterRegistry registro) {
        Gauge.builder("infnethub.expurgo.abertos", expurgos, r -> r.countByEstado(EstadoDoExpurgo.SOLICITADO))
                .description("Sagas de expurgo esperando a resposta dos participantes")
                .register(registro);
    }
}
