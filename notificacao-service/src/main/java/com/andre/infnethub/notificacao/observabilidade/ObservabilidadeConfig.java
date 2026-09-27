package com.andre.infnethub.notificacao.observabilidade;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * O que NÃO vira trace: rotas de operação ({@code /actuator}, lidas pelo
 * Prometheus e pelo orquestrador), tarefas agendadas e a renovação do registro
 * no Eureka. Com amostragem de 100%, sem este filtro um serviço parado produz
 * traces sem parar. A explicação completa está na classe de mesmo nome do
 * infnethub-core.
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
        // observações nascem sem pai, ou filhas da observação vazia que ocupa o
        // lugar da descartada, e virariam traces soltos; nas requisições de
        // verdade são filhas da requisição, e ficam.
        if (nome.startsWith("spring.security.")) {
            ObservationView pai = contexto.getParentObservation();
            if (pai == null || (pai instanceof Observation observacao && observacao.isNoop())) {
                return false;
            }
        }
        if (contexto instanceof ServerRequestObservationContext servidor) {
            return !servidor.getCarrier().getRequestURI().startsWith("/actuator");
        }
        if (contexto instanceof ClientRequestObservationContext cliente && cliente.getCarrier() != null) {
            return !cliente.getCarrier().getURI().getPath().contains("/eureka/");
        }
        return true;
    }
}
