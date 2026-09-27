package com.andre.infnethub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica que o contexto sobe inteiro: entidades mapeadas, repositórios Envers
 * instanciados e configuração de auditoria coerente.
 *
 * <p>Roda no perfil {@code test} (H2 em memória) de propósito. Sem isto cairia no
 * perfil padrão {@code dev} e exigiria um PostgreSQL no ar para o build passar.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contexto da aplicação")
class InfnetHubApplicationTests {

	@Test
	@DisplayName("o contexto carrega")
	void contextLoads() {
	}

}
