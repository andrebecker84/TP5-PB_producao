package com.andre.infnethub.gateway.observabilidade;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Devolve o traceId da requisição no cabeçalho {@code X-Trace-Id} da resposta.
 *
 * <p>O trace nasce aqui, no gateway, e é por ele que tudo o que aconteceu com a
 * requisição pode ser recuperado — os spans no Tempo, os logs de cada serviço no
 * Loki. Mas o traceId só existia do lado de dentro. Com ele na resposta, quem
 * relata um erro relata também onde procurá-lo: "deu 500 às 14h" vira um
 * identificador que abre o caminho inteiro da requisição no Grafana, sem
 * garimpo por horário.
 *
 * <p>Vale para toda resposta, inclusive as recusadas pela segurança (401, 403)
 * — o filtro roda antes dela. As rotas de operação, que não são rastreadas
 * (ver {@link ObservabilidadeConfig}), saem sem o cabeçalho.
 *
 * <p>O identificador não expõe nada: é um número aleatório, sem relação com
 * usuário, dado ou endereço interno.
 */
@Component
class TraceIdNaResposta implements WebFilter, Ordered {

    static final String CABECALHO = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange troca, WebFilterChain cadeia) {
        traceIdDe(troca).ifPresent(id -> troca.getResponse().getHeaders().set(CABECALHO, id));
        return cadeia.filter(troca);
    }

    /**
     * O span da requisição é aberto pelo adaptador HTTP do WebFlux antes de
     * qualquer filtro, e o contexto da observação fica nos atributos da troca.
     */
    static Optional<String> traceIdDe(ServerWebExchange troca) {
        return ServerRequestObservationContext.findCurrent(troca.getAttributes())
                .map(contexto -> contexto.<TracingContext>get(TracingContext.class))
                .map(TracingContext::getSpan)
                .map(Span::context)
                .map(TraceContext::traceId);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
