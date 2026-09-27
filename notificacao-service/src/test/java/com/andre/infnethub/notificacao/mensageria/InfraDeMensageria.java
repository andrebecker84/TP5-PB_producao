package com.andre.infnethub.notificacao.mensageria;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/** RabbitMQ real, em contêiner — ver a nota na classe equivalente do boletim. */
@TestConfiguration(proxyBeanMethods = false)
class InfraDeMensageria {

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
}
