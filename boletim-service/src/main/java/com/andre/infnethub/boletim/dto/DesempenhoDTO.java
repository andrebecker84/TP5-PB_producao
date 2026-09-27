package com.andre.infnethub.boletim.dto;

import java.util.List;

/**
 * Indicadores do painel de desempenho.
 *
 * <p>O {@code indiceGeral} é a média ponderada dos conceitos (DML 100, DL 80,
 * D 60, ND 0). É indicador derivado, criado para permitir comparar evolução ao
 * longo do tempo — algo que uma escala de quatro degraus não permite fazer bem.
 * Não substitui o conceito e não vale como nota: o {@code conceitoEquivalente}
 * existe para lembrar disso, traduzindo o número de volta para a escala real.
 */
public record DesempenhoDTO(
        Long alunoId,
        AlunoDTO aluno,
        int indiceGeral,
        String conceitoEquivalente,
        int competenciasAvaliadas,
        int dml,
        int dl,
        int d,
        int nd,
        int presencaGeral,
        int entregasNoPrazo,
        Integer blocoAtual,
        int presencaMinimaExigida,
        List<EvolucaoBlocoDTO> evolucao,
        /** Frequência das disciplinas do bloco em curso. */
        List<PresencaDisciplinaDTO> presencas,
        List<CargaCategoriaDTO> cargaHoraria
) {
}
