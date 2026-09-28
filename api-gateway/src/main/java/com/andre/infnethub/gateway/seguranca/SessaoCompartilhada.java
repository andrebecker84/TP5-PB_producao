package com.andre.infnethub.gateway.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

/**
 * Tudo o que o login deixa no gateway fica na sessão — e, no cluster, a sessão
 * fica no Redis.
 *
 * <p>A sessão guarda o que o BFF tem de mais valioso: o pedido de login em
 * andamento e, depois dele, quem é a pessoa e os tokens dela. Na memória, tudo
 * isso era da réplica que atendeu. O Service do cluster prendia o navegador
 * àquela réplica, e isso funcionava até ela reiniciar — numa atualização, numa
 * falha da liveness, no autoescalonamento removendo réplicas. Aí as sessões
 * sumiam junto: quem estava logado caía, e quem estava no meio do login voltava
 * com "o pedido expirou".
 */
@Configuration(proxyBeanMethods = false)
class SessaoCompartilhada {

    /**
     * Os tokens do Keycloak dentro da sessão.
     *
     * <p>O padrão do Spring Security os guarda num mapa em memória, à parte da
     * sessão. Com a sessão no Redis, o reinício de uma réplica preservava quem
     * estava logado mas perdia os tokens, e a próxima chamada voltava 401 —
     * verificado no cluster, reiniciando o gateway com uma pessoa logada. Na
     * sessão, os tokens vão para onde a sessão for.
     */
    @Bean
    ServerOAuth2AuthorizedClientRepository clientesAutorizados() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    /**
     * Só no cluster. No Compose há um gateway só, e um Redis a mais seria
     * infraestrutura sem problema para resolver.
     */
    @Configuration(proxyBeanMethods = false)
    @Profile("k8s")
    @EnableRedisWebSession(redisNamespace = "infnethub:sessao")
    static class NoRedis {
    }
}
