package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.CenarioAcademico;
import com.andre.infnethub.boletim.exception.ConflitoDeDadosException;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import com.andre.infnethub.boletim.model.Avaliacao;
import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Conceito;
import com.andre.infnethub.boletim.model.Matricula;
import com.andre.infnethub.boletim.repository.AvaliacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lançamento e correção de conceitos.
 *
 * <p>É a única escrita que altera o resultado acadêmico do aluno, e a que mais
 * precisa recusar entrada incoerente: o banco aceitaria ligar uma avaliação a
 * uma competência de outra disciplina, porque as duas chaves estrangeiras são
 * válidas isoladamente.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AvaliacaoService — lançamento de conceitos")
class AvaliacaoServiceTest {

    private static final AtomicInteger PROXIMO_BLOCO = new AtomicInteger(500);
    private static final AtomicInteger PROXIMO_ALUNO = new AtomicInteger(6000);

    @Autowired private AvaliacaoService avaliacaoService;
    @Autowired private AvaliacaoRepository avaliacaoRepository;
    @Autowired private CenarioAcademico cenario;

    /** Devolve long, e não int: o contador não faz autoboxing para Long sozinho. */
    private long novoAluno() {
        return PROXIMO_ALUNO.incrementAndGet();
    }

    @Test
    @DisplayName("lança o conceito e carimba a data da avaliação")
    void lancaConceito() {
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco do lançamento");
        Matricula m = cenario.matricula(novoAluno(),
                cenario.disciplina(bloco, "Disciplina", 60, false), "26E2", 90, 4, 0, 0);
        List<Avaliacao> avaliacoes = cenario.avaliar(m, (Conceito) null);
        Long competenciaId = avaliacoes.get(0).getCompetencia().getId();

        var resultado = avaliacaoService.registrarConceito(m.getId(), competenciaId, "DML");

        assertThat(resultado.conceito()).isEqualTo("DML");
        assertThat(resultado.conceitoNome()).isEqualTo("Demonstrou com Máximo Louvor");
        assertThat(resultado.conceitoRegra()).isNotBlank();
        assertThat(avaliacaoRepository.findById(avaliacoes.get(0).getId()).orElseThrow().getAvaliadoEm())
                .isNotNull();
    }

    @Test
    @DisplayName("enviar nulo devolve a competência a 'em avaliação' e limpa a data")
    void desfazLancamento() {
        // É o caminho para corrigir um conceito digitado errado. Sem ele, um
        // lançamento equivocado só poderia ser trocado por outro, nunca retirado.
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco do desfazer");
        Matricula m = cenario.matricula(novoAluno(),
                cenario.disciplina(bloco, "Disciplina", 60, false), "26E2", 90, 4, 0, 0);
        List<Avaliacao> avaliacoes = cenario.avaliar(m, Conceito.ND);
        Long competenciaId = avaliacoes.get(0).getCompetencia().getId();

        var resultado = avaliacaoService.registrarConceito(m.getId(), competenciaId, null);

        assertThat(resultado.conceito()).isNull();
        assertThat(avaliacaoRepository.findById(avaliacoes.get(0).getId()).orElseThrow().getAvaliadoEm())
                .isNull();
    }

    @Test
    @DisplayName("é idempotente: lançar o mesmo conceito duas vezes não cria uma segunda avaliação")
    void idempotente() {
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco idempotente");
        Matricula m = cenario.matricula(novoAluno(),
                cenario.disciplina(bloco, "Disciplina", 60, false), "26E2", 90, 4, 0, 0);
        Long competenciaId = cenario.avaliar(m, (Conceito) null).get(0).getCompetencia().getId();

        avaliacaoService.registrarConceito(m.getId(), competenciaId, "DL");
        avaliacaoService.registrarConceito(m.getId(), competenciaId, "DL");

        // É por isso que o verbo é PUT, e não POST.
        assertThat(avaliacaoRepository.findByMatriculaId(m.getId())).hasSize(1);
    }

    @Test
    @DisplayName("recusa competência que não pertence à disciplina da matrícula")
    void competenciaDeOutraDisciplina() {
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco cruzado");
        long aluno = novoAluno();

        Matricula backEnd = cenario.matricula(aluno,
                cenario.disciplina(bloco, "Back-End", 60, false), "26E2", 90, 4, 0, 0);
        cenario.avaliar(backEnd, Conceito.DL);

        Matricula machineLearning = cenario.matricula(aluno,
                cenario.disciplina(bloco, "Machine Learning", 60, false), "26E2", 90, 4, 0, 0);
        Long competenciaDeML = cenario.avaliar(machineLearning, Conceito.D).get(0).getCompetencia().getId();

        // O banco aceitaria: as duas FKs são válidas isoladamente. Quem recusa
        // é a aplicação — e sem isso o boletim exibiria, em Back-End, uma
        // competência de Machine Learning.
        assertThatThrownBy(() -> avaliacaoService.registrarConceito(backEnd.getId(), competenciaDeML, "DML"))
                .isInstanceOf(ConflitoDeDadosException.class)
                .hasMessageContaining("não pertence à disciplina");
    }

    @Test
    @DisplayName("matrícula, competência ou conceito inexistentes viram 404")
    void referenciasInexistentes() {
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco das referências");
        Matricula m = cenario.matricula(novoAluno(),
                cenario.disciplina(bloco, "Disciplina", 60, false), "26E2", 90, 4, 0, 0);
        Long competenciaId = cenario.avaliar(m, (Conceito) null).get(0).getCompetencia().getId();

        assertThatThrownBy(() -> avaliacaoService.registrarConceito(999_999L, competenciaId, "DL"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Matrícula");
        assertThatThrownBy(() -> avaliacaoService.registrarConceito(m.getId(), 999_999L, "DL"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Competência");
        assertThatThrownBy(() -> avaliacaoService.registrarConceito(m.getId(), competenciaId, "XPTO"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("XPTO");
    }
}
