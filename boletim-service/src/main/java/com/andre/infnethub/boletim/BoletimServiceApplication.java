package com.andre.infnethub.boletim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microsserviço de Desempenho Acadêmico.
 *
 * <p>Amplia o domínio do Infnet Hub com um contexto que não existia: conceitos,
 * competências e blocos cursados. Não é uma extração de algo que já estava na
 * aplicação central — é assunto novo, e por isso a fronteira entre os dois cai
 * naturalmente, sem cortar agregados ao meio.
 *
 * <p>Consequência disso: banco próprio. Nota de aluno não tem relação de
 * integridade com post nem com vaga, então não há chave estrangeira a preservar
 * entre os dois lados. O que o boletim precisa da aplicação central — os dados
 * de identificação do aluno — chega por evento, pelo RabbitMQ, e fica numa
 * réplica local. No TP3 vinha por chamada HTTP a cada leitura.
 */
@SpringBootApplication
public class BoletimServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BoletimServiceApplication.class, args);
	}
}
