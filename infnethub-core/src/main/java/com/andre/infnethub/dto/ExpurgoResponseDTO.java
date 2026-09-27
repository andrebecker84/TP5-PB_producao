package com.andre.infnethub.dto;

import com.andre.infnethub.expurgo.ExpurgoLgpd;

import java.time.LocalDateTime;

/**
 * O estado de um pedido de remoção, para quem perguntar.
 *
 * <p>É a resposta que o titular dos dados tem direito de receber — o que foi
 * pedido, quando, em que pé está e, se não foi adiante, por quê.
 *
 * @param estado            SOLICITADO, CONCLUIDO ou REVERTIDO.
 * @param motivo            preenchido só na reversão.
 * @param registrosMantidos preenchido só na conclusão: quantos registros
 *                          acadêmicos continuam guardados, já sem identificação.
 */
public record ExpurgoResponseDTO(
        Long id,
        Long usuarioId,
        String estado,
        Long solicitadoPor,
        LocalDateTime solicitadoEm,
        LocalDateTime encerradoEm,
        String motivo,
        Integer registrosMantidos
) {
    public static ExpurgoResponseDTO de(ExpurgoLgpd e) {
        return new ExpurgoResponseDTO(
                e.getId(), e.getUsuarioId(), e.getEstado().name(), e.getSolicitadoPor(),
                e.getSolicitadoEm(), e.getEncerradoEm(), e.getMotivo(), e.getRegistrosMantidos());
    }
}
