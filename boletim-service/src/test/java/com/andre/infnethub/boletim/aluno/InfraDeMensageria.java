package com.andre.infnethub.boletim.aluno;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * RabbitMQ real, em contêiner, para os testes do consumidor.
 *
 * <p>Repete a do core de propósito: são poucas linhas, e compartilhá-las
 * obrigaria a publicar um <em>test-jar</em> entre módulos — dependência de teste
 * cruzando serviços, justamente o que a separação quer evitar.
 */
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
