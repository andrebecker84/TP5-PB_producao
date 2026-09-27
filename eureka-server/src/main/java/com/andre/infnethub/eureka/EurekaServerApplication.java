package com.andre.infnethub.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Registro de serviços do Infnet Hub.
 *
 * <p>Numa arquitetura distribuída os serviços não podem conhecer uns aos outros
 * por host e porta fixos: em qualquer ambiente que não seja a máquina do
 * desenvolvedor, esses valores mudam a cada implantação. Cada serviço se anuncia
 * aqui pelo nome lógico ({@code spring.application.name}), e quem precisa falar
 * com ele pergunta o endereço ao registro em vez de carregá-lo em configuração.
 *
 * <p>É o que permite ao gateway rotear para {@code infnethub-core} e ao
 * boletim-service chamá-lo por nome, sem que nenhum dos dois saiba a porta 21081.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurekaServerApplication.class, args);
	}
}
