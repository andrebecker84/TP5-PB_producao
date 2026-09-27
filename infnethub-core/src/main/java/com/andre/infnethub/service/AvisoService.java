package com.andre.infnethub.service;

import com.andre.infnethub.dto.AvisoRequestDTO;
import com.andre.infnethub.dto.AvisoResponseDTO;

/** Porta de serviço — Bounded Context: Identidade (comunicação institucional) */
public interface AvisoService {

    /**
     * Aceita o pedido de aviso e o deposita na caixa de saída.
     *
     * <p>Não entrega nada: entregar é trabalho do serviço de notificação, que
     * pode até estar fora do ar neste momento. O que este método garante é que
     * o pedido não se perde.
     *
     * @param solicitadoPorId quem pediu, lido do token — nunca do corpo da
     *                        requisição.
     */
    AvisoResponseDTO enviar(AvisoRequestDTO dto, Long solicitadoPorId);
}
