package com.andre.infnethub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    /**
     * Origens liberadas, vindas de {@code app.cors.allowed-origins}.
     *
     * <p>No TP1 a origem era fixa no código (`http://localhost:3000`). Além de
     * não sobreviver a produção, isso obrigava a recompilar para trocar a porta
     * do front — e o TP2 usa um bloco de portas próprio para não disputar com
     * outros projetos na mesma máquina.
     */
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        // X-Usuario-Id precisa passar: é por ele que o cliente
                        // identifica o autor das alterações para a auditoria.
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
