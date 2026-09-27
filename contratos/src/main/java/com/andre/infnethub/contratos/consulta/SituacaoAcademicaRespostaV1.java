package com.andre.infnethub.contratos.consulta;

/**
 * O que o boletim responde sobre um aluno.
 *
 * @param consultado     {@code false} quando o boletim não respondeu a tempo.
 *                       É o campo mais importante do record: sem ele, "nenhuma
 *                       matrícula" e "não consegui perguntar" seriam a mesma
 *                       resposta — e a secretaria excluiria uma pessoa com
 *                       matrícula ativa achando que não tinha nenhuma.
 * @param matriculas     quantas matrículas o aluno tem no boletim.
 * @param avaliacoes     quantos conceitos já foram lançados para ele.
 * @param podeSerExcluido só quando o boletim respondeu <em>e</em> não há nada
 *                       registrado. Na dúvida, não.
 */
public record SituacaoAcademicaRespostaV1(
        Long alunoId,
        boolean consultado,
        int matriculas,
        int avaliacoes,
        boolean podeSerExcluido
) {

    public static SituacaoAcademicaRespostaV1 de(Long alunoId, int matriculas, int avaliacoes) {
        return new SituacaoAcademicaRespostaV1(alunoId, true, matriculas, avaliacoes,
                matriculas == 0 && avaliacoes == 0);
    }

    /** O boletim não respondeu. A ausência de resposta não é um "não". */
    public static SituacaoAcademicaRespostaV1 semResposta(Long alunoId) {
        return new SituacaoAcademicaRespostaV1(alunoId, false, 0, 0, false);
    }
}
