package com.andre.infnethub.dto.historico;

import com.andre.infnethub.auditoria.RevisaoAuditoria;

import java.time.LocalDateTime;

/**
 * Cabeçalho de uma revisão, sem o snapshot.
 *
 * <p>Alimenta a linha do tempo global de auditoria — "quem alterou alguma coisa,
 * e quando" — sem carregar o estado das entidades envolvidas.
 */
public record RevisaoResumoDTO(
        int revisao,
        LocalDateTime dataHora,
        String autor,
        String origem
) {
    public static RevisaoResumoDTO de(RevisaoAuditoria r) {
        return new RevisaoResumoDTO(r.getRev(), r.getDataHora(), r.getAutor(), r.getOrigem());
    }
}
