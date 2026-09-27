package com.andre.infnethub.boletim;

import com.andre.infnethub.boletim.controller.BoletimController;
import com.andre.infnethub.boletim.exception.GlobalExceptionHandler;
import com.andre.infnethub.boletim.exception.JsonErrorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O contexto sobe inteiro.
 *
 * <p>Vale mais neste serviço do que a média: ele reúne Spring Boot 4, JPA,
 * Flyway, Eureka e Spring AMQP, e combinações assim não falham no build —
 * falham na criação dos beans. Um teste que apenas sobe o contexto já apanha
 * esse tipo de quebra.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contexto do boletim-service")
class BoletimServiceApplicationTests {

    @Autowired private ApplicationContext contexto;

    @Test
    @DisplayName("carrega e registra os componentes esperados")
    void contextoCarrega() {
        assertThat(contexto).isNotNull();
        assertThat(contexto.getBean(BoletimController.class)).isNotNull();
        // O contrato de erro em JSON depende dos dois: o handler cobre o que
        // passa pelo DispatcherServlet, o controller de /error cobre o resto.
        assertThat(contexto.getBean(GlobalExceptionHandler.class)).isNotNull();
        assertThat(contexto.getBean(JsonErrorController.class)).isNotNull();
    }

    @Test
    @DisplayName("não há mais cliente HTTP para o infnethub-core no classpath")
    void semClienteDoCore() {
        // A dependência síncrona do TP3 foi substituída por eventos. Se o
        // OpenFeign voltar ao pom, este teste avisa antes que o acoplamento
        // retorne sem ninguém ter decidido por isso.
        assertThat(ClassUtils.isPresent("org.springframework.cloud.openfeign.FeignClient",
                getClass().getClassLoader())).isFalse();
    }

    @Test
    @DisplayName("o DataLoader não roda fora do perfil dev")
    void cargaDeDemonstracaoRestritaAoDev() {
        // Se ele vazasse para os testes, cada classe partiria com 3 blocos e 9
        // matrículas que ninguém pediu, e as asserções de contagem quebrariam
        // de formas difíceis de rastrear.
        assertThat(contexto.getBeanNamesForType(com.andre.infnethub.boletim.config.DataLoader.class))
                .isEmpty();
    }
}
