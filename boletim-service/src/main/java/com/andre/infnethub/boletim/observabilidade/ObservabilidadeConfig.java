package com.andre.infnethub.boletim.observabilidade;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * O que NÃO vira trace.
 *
 * <p>Com amostragem de 100%, tudo o que é observado vira trace — e boa parte do
 * que um serviço faz não é trabalho de ninguém: o Prometheus lendo métricas a
 * cada 15 s, o orquestrador perguntando pela saúde a cada 10 s, o relay da caixa
 * de saída acordando a cada meio segundo para descobrir que não há nada a
 * publicar, o cliente do Eureka renovando o registro. Sem filtro, um serviço
 * parado produz traces sem parar, e a busca por uma requisição de verdade vira
 * garimpo.
 *
 * <p>O filtro corta a observação na origem. Não afeta as métricas de tempo de
 * resposta, que o painel usa, porque as rotas de operação não fazem parte
 * delas de qualquer forma. E não esconde o trabalho do relay: quando ele
 * publica, o span que abre é filho da requisição que gerou o evento — ver
 * {@link Rastreamento}.
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
