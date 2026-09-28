package com.andre.infnethub.notificacao.aovivo;

import java.util.List;

/**
 * O par de {@link AvisoAoVivo}: "estas notificações do usuário X foram apagadas
 * — se ele estiver conectado a você, tire-as da tela".
 */
public record RemocaoAoVivo(Long destinatarioId, List<Long> ids) {
}
