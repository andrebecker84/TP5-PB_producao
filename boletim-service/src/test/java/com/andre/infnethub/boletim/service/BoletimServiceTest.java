package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.CenarioAcademico;
import com.andre.infnethub.boletim.dto.BoletimDTO;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Montagem do boletim a partir do percurso do aluno.
 *
 * <p>Nenhum dublê do infnethub-core: desde o TP4 o boletim não o chama. Os
 * dados do aluno vêm da réplica local, que o teste preenche como o evento
 * preencheria — o serviço é testável sozinho por construção, não por mock.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("BoletimService — montagem do boletim do aluno")
class BoletimServiceTest {

    /** Cada classe de teste usa uma faixa própria de ids e de números de bloco. */
    private static final AtomicInteger PROXIMO_BLOCO = new AtomicInteger(100);
    private static final AtomicInteger PROXIMO_ALUNO = new AtomicInteger(1000);

    @Autowired private BoletimService boletimService;
    @Autowired private CenarioAcademico cenario;

    private long novoAluno() {
        return PROXIMO_ALUNO.incrementAndGet();
    }

    private Bloco novoBloco(String titulo) {
        return cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), titulo);
    }

    @Test
    @DisplayName("agrupa as disciplinas por bloco e apura o resumo do percurso")
    void montaBoletimCompleto() {
        long aluno = novoAluno();
        cenario.alunoSincronizado(aluno, "Aluno de Teste");

        Bloco b1 = novoBloco("Fundamentos");
        cenario.cursouEPassou(aluno, b1, "Estruturas de Dados", 60, "25E1", Conceito.DML, Conceito.DL);
        cenario.cursouEPassou(aluno, b1, "Algoritmos", 60, "25E1", Conceito.DML, Conceito.DML);

        Bloco b2 = novoBloco("Aplicações");
        Matricula emCurso = cenario.matricula(aluno, cenario.disciplina(b2, "Microsserviços", 60, false),
                "26E2", 92, 4, 0, 1);
        cenario.avaliar(emCurso, Conceito.DML, null);

        BoletimDTO boletim = boletimService.montarBoletim(aluno);

        assertThat(boletim.blocos()).hasSize(2);
        assertThat(boletim.blocos()).extracting("numero").containsExactly(b1.getNumero(), b2.getNumero());

        var primeiro = boletim.blocos().get(0);
        assertThat(primeiro.status()).isEqualTo("CONCLUIDO");
        assertThat(primeiro.disciplinasAprovadas()).isEqualTo(2);

        var segundo = boletim.blocos().get(1);
        assertThat(segundo.status()).isEqualTo("EM_CURSO");
        assertThat(segundo.disciplinasAprovadas()).isZero();

        var resumo = boletim.resumo();
        assertThat(resumo.competenciasAvaliadas()).isEqualTo(5);   // 4 do bloco 1 + 1 do bloco 2
        assertThat(resumo.emAvaliacao()).isEqualTo(1);
        assertThat(resumo.dml()).isEqualTo(4);
        assertThat(resumo.dl()).isEqualTo(1);
        assertThat(resumo.disciplinasAprovadas()).isEqualTo(2);
        assertThat(resumo.disciplinasCursando()).isEqualTo(1);
        // Só a carga das APROVADAS conta para a integralização.
        assertThat(resumo.cargaHorariaAprovada()).isEqualTo(120);
        assertThat(resumo.presencaMinimaExigida()).isEqualTo(75);
        assertThat(resumo.cargaHorariaExigida()).isEqualTo(2160);
    }

    @Test
    @DisplayName("traz o conceito final e o motivo da situação já apurados, prontos para exibir")
    void apuraSituacaoDeCadaDisciplina() {
        long aluno = novoAluno();

        Bloco bloco = novoBloco("Bloco com reprovação por frequência");
        Matricula faltosa = cenario.matricula(aluno, cenario.disciplina(bloco, "Disciplina Faltosa", 60, false),
                "26E2", 50, 4, 1, 0);
        cenario.avaliar(faltosa, Conceito.DML, Conceito.DML);

        var disciplina = boletimService.montarBoletim(aluno).blocos().get(0).disciplinas().get(0);

        assertThat(disciplina.situacao()).isEqualTo("REPROVADO");
        assertThat(disciplina.situacaoDescricao()).isEqualTo("Reprovado");
        assertThat(disciplina.motivoSituacao()).contains("frequência de 50%");
        assertThat(disciplina.conceitoFinal()).isEqualTo("DML");
        assertThat(disciplina.avisoTp()).contains("1 TP fora do prazo");
        // O cliente recebe os TPs entregues calculados, sem ter de subtrair.
        assertThat(disciplina.tps().entregues()).isEqualTo(4);
    }

    @Test
    @DisplayName("aluno sem nenhuma matrícula é 404, não uma lista vazia")
    void alunoSemPercurso() {
        // 200 com conteúdo vazio faria o cliente exibir "nenhum bloco" como se
        // fosse um aluno recém-ingressado, escondendo um id errado.
        assertThatThrownBy(() -> boletimService.montarBoletim(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    @DisplayName("sem o cadastro do aluno na réplica, o histórico vem completo e só a identificação falta")
    void semCadastroNaReplica() {
        long aluno = novoAluno();
        // Nenhum evento de cadastro recebido para este aluno.

        Bloco bloco = novoBloco("Bloco íntegro");
        cenario.cursouEPassou(aluno, bloco, "Disciplina Íntegra", 60, "26E2", Conceito.DL, Conceito.DL);

        BoletimDTO boletim = boletimService.montarBoletim(aluno);

        assertThat(boletim.aluno().isIndisponivel()).isTrue();
        assertThat(boletim.aluno().nome()).isNull();
        assertThat(boletim.aluno().id()).isEqualTo(aluno);   // o id é verdadeiro: veio da requisição
        // O que importa continua lá.
        assertThat(boletim.blocos()).hasSize(1);
        assertThat(boletim.resumo().competenciasAvaliadas()).isEqualTo(2);
        assertThat(boletim.resumo().disciplinasAprovadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("o boletim de um aluno não enxerga o percurso de outro")
    void isolamentoEntreAlunos() {
        long alunoA = novoAluno();
        long alunoB = novoAluno();

        Bloco bloco = novoBloco("Bloco compartilhado no catálogo");
        var disciplina = cenario.disciplina(bloco, "Disciplina comum", 60, false);

        cenario.avaliar(cenario.matricula(alunoA, disciplina, "26E2", 90, 4, 0, 0), Conceito.DML);
        cenario.avaliar(cenario.matricula(alunoB, disciplina, "26E1", 80, 4, 0, 0), Conceito.D);

        // Mesma disciplina do catálogo, percursos distintos: é o que a separação
        // entre catálogo e matrícula existe para permitir.
        assertThat(boletimService.montarBoletim(alunoA).resumo().dml()).isEqualTo(1);
        assertThat(boletimService.montarBoletim(alunoA).resumo().d()).isZero();
        assertThat(boletimService.montarBoletim(alunoB).resumo().d()).isEqualTo(1);
        assertThat(boletimService.montarBoletim(alunoB).resumo().dml()).isZero();
    }
}
