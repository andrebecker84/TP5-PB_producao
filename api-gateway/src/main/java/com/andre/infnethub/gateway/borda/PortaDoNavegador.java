package com.andre.infnethub.gateway.borda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.stereotype.Component;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * A porta por onde o navegador entra — e só ele.
 *
 * <h2>Duas portas, dois públicos</h2>
 * <p>A porta principal do gateway fala só HTTPS: por ela chegam o front-end
 * (do lado do servidor), o Prometheus e o healthcheck, todos dentro da rede do
 * Compose, e todos conferem o certificado. O navegador de quem usa o sistema
 * em desenvolvimento, porém, não confia na autoridade de desenvolvimento — e
 * um aviso de certificado inválido a cada acesso a {@code localhost} não é
 * segurança, é ruído que ensina a clicar em "continuar mesmo assim".
 *
 * <p>Esta segunda porta atende o navegador em HTTP, com o mesmo tratamento de
 * requisição da principal — mesma cadeia de segurança, mesmas rotas, mesma
 * sessão. Ela é publicada pelo Compose <strong>só no 127.0.0.1</strong> da
 * máquina: o tráfego não sai do computador de quem está usando. Em produção,
 * o que ocupa este lugar é o TLS da borda, com certificado de verdade, e esta
 * porta não é ligada.
 *
 * <h2>O que o navegador não alcança por aqui</h2>
 * <p>Os endpoints de operação do Actuator — métricas, rotas do gateway — são
 * para quem opera, pela rede interna. Por esta porta respondem só a saúde e a
 * identificação do serviço; o resto é 404, sem dizer que existe.
 */
@Component
@ConditionalOnProperty("app.porta-do-navegador")
class PortaDoNavegador implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PortaDoNavegador.class);

    private final HttpHandler tratamento;
    private final int porta;
    private volatile DisposableServer servidor;

    PortaDoNavegador(HttpHandler tratamento, @Value("${app.porta-do-navegador}") int porta) {
        this.tratamento = tratamento;
        this.porta = porta;
    }

    static boolean reservadoParaAOperacao(String caminho) {
        return caminho.startsWith("/actuator")
                && !caminho.equals("/actuator/health")
                && !caminho.startsWith("/actuator/health/")
                && !caminho.equals("/actuator/info");
    }

    @Override
    public void start() {
        HttpHandler filtrado = (requisicao, resposta) -> {
            if (reservadoParaAOperacao(requisicao.getPath().value())) {
                resposta.setStatusCode(HttpStatus.NOT_FOUND);
                return resposta.setComplete();
            }
            return tratamento.handle(requisicao, resposta);
        };
        servidor = HttpServer.create()
                .port(porta)
                .handle(new ReactorHttpHandlerAdapter(filtrado))
                .bindNow();
        log.info("porta do navegador aberta em HTTP na {}", porta);
    }

    @Override
    public void stop() {
        DisposableServer atual = servidor;
        if (atual != null) {
            atual.disposeNow();
            servidor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return servidor != null;
    }
}
