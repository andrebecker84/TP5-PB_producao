package com.andre.infnethub.eureka;

import com.netflix.eureka.EurekaServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O registro sobe com a configuração que o resto do sistema pressupõe.
 *
 * <p>Não é teste do Eureka — é da configuração deste projeto, que mudou por
 * causa de um defeito medido: com o cache de leitura intermediário ligado, o
 * gateway continuou mandando requisições ao endereço de um contêiner já
 * recriado por vinte segundos. Se a propriedade for renomeada numa atualização
 * do Spring Cloud, ela deixa de ter efeito em silêncio; este teste é o que
 * avisa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Registro de serviços — configuração que o sistema pressupõe")
class RegistroTest {

    @Autowired
    private EurekaServerConfig configuracao;

    @Test
    @DisplayName("responde do registro vivo, sem a cópia atualizada a cada 30 s")
    void semCacheDeLeitura() {
        assertThat(configuracao.shouldUseReadOnlyResponseCache()).isFalse();
    }

    @Test
    @DisplayName("remove instância que parou de renovar a cada 5 s, sem autopreservação")
    void despejoRapido() {
        assertThat(configuracao.shouldEnableSelfPreservation()).isFalse();
        assertThat(configuracao.getEvictionIntervalTimerInMs()).isEqualTo(5_000);
    }
}
