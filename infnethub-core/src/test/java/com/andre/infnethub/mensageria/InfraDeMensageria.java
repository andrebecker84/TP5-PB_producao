package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Um RabbitMQ de verdade, em contêiner, e uma fila que faz o papel do boletim.
 *
 * <p>O contêiner fica num campo estático, iniciado uma vez: classes de teste com
 * configurações diferentes ganham contextos diferentes, e como {@code @Bean}
 * comum o broker subiria de novo a cada uma.
 *
 * <p>{@link ServiceConnection} faz o Spring Boot apontar host, porta e
 * credenciais para o contêiner — nenhuma propriedade escrita à mão.
 */
@TestConfiguration(proxyBeanMethods = false)
class InfraDeMensageria {

    /** A fila espiã: recebe tudo sobre usuários, como a do boletim receberia. */
    static final String FILA_ESPIA = "teste.core.usuarios";

    private static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.3-management-alpine"));

    static {
        RABBITMQ.start();
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitmq() {
        return RABBITMQ;
    }

    /**
     * Durável mesmo sendo de teste: o RabbitMQ 4 recusa filas não duráveis e não
     * exclusivas ({@code transient_nonexcl_queues} descontinuado) e derruba a
     * conexão inteira, não só a declaração.
     */
    @Bean
    Queue filaEspia() {
        return QueueBuilder.durable(FILA_ESPIA).build();
    }

    @Bean
    Binding ligacaoEspia(TopicExchange exchangeDeEventos) {
        return BindingBuilder.bind(filaEspia()).to(exchangeDeEventos).with(Canais.PADRAO_USUARIO);
    }
}
