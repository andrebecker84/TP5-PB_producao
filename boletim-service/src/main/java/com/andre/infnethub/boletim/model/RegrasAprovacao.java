package com.andre.infnethub.boletim.model;

import java.util.Collection;

/**
 * As regras de aprovação da Infnet, em um lugar só.
 *
 * <p>Até aqui elas viviam dentro da página do boletim, em TypeScript. Isso
 * significava que a regra de aprovação era conhecida pela tela e por mais
 * ninguém: qualquer outro consumidor da API — um app, um relatório, a
 * secretaria — teria de reimplementá-la, e duas implementações da mesma regra
 * divergem com o tempo. Trazê-las para o serviço é o que torna o boletim uma
 * fonte de verdade em vez de uma tela que calcula sozinha.
 *
 * <p>Classe sem estado e sem dependências: são funções puras sobre os dados que
 * recebem, o que as torna testáveis sem banco nem contexto Spring.
 */
public final class RegrasAprovacao {

    /** Frequência mínima na modalidade presencial. */
    public static final int PRESENCA_MINIMA = 75;

    /** Carga horária de disciplinas exigida para a integralização do curso. */
    public static final int CARGA_DISCIPLINAS_EXIGIDA = 2160;

    private RegrasAprovacao() {
    }

    /**
     * O conceito final de uma disciplina: o pior entre os das competências.
     *
     * <p>Devolve {@code null} se alguma competência ainda não foi avaliada —
     * uma disciplina não tem conceito final enquanto está em curso, e devolver
     * o pior dos já lançados daria a impressão de resultado fechado.
     */
    public static Conceito conceitoFinal(Collection<Avaliacao> avaliacoes) {
        if (avaliacoes.isEmpty()) {
            return null;
        }
        Conceito pior = Conceito.DML;
        for (Avaliacao avaliacao : avaliacoes) {
            if (avaliacao.getConceito() == null) {
                return null;
            }
            pior = avaliacao.getConceito().pior(pior);
        }
        return pior;
    }

    /**
     * A situação apurada de uma disciplina cursada.
     *
     * <p>A ordem das checagens importa. A frequência é verificada primeiro
     * porque reprova sozinha, independentemente dos conceitos: um aluno com
     * todas as competências DML e 60% de presença está reprovado, e apurar o
     * conceito antes daria a resposta certa pelo motivo errado.
     */
    public static SituacaoDisciplina situacao(Matricula matricula) {
        boolean frequenciaOk = matricula.getDisciplina().isIsentaFrequencia()
                || matricula.getPresencaPercentual() >= PRESENCA_MINIMA;

        if (!frequenciaOk) {
            return SituacaoDisciplina.REPROVADO;
        }

        Conceito conceito = conceitoFinal(matricula.getAvaliacoes());
        if (conceito == Conceito.ND) {
            return SituacaoDisciplina.REPROVADO;
        }
        if (conceito == null) {
            return SituacaoDisciplina.CURSANDO;
        }
        return SituacaoDisciplina.APROVADO;
    }

    /** A explicação da situação, para a interface exibir junto do resultado. */
    public static String motivo(Matricula matricula) {
        boolean frequenciaOk = matricula.getDisciplina().isIsentaFrequencia()
                || matricula.getPresencaPercentual() >= PRESENCA_MINIMA;

        if (!frequenciaOk) {
            return "frequência de %d%% — abaixo dos %d%% exigidos"
                    .formatted(matricula.getPresencaPercentual(), PRESENCA_MINIMA);
        }

        Conceito conceito = conceitoFinal(matricula.getAvaliacoes());
        if (conceito == Conceito.ND) {
            return "há competência não demonstrada";
        }
        if (conceito == null) {
            return "competências ainda em avaliação neste bloco";
        }
        return "todas as competências demonstradas · conceito " + conceito.name();
    }

    /**
     * O teto de conceito imposto pelas entregas fora do prazo.
     *
     * <p>Devolve {@code null} quando não há restrição. Um TP fora do prazo
     * limita o AT a DL; dois ou mais, a D; e um TP não entregue até o prazo
     * limite torna as competências ND.
     */
    public static String avisoTp(Matricula matricula) {
        if (matricula.getTpsPendentes() > 0) {
            return "TP pendente — o AT fica ND se não for entregue até o prazo limite";
        }
        if (matricula.getTpsAtraso() >= 2) {
            return "2 TPs fora do prazo — conceitos do AT limitados a D";
        }
        if (matricula.getTpsAtraso() == 1) {
            return "1 TP fora do prazo — conceitos do AT limitados a DL";
        }
        return null;
    }

    /**
     * O índice de desempenho de um conjunto de conceitos.
     *
     * <p>Média dos pesos, arredondada. Zero para conjunto vazio — sem
     * competência avaliada não há desempenho a medir, e devolver zero é
     * preferível a propagar um NaN para a interface.
     */
    public static int indice(Collection<Conceito> conceitos) {
        if (conceitos.isEmpty()) {
            return 0;
        }
        int soma = conceitos.stream().mapToInt(Conceito::getPeso).sum();
        return Math.round((float) soma / conceitos.size());
    }

    /**
     * O conceito equivalente a um índice — o caminho inverso de {@link #indice}.
     *
     * <p>Os limiares acompanham as regras de rubrica: DML exige tudo, DL exige
     * 75%, D exige metade. Serve para lembrar que o número é uma tradução de
     * conceitos, e não uma nota que tenha vida própria.
     */
    public static Conceito conceitoEquivalente(int indice) {
        if (indice >= 95) return Conceito.DML;
        if (indice >= 75) return Conceito.DL;
        if (indice >= 55) return Conceito.D;
        return Conceito.ND;
    }
}
