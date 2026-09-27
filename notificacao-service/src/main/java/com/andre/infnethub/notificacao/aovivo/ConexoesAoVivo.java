package com.andre.infnethub.notificacao.aovivo;

import com.andre.infnethub.notificacao.dto.NotificacaoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Os navegadores conectados a <strong>esta</strong> instância, por usuário.
 *
 * <p>Server-Sent Events, e não WebSocket: o fluxo é num sentido só — o servidor
 * avisa, o navegador escuta —, e SSE é HTTP comum. Passa pelo gateway sem
 * configuração especial, reconecta sozinho quando a conexão cai (o
 * {@code EventSource} do navegador faz isso por conta própria) e não precisa de
 * biblioteca no front-end.
 *
 * <p>Uma pessoa pode ter várias abas abertas; cada aba é uma conexão, e todas
 * recebem o aviso.
 *
 * <h2>Desligamento</h2>
 * <p>{@link SmartLifecycle} para fechar as conexões <em>antes</em> do
 * desligamento gracioso do servidor. O Spring Boot espera as requisições em
 * andamento terminarem — até 30 segundos —, e uma conexão SSE é uma requisição
 * que nunca termina sozinha. Sem isto, cada reimplantação de uma instância
 * levaria o prazo inteiro para sair. Fechadas aqui, os navegadores reconectam
 * de imediato, e o gateway os leva a uma instância que continua no ar.
 */
@Component
public class ConexoesAoVivo implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConexoesAoVivo.class);

    /**
     * Uma conexão dura no máximo meia hora; depois o navegador reconecta. Sem
     * limite, uma aba esquecida aberta seguraria recursos para sempre, e uma
     * conexão morta sem aviso — o notebook que fechou a tampa — só seria
     * percebida no próximo envio.
     */
    private static final Duration DURACAO_MAXIMA = Duration.ofMinutes(30);

    private final Map<Long, Set<SseEmitter>> porUsuario = new ConcurrentHashMap<>();

    private volatile boolean emOperacao;

    public SseEmitter conectar(Long usuarioId) {
        SseEmitter emissor = new SseEmitter(DURACAO_MAXIMA.toMillis());
        porUsuario.computeIfAbsent(usuarioId, id -> ConcurrentHashMap.newKeySet()).add(emissor);

        Runnable desconectar = () -> desconectar(usuarioId, emissor);
        emissor.onCompletion(desconectar);
        // No tempo máximo, encerra a resposta normalmente. Sem o complete(), o
        // Spring trata o fim do prazo como erro (AsyncRequestTimeoutException)
        // e o tratador de exceções tentaria escrever JSON num fluxo SSE.
        emissor.onTimeout(() -> {
            desconectar.run();
            emissor.complete();
        });
        emissor.onError(erro -> desconectar.run());

        // O primeiro evento confirma a conexão ao navegador — e força o envio
        // dos cabeçalhos, sem o que alguns proxies seguram a resposta até o
        // primeiro dado de verdade.
        enviar(usuarioId, emissor, SseEmitter.event().name("conectado").data("ok"));
        return emissor;
    }

    /** Entrega a notificação a todas as abas daquele usuário conectadas aqui. */
    public void entregar(Long usuarioId, NotificacaoDTO notificacao) {
        Set<SseEmitter> emissores = porUsuario.get(usuarioId);
        if (emissores == null) {
            return;
        }
        for (SseEmitter emissor : emissores) {
            enviar(usuarioId, emissor, SseEmitter.event()
                    .name("notificacao")
                    .id(String.valueOf(notificacao.id()))
                    .data(notificacao));
        }
    }

    /**
     * Um comentário SSE a cada 20 segundos, em todas as conexões.
     *
     * <p>Não é lido pelo navegador. Existe porque proxies e balanceadores fecham
     * conexões ociosas — tipicamente depois de 60 segundos sem tráfego —, e uma
     * pessoa sem notificação por um minuto perderia a conexão sem saber.
     */
    @Scheduled(fixedRate = 20_000)
    void manterVivas() {
        porUsuario.forEach((usuarioId, emissores) ->
                emissores.forEach(emissor -> enviar(usuarioId, emissor, SseEmitter.event().comment("ping"))));
    }

    @Override
    public void start() {
        emOperacao = true;
    }

    /**
     * Fase padrão do {@link SmartLifecycle}, a mais alta: no desligamento, as
     * fases param da maior para a menor, e o desligamento gracioso do servidor
     * web roda numa fase abaixo desta. As conexões fecham primeiro.
     */
    @Override
    public void stop() {
        emOperacao = false;
        int fechadas = conexoesAbertas();
        porUsuario.values().forEach(emissores -> emissores.forEach(SseEmitter::complete));
        porUsuario.clear();
        if (fechadas > 0) {
            log.info("{} conexão(ões) ao vivo encerrada(s) para o desligamento", fechadas);
        }
    }

    @Override
    public boolean isRunning() {
        return emOperacao;
    }

    public int conexoesAbertas() {
        return porUsuario.values().stream().mapToInt(Set::size).sum();
    }

    private void enviar(Long usuarioId, SseEmitter emissor, SseEmitter.SseEventBuilder evento) {
        try {
            emissor.send(evento);
        } catch (IOException | IllegalStateException e) {
            // A aba foi fechada. Não é erro: é o jeito normal de uma conexão SSE
            // terminar, e o navegador reconecta se ainda estiver interessado.
            desconectar(usuarioId, emissor);
        }
    }

    private void desconectar(Long usuarioId, SseEmitter emissor) {
        porUsuario.computeIfPresent(usuarioId, (id, emissores) -> {
            emissores.remove(emissor);
            return emissores.isEmpty() ? null : emissores;
        });
        log.debug("conexão ao vivo do usuário {} encerrada", usuarioId);
    }
}
