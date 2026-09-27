package com.andre.infnethub.dto.historico;

import java.time.LocalDateTime;

/**
 * Uma entrada do histórico: o que mudou, quando, por quem — e o estado completo
 * da entidade naquele instante.
 *
 * @param snapshot estado integral da entidade na revisão, não um diff. Comparar
 *                 duas revisões consecutivas é o que revela o que mudou; guardar
 *                 o estado inteiro permite responder "como estava em T?" lendo
 *                 uma única linha, sem reconstruir a partir do início.
 */
public record RevisaoDTO<T>(
        int revisao,
        TipoAlteracao tipo,
        String tipoDescricao,
        LocalDateTime dataHora,
        String autor,
        String origem,
        T snapshot
) {
}
