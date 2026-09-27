package com.andre.infnethub.gateway.observabilidade;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

/**
 * O que NÃO vira trace no gateway.
 *
 * <p>O gateway é onde o trace nasce, e por isso é também onde o ruído mais
 * atrapalharia: cada leitura de métricas do Prometheus, cada pergunta de saúde
 * do orquestrador e cada renovação do registro no Eureka virariam um trace
 * próprio, misturado aos das pessoas usando a plataforma. As rotas de operação
 * ficam fora; o login (a conversa com o Keycloak) e todo repasse aos serviços
 * continuam rastreados.
 *
 * <p>O gateway é reativo, e os contextos de observação do servidor são os do
 * WebFlux — daí as classes diferentes das usadas nos serviços.
 */
@Configuration(proxyBeanMethods = false)
class ObservabilidadeConfig {

    @Bean
    ObservationPredicate semRuidoDeOperacao() {
        return ObservabilidadeConfig::interessa;
    }

    static boolean interessa(String nome, Observation.Context contexto) {
        if ("tasks.scheduled.execution".equals(nome)) {
            return false;
        }
        // O Spring Security observa a própria cadeia de filtros. Numa requisição
        // descartada abaixo (uma sonda, uma coleta do Prometheus) essas
        // observações nascem sem pai e virariam traces soltos. No WebFlux o pai
        // só é ligado depois desta decisão, então aqui elas saem de todo trace,
        // inclusive dos de verdade: o trace começa no "http post" do gateway, e
        // a autenticação e a autorização de cada serviço continuam nele. Troca
        // consciente — são passos de menos de 1 ms, contra centenas de traces
        // soltos por minuto das sondas.
        if (nome.startsWith("spring.security.")) {
            ObservationView pai = contexto.getParentObservation();
            if (pai == null || (pai instanceof Observation observacao && observacao.isNoop())) {
                return false;
            }
        }
        if (contexto instanceof ServerRequestObservationContext servidor) {
            return !servidor.getCarrier().getPath().value().startsWith("/actuator");
        }
        if (contexto instanceof ClientRequestObservationContext cliente && cliente.getCarrier() != null) {
            return !cliente.getCarrier().getURI().getPath().contains("/eureka/");
        }
        if (contexto instanceof org.springframework.web.reactive.function.client.ClientRequestObservationContext cliente
                && cliente.getRequest() != null) {
            return !cliente.getRequest().url().getPath().contains("/eureka/");
        }
        return true;
    }
}
