package com.andre.infnethub.notificacao.service;

import java.util.List;

/**
 * Evento de domínio, dentro do processo: notificações de uma pessoa acabaram
 * de ser apagadas. O par de {@link NotificacaoCriada}, pelo mesmo caminho até
 * as abas abertas.
 */
public record NotificacoesApagadas(Long destinatarioId, List<Long> ids) {
}
