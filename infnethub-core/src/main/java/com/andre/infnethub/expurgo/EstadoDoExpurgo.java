package com.andre.infnethub.expurgo;

/**
 * Onde a saga está.
 *
 * <p>Três estados, e nenhum deles é "falhou". Uma saga que falha tecnicamente
 * continua em {@link #SOLICITADO}: a mensagem está na fila ou na fila de
 * mensagens mortas, e o processo espera. O que encerra a saga é uma decisão —
 * cumprida ou recusada —, não a ausência de resposta.
 */
public enum EstadoDoExpurgo {

    /**
     * O pedido foi aceito e a pessoa está bloqueada. Os participantes ainda não
     * responderam, ou responderam em parte.
     */
    SOLICITADO,

    /** Todos cumpriram. O cadastro foi anonimizado e não volta. */
    CONCLUIDO,

    /**
     * Um participante recusou, com motivo. O bloqueio foi desfeito — é a
     * compensação — e a pessoa voltou a existir.
     */
    REVERTIDO;

    public boolean emAndamento() {
        return this == SOLICITADO;
    }
}
