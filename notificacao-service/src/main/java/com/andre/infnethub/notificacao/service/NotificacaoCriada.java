package com.andre.infnethub.notificacao.service;

import com.andre.infnethub.notificacao.dto.NotificacaoDTO;

/**
 * Evento de domínio, dentro do processo: uma notificação acabou de ser gravada.
 *
 * <p>Circula pelo {@code ApplicationEventPublisher} do Spring, não pelo broker.
 * É a ponte entre a gravação — que acontece numa transação — e o aviso ao vivo,
 * que só deve sair depois do commit.
 */
public record NotificacaoCriada(Long destinatarioId, NotificacaoDTO notificacao) {
}
