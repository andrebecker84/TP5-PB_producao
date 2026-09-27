package com.andre.infnethub.notificacao.dto;

import com.andre.infnethub.notificacao.model.Notificacao;

import java.time.Instant;

/** Uma notificação como a interface a exibe. */
public record NotificacaoDTO(
        Long id,
        String tipo,
        String texto,
        String link,
        Instant criadaEm,
        boolean lida
) {
    public static NotificacaoDTO de(Notificacao n) {
        return new NotificacaoDTO(n.getId(), n.getTipo().name(), n.getTexto(), n.getLink(), n.getCriadaEm(), n.isLida());
    }
}
