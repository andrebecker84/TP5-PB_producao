package com.andre.infnethub.boletim;

import com.andre.infnethub.boletim.aluno.AlunoReplica;
import com.andre.infnethub.boletim.aluno.AlunoReplicaRepository;
import com.andre.infnethub.boletim.model.*;
import com.andre.infnethub.boletim.repository.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Montador de cenários acadêmicos para os testes.
 *
 * <p>Existe porque quase todo teste deste serviço precisa da mesma corrente de
 * objetos — bloco, disciplina, competências, matrícula, avaliações —, e repetir
 * essa montagem em cada método esconderia, no meio do ruído, a única coisa que
 * varia de um teste para o outro: os conceitos e a presença.
 *
 * <p>Não é a carga de demonstração: o {@code DataLoader} está restrito ao perfil
 * dev justamente para que os testes construam apenas o que vão asseverar.
 */
@Component
public class CenarioAcademico {

    private final BlocoRepository blocoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final CompetenciaRepository competenciaRepository;
    private final MatriculaRepository matriculaRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final AtividadeAcademicaRepository atividadeRepository;
    private final AlunoReplicaRepository replicaRepository;

    public CenarioAcademico(BlocoRepository blocoRepository,
                            DisciplinaRepository disciplinaRepository,
                            CompetenciaRepository competenciaRepository,
                            MatriculaRepository matriculaRepository,
                            AvaliacaoRepository avaliacaoRepository,
                            AtividadeAcademicaRepository atividadeRepository,
                            AlunoReplicaRepository replicaRepository) {
        this.blocoRepository = blocoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.competenciaRepository = competenciaRepository;
        this.matriculaRepository = matriculaRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.atividadeRepository = atividadeRepository;
        this.replicaRepository = replicaRepository;
    }

    /**
     * O cadastro do aluno já chegou por evento.
     *
     * <p>Grava direto na réplica, sem passar pela fila: estes testes são sobre a
     * montagem do boletim, e o caminho do evento até a réplica tem teste próprio.
     */
    public AlunoReplica alunoSincronizado(Long alunoId, String nome) {
        return replicaRepository.save(AlunoReplica.builder()
                .id(alunoId).nome(nome).escola("Faculdade Infnet").ultimoBloco("Bloco 5").classe("26E2")
                .papel("ALUNO").papelDescricao("Aluno(a)")
                .versaoOrigem(0).sincronizadoEm(LocalDateTime.now())
                .build());
    }

    /** Números de bloco são únicos no catálogo; cada teste reserva o seu. */
    public Bloco bloco(int numero, String titulo) {
        return blocoRepository.save(Bloco.builder().numero(numero).titulo(titulo).build());
    }

    public Disciplina disciplina(Bloco bloco, String nome, int carga, boolean isentaFrequencia) {
        return disciplinaRepository.save(Disciplina.builder()
                .bloco(bloco)
                .nome(nome)
                .tipo(TipoDisciplina.REGULAR)
                .cargaHoraria(carga)
                .isentaFrequencia(isentaFrequencia)
                .build());
    }

    public Matricula matricula(Long alunoId, Disciplina disciplina, String periodo,
                               int presenca, int tpsTotal, int tpsAtraso, int tpsPendentes) {
        return matriculaRepository.save(Matricula.builder()
                .alunoId(alunoId)
                .disciplina(disciplina)
                .periodo(periodo)
                .presencaPercentual(presenca)
                .tpsTotal(tpsTotal)
                .tpsAtraso(tpsAtraso)
                .tpsPendentes(tpsPendentes)
                .build());
    }

    /**
     * Cria as competências da disciplina e lança os conceitos na matrícula.
     *
     * <p>Aceita {@code null} na lista: é o estado "em avaliação", que é
     * justamente o que separa uma disciplina cursando de uma reprovada.
     */
    public List<Avaliacao> avaliar(Matricula matricula, Conceito... conceitos) {
        List<Avaliacao> criadas = new ArrayList<>();
        for (int i = 0; i < conceitos.length; i++) {
            Competencia competencia = competenciaRepository.save(Competencia.builder()
                    .disciplina(matricula.getDisciplina())
                    .nome("Competência " + (i + 1) + " de " + matricula.getDisciplina().getNome())
                    .ordem(i + 1)
                    .build());

            Avaliacao avaliacao = Avaliacao.builder()
                    .matricula(matricula)
                    .competencia(competencia)
                    .build();
            avaliacao.registrar(conceitos[i]);
            criadas.add(avaliacaoRepository.save(avaliacao));
        }
        return criadas;
    }

    public AtividadeAcademica atividade(Long alunoId, CategoriaAtividade categoria, String nome,
                                        int carga, StatusAtividade status) {
        return atividadeRepository.save(AtividadeAcademica.builder()
                .alunoId(alunoId)
                .categoria(categoria)
                .nome(nome)
                .cargaHoraria(carga)
                .periodo("26E2")
                .status(status)
                .build());
    }

    /**
     * Disciplina aprovada, montada de uma vez — o caso mais comum nos cenários.
     * Presença folgada e entregas em dia para que o resultado dependa só dos
     * conceitos informados.
     */
    public Matricula cursouEPassou(Long alunoId, Bloco bloco, String nome, int carga,
                                   String periodo, Conceito... conceitos) {
        Matricula m = matricula(alunoId, disciplina(bloco, nome, carga, false), periodo, 95, 4, 0, 0);
        avaliar(m, conceitos);
        return m;
    }
}
