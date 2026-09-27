package com.andre.infnethub.boletim.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As regras de aprovação da Infnet.
 *
 * <p>Estes testes não sobem contexto Spring nem tocam o banco, e é de propósito:
 * {@link RegrasAprovacao} é composta de funções puras, e cobri-las através de um
 * {@code @SpringBootTest} custaria segundos por asserção para exercitar a mesma
 * aritmética.
 *
 * <p>Até o TP2 estas regras viviam em TypeScript, dentro da página do boletim, e
 * não tinham teste nenhum — não havia onde pendurá-lo. Tê-las no serviço é o que
 * torna possível verificá-las.
 */
@DisplayName("Regras de aprovação")
class RegrasAprovacaoTest {

    private Matricula matricula(int presenca, boolean isenta, int tpsTotal, int atraso, int pendentes,
                                Conceito... conceitos) {
        Disciplina disciplina = Disciplina.builder()
                .nome("Disciplina de teste")
                .tipo(TipoDisciplina.REGULAR)
                .cargaHoraria(60)
                .isentaFrequencia(isenta)
                .build();

        Matricula m = Matricula.builder()
                .alunoId(1L)
                .disciplina(disciplina)
                .periodo("26E2")
                .presencaPercentual(presenca)
                .tpsTotal(tpsTotal)
                .tpsAtraso(atraso)
                .tpsPendentes(pendentes)
                .build();

        for (Conceito c : conceitos) {
            Avaliacao a = Avaliacao.builder().matricula(m).build();
            a.registrar(c);
            m.getAvaliacoes().add(a);
        }
        return m;
    }

    @Nested
    @DisplayName("conceito final da disciplina")
    class ConceitoFinal {

        @Test
        @DisplayName("é o PIOR entre os conceitos — demonstrar quase todas não aprova com louvor")
        void piorConceito() {
            assertThat(RegrasAprovacao.conceitoFinal(
                    matricula(95, false, 4, 0, 0, Conceito.DML, Conceito.DL, Conceito.DML).getAvaliacoes()))
                    .isEqualTo(Conceito.DL);
        }

        @Test
        @DisplayName("é nulo se QUALQUER competência ainda está em avaliação")
        void pendenteZeraOConceito() {
            // Devolver o pior dos já lançados daria a impressão de resultado
            // fechado numa disciplina que ainda está em curso.
            assertThat(RegrasAprovacao.conceitoFinal(
                    matricula(95, false, 4, 0, 0, Conceito.DML, null, Conceito.DML).getAvaliacoes()))
                    .isNull();
        }

        @Test
        @DisplayName("disciplina sem nenhuma competência não tem conceito")
        void semCompetencias() {
            assertThat(RegrasAprovacao.conceitoFinal(List.of())).isNull();
        }
    }

    @Nested
    @DisplayName("situação apurada")
    class Situacao {

        @Test
        @DisplayName("todas demonstradas e presença suficiente aprova")
        void aprovado() {
            assertThat(RegrasAprovacao.situacao(matricula(90, false, 4, 0, 0, Conceito.DL, Conceito.D)))
                    .isEqualTo(SituacaoDisciplina.APROVADO);
        }

        @Test
        @DisplayName("uma competência ND reprova, mesmo com as demais no máximo")
        void ndReprova() {
            assertThat(RegrasAprovacao.situacao(matricula(98, false, 4, 0, 0, Conceito.DML, Conceito.ND)))
                    .isEqualTo(SituacaoDisciplina.REPROVADO);
        }

        @Test
        @DisplayName("competência em avaliação deixa a disciplina como cursando, não reprovada")
        void pendenteFicaCursando() {
            assertThat(RegrasAprovacao.situacao(matricula(90, false, 4, 0, 0, Conceito.DML, null)))
                    .isEqualTo(SituacaoDisciplina.CURSANDO);
        }

        @Test
        @DisplayName("frequência abaixo de 75% reprova mesmo com TODAS as competências em DML")
        void frequenciaReprovaSozinha() {
            // A checagem de frequência vem antes da de conceito de propósito:
            // apurar o conceito primeiro daria a resposta certa pelo motivo errado.
            Matricula m = matricula(60, false, 4, 0, 0, Conceito.DML, Conceito.DML);
            assertThat(RegrasAprovacao.situacao(m)).isEqualTo(SituacaoDisciplina.REPROVADO);
            assertThat(RegrasAprovacao.motivo(m)).contains("frequência de 60%").contains("75%");
        }

        @Test
        @DisplayName("disciplina isenta de frequência aprova com presença baixa")
        void isentaDeFrequencia() {
            // Planejamento de Curso e Carreira: 62% de presença e ainda assim aprovado.
            assertThat(RegrasAprovacao.situacao(matricula(62, true, 2, 0, 0, Conceito.DL, Conceito.D)))
                    .isEqualTo(SituacaoDisciplina.APROVADO);
        }

        @Test
        @DisplayName("exatamente 75% de presença é suficiente — o mínimo é inclusivo")
        void limiteExatoDaFrequencia() {
            assertThat(RegrasAprovacao.situacao(matricula(75, false, 4, 0, 0, Conceito.D)))
                    .isEqualTo(SituacaoDisciplina.APROVADO);
            assertThat(RegrasAprovacao.situacao(matricula(74, false, 4, 0, 0, Conceito.D)))
                    .isEqualTo(SituacaoDisciplina.REPROVADO);
        }
    }

    @Nested
    @DisplayName("teto imposto pelas entregas")
    class AvisoTp {

        @Test
        @DisplayName("sem atraso nem pendência não há aviso")
        void semAviso() {
            assertThat(RegrasAprovacao.avisoTp(matricula(90, false, 4, 0, 0, Conceito.DL))).isNull();
        }

        @Test
        @DisplayName("um TP fora do prazo limita a DL")
        void umAtraso() {
            assertThat(RegrasAprovacao.avisoTp(matricula(90, false, 4, 1, 0, Conceito.DL)))
                    .contains("1 TP fora do prazo").contains("DL");
        }

        @Test
        @DisplayName("dois ou mais TPs fora do prazo limitam a D")
        void doisAtrasos() {
            assertThat(RegrasAprovacao.avisoTp(matricula(90, false, 4, 2, 0, Conceito.D)))
                    .contains("2 TPs fora do prazo").contains("D");
        }

        @Test
        @DisplayName("TP pendente tem precedência sobre atraso — é o alerta mais grave")
        void pendenteVemPrimeiro() {
            assertThat(RegrasAprovacao.avisoTp(matricula(90, false, 4, 2, 1, Conceito.D)))
                    .contains("TP pendente").contains("ND");
        }
    }

    @Nested
    @DisplayName("índice de desempenho")
    class Indice {

        @Test
        @DisplayName("é a média dos pesos, arredondada")
        void mediaDosPesos() {
            // DML 100 · DL 80 · D 60 → 240 / 3 = 80
            assertThat(RegrasAprovacao.indice(List.of(Conceito.DML, Conceito.DL, Conceito.D)))
                    .isEqualTo(80);
        }

        @Test
        @DisplayName("conjunto vazio devolve zero em vez de propagar NaN para a interface")
        void semConceitos() {
            assertThat(RegrasAprovacao.indice(List.of())).isZero();
        }

        @Test
        @DisplayName("traduz o índice de volta para o degrau correspondente da escala")
        void conceitoEquivalente() {
            assertThat(RegrasAprovacao.conceitoEquivalente(100)).isEqualTo(Conceito.DML);
            assertThat(RegrasAprovacao.conceitoEquivalente(95)).isEqualTo(Conceito.DML);
            assertThat(RegrasAprovacao.conceitoEquivalente(94)).isEqualTo(Conceito.DL);
            assertThat(RegrasAprovacao.conceitoEquivalente(75)).isEqualTo(Conceito.DL);
            assertThat(RegrasAprovacao.conceitoEquivalente(74)).isEqualTo(Conceito.D);
            assertThat(RegrasAprovacao.conceitoEquivalente(55)).isEqualTo(Conceito.D);
            assertThat(RegrasAprovacao.conceitoEquivalente(54)).isEqualTo(Conceito.ND);
            assertThat(RegrasAprovacao.conceitoEquivalente(0)).isEqualTo(Conceito.ND);
        }
    }

    @Test
    @DisplayName("a ordem da enumeração vai do pior para o melhor — dela depende a apuração")
    void ordemDaEscala() {
        // pior() e conceitoFinal() usam ordinal(). Reordenar a enumeração por
        // engano inverteria silenciosamente todas as apurações do boletim.
        assertThat(Conceito.values())
                .containsExactly(Conceito.ND, Conceito.D, Conceito.DL, Conceito.DML);
        assertThat(Conceito.DML.pior(Conceito.D)).isEqualTo(Conceito.D);
        assertThat(Conceito.ND.pior(Conceito.DML)).isEqualTo(Conceito.ND);
        assertThat(Conceito.DL.pior(null)).isEqualTo(Conceito.DL);
    }
}
