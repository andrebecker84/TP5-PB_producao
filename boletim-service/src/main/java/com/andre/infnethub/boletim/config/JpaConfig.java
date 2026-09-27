package com.andre.infnethub.boletim.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita o carimbo automático de {@code criadoEm} e {@code atualizadoEm}.
 *
 * <p>Sem {@code auditorAwareRef}, diferente do infnethub-core: aqui não há
 * {@code @CreatedBy}/{@code @LastModifiedBy} para preencher. Ver a nota em
 * {@code EntidadeBase} sobre por que a autoria ficou de fora deste serviço.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
