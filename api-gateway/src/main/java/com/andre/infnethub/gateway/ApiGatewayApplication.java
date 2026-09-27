package com.andre.infnethub.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto único de entrada do Infnet Hub.
 *
 * <p>Sem gateway, o front-end precisaria conhecer o endereço de cada serviço e
 * lidar com CORS de várias origens — e cada serviço novo obrigaria a mexer no
 * cliente. Com ele, o navegador fala com um endereço só e o roteamento vira
 * decisão de servidor.
 *
 * <p>É também onde o header {@code X-Usuario-Id}, que identifica o autor nas
 * revisões de auditoria, precisa ser repassado adiante: o filtro de contexto de
 * auditoria vive na aplicação central, e um header que morre no gateway deixaria
 * todo o histórico sem autoria. As rotas e esse repasse entram na etapa de
 * comunicação distribuída.
 */
@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}
}
