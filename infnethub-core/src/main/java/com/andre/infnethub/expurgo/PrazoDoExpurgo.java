package com.andre.infnethub.expurgo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Vigia o prazo das sagas de expurgo abertas.
 *
 * <p>Uma tarefa periódica sobre o banco, e não uma mensagem com atraso no
 * broker, por um motivo simples: o estado da saga mora na tabela
 * {@code expurgo_lgpd}, e é ela que precisa ser consultada no vencimento. Uma
 * mensagem de "verifique o prazo" chegaria ao core só para ele fazer a mesma
 * consulta — com uma peça a mais para falhar e nenhuma informação a mais.
 *
 * <p>O prazo padrão é de um dia: tempo de sobra para um participante fora do
 * ar voltar e responder, e curto o bastante para ninguém ficar bloqueado por
 * esquecimento. Em ambiente de demonstração ele é encurtado por configuração.
 */
@Component
public class PrazoDoExpurgo {

    private static final Logger log = LoggerFactory.getLogger(PrazoDoExpurgo.class);

    private final SagaDeExpurgo saga;
    private final Duration prazo;

    public PrazoDoExpurgo(SagaDeExpurgo saga, @Value("${app.expurgo.prazo:PT24H}") Duration prazo) {
        this.saga = saga;
        this.prazo = prazo;
    }

    @Scheduled(fixedDelayString = "${app.expurgo.verificacao-ms:30000}",
            initialDelayString = "${app.expurgo.verificacao-ms:30000}")
    public void verificar() {
        int encerrados = saga.expirarAbertosAntesDe(LocalDateTime.now().minus(prazo), prazo);
        if (encerrados > 0) {
            log.warn("{} expurgo(s) encerrado(s) por prazo vencido ({})", encerrados, prazo);
        }
    }

    public Duration prazo() {
        return prazo;
    }
}
