package com.andre.infnethub.notificacao.aovivo;

import com.andre.infnethub.notificacao.dto.NotificacaoDTO;

/**
 * O recado que uma instância deste serviço deixa para as outras: "chegou
 * notificação para o usuário X — se ele estiver conectado a você, entregue".
 *
 * <p>Fica aqui, e não no módulo de contratos, porque não é contrato entre
 * serviços: só instâncias do próprio notificacao-service o publicam e o leem.
 * Pode mudar de forma a qualquer momento sem que ninguém de fora perceba.
 */
public record AvisoAoVivo(Long destinatarioId, NotificacaoDTO notificacao) {
}
