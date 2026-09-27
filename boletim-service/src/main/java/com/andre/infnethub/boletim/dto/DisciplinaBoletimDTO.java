package com.andre.infnethub.boletim.dto;

import java.util.List;

/**
 * Uma disciplina cursada, já apurada.
 *
 * <p>{@code situacao}, {@code conceitoFinal} e {@code avisoTp} não existem no
 * banco: são calculados a cada consulta pelas regras de aprovação. Vêm prontos
 * do servidor porque a regra é do domínio, não da tela — hoje ela está
 * implementada na própria página, o que significa que qualquer outro consumidor
 * da API (um app, um relatório) teria de reimplementá-la e poderia divergir.
 */
public record DisciplinaBoletimDTO(
        Long matriculaId,
        Long disciplinaId,
        String nome,
        String tipo,
        String tipoDescricao,
        Integer cargaHoraria,
        String periodo,
        Integer presencaPercentual,
        boolean isentaFrequencia,
        TpsDTO tps,
        String conceitoFinal,
        String situacao,
        String situacaoDescricao,
        String motivoSituacao,
        String avisoTp,
        List<CompetenciaAvaliadaDTO> competencias
) {
}
