package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.CenarioAcademico;
import com.andre.infnethub.boletim.dto.DesempenhoDTO;
import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Conceito;
import com.andre.infnethub.boletim.model.Matricula;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Indicadores derivados dos conceitos.
 *
 * <p>São números que a interface exibe sem recalcular, então um erro aqui não
 * aparece como exceção — aparece como um painel plausível e errado. Daí a
 * cobertura ir atrás dos casos em que a média simples e a ponderada divergem.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DesempenhoService — indicadores do painel")
class DesempenhoServiceTest {

    private static final AtomicInteger PROXIMO_BLOCO = new AtomicInteger(200);
    private static final AtomicInteger PROXIMO_ALUNO = new AtomicInteger(2000);

    @Autowired private DesempenhoService desempenhoService;
    @Autowired private CenarioAcademico cenario;

    private long novoAluno() {
        return PROXIMO_ALUNO.incrementAndGet();
    }

    private Bloco novoBloco(String titulo) {
        return cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), titulo);
    }

    @Test
    @DisplayName("o índice geral é a média dos pesos e vem com o conceito equivalente")
    void indiceEConceito() {
        long aluno = novoAluno();
        Bloco bloco = novoBloco("Bloco do índice");
        // DML 100 · DL 80 · D 60 → 80
        cenario.cursouEPassou(aluno, bloco, "Disciplina", 60, "26E2",
                Conceito.DML, Conceito.DL, Conceito.D);

        DesempenhoDTO d = desempenhoService.apurar(aluno);

        assertThat(d.indiceGeral()).isEqualTo(80);
        assertThat(d.conceitoEquivalente()).isEqualTo("DL");
        assertThat(d.competenciasAvaliadas()).isEqualTo(3);
        assertThat(d.dml()).isEqualTo(1);
        assertThat(d.dl()).isEqualTo(1);
        assertThat(d.d()).isEqualTo(1);
        assertThat(d.nd()).isZero();
    }

    @Test
    @DisplayName("a presença é ponderada pela carga horária, não uma média simples")
    void presencaPonderada() {
        long aluno = novoAluno();
        Bloco bloco = novoBloco("Bloco da presença");

        // 100% numa disciplina de 20h e 40% numa de 80h.
        // Média simples daria 70; ponderada dá (100*20 + 40*80) / 100 = 52.
        Matricula curta = cenario.matricula(aluno, cenario.disciplina(bloco, "Curta", 20, false),
                "26E2", 100, 2, 0, 0);
        cenario.avaliar(curta, Conceito.DL);
        Matricula longa = cenario.matricula(aluno, cenario.disciplina(bloco, "Longa", 80, false),
                "26E2", 40, 2, 0, 0);
        cenario.avaliar(longa, Conceito.DL);

        // Faltar num encontro de uma disciplina curta não pode pesar o mesmo
        // que faltar numa longa.
        assertThat(desempenhoService.apurar(aluno).presencaGeral()).isEqualTo(52);
    }

    @Test
    @DisplayName("entregas no prazo contam atraso E pendência como fora do prazo")
    void entregasNoPrazo() {
        long aluno = novoAluno();
        Bloco bloco = novoBloco("Bloco das entregas");

        // 10 TPs no total, 1 atrasado e 1 pendente → 8 de 10 no prazo.
        Matricula a = cenario.matricula(aluno, cenario.disciplina(bloco, "A", 60, false), "26E2", 90, 5, 1, 0);
        cenario.avaliar(a, Conceito.DL);
        Matricula b = cenario.matricula(aluno, cenario.disciplina(bloco, "B", 60, false), "26E2", 90, 5, 0, 1);
        cenario.avaliar(b, Conceito.DL);

        assertThat(desempenhoService.apurar(aluno).entregasNoPrazo()).isEqualTo(80);
    }

    @Test
    @DisplayName("blocos sem conceito lançado ficam fora da série de evolução")
    void blocoSemConceitoNaoEntraNaEvolucao() {
        long aluno = novoAluno();

        Bloco avaliado = novoBloco("Bloco avaliado");
        cenario.cursouEPassou(aluno, avaliado, "Com conceito", 60, "25E1", Conceito.DML, Conceito.DML);

        Bloco semConceito = novoBloco("Bloco sem conceito");
        Matricula m = cenario.matricula(aluno, cenario.disciplina(semConceito, "Sem conceito", 60, false),
                "26E2", 90, 4, 0, 0);
        cenario.avaliar(m, null, null);

        DesempenhoDTO d = desempenhoService.apurar(aluno);

        // Um ponto em zero seria lido como queda de desempenho, quando na
        // verdade significa que ainda não houve avaliação.
        assertThat(d.evolucao()).hasSize(1);
        assertThat(d.evolucao().get(0).numeroBloco()).isEqualTo(avaliado.getNumero());
        assertThat(d.evolucao().get(0).indice()).isEqualTo(100);
    }

    @Test
    @DisplayName("o bloco atual é o mais avançado com disciplina em curso")
    void blocoAtualEAsPresencas() {
        long aluno = novoAluno();

        Bloco concluido = novoBloco("Bloco concluído");
        cenario.cursouEPassou(aluno, concluido, "Concluída", 60, "25E1", Conceito.DML);

        Bloco emCurso = novoBloco("Bloco em curso");
        Matricula m = cenario.matricula(aluno, cenario.disciplina(emCurso, "Em curso", 60, false),
                "26E2", 88, 4, 0, 0);
        cenario.avaliar(m, Conceito.DL, null);

        DesempenhoDTO d = desempenhoService.apurar(aluno);

        assertThat(d.blocoAtual()).isEqualTo(emCurso.getNumero());
        // As presenças mostradas são só as do bloco em curso: frequência é
        // indicador acionável, e a de um bloco encerrado não muda mais nada.
        assertThat(d.presencas()).hasSize(1);
        assertThat(d.presencas().get(0).disciplina()).isEqualTo("Em curso");
        assertThat(d.presencas().get(0).percentual()).isEqualTo(88);
        assertThat(d.presencas().get(0).abaixoDoMinimo()).isFalse();
    }

    @Test
    @DisplayName("disciplina isenta com presença baixa não é marcada como abaixo do mínimo")
    void isentaNaoEMarcadaComoBaixa() {
        long aluno = novoAluno();
        Bloco bloco = novoBloco("Bloco da isenta");
        Matricula m = cenario.matricula(aluno, cenario.disciplina(bloco, "Isenta", 30, true),
                "26E2", 55, 2, 0, 0);
        cenario.avaliar(m, Conceito.DL, null);

        var presenca = desempenhoService.apurar(aluno).presencas().get(0);

        assertThat(presenca.percentual()).isEqualTo(55);
        assertThat(presenca.isentaFrequencia()).isTrue();
        assertThat(presenca.abaixoDoMinimo()).isFalse();
    }
}
