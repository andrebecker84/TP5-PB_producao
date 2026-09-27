package com.andre.infnethub.boletim.service.impl;

import com.andre.infnethub.boletim.aluno.ReplicaDeAlunos;
import com.andre.infnethub.boletim.dto.*;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import com.andre.infnethub.boletim.model.*;
import com.andre.infnethub.boletim.repository.MatriculaRepository;
import com.andre.infnethub.boletim.service.BoletimService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BoletimServiceImpl implements BoletimService {

    private final MatriculaRepository matriculaRepository;
    private final ReplicaDeAlunos alunos;

    @Override
    @Transactional(readOnly = true)
    public BoletimDTO montarBoletim(Long alunoId) {
        List<Matricula> percurso = matriculaRepository.findPercursoDoAluno(alunoId);

        // Aluno sem nenhuma matrícula é 404, e não uma lista vazia: quem pede o
        // boletim de um id inexistente cometeu um erro, e devolver 200 com
        // conteúdo vazio esconderia isso — o cliente mostraria "nenhum bloco"
        // como se fosse um aluno recém-ingressado.
        if (percurso.isEmpty()) {
            throw new ResourceNotFoundException("Nenhum registro acadêmico encontrado para o aluno: " + alunoId);
        }

        // Os dados do aluno saem da réplica local, alimentada por eventos do
        // core. No TP3 esta linha era uma chamada HTTP ao core, e o boletim
        // saía sem identificação sempre que ele estivesse fora. Agora a
        // disponibilidade do core não participa da leitura.
        return new BoletimDTO(
                alunoId,
                alunos.buscar(alunoId),
                agruparPorBloco(percurso),
                resumir(percurso));
    }

    /**
     * Agrupa as matrículas em blocos, preservando a ordem do curso.
     *
     * <p>{@link LinkedHashMap} e não {@code HashMap}: a consulta já devolve
     * ordenado por número de bloco, e um mapa sem ordem jogaria fora esse
     * trabalho, entregando os blocos embaralhados ao cliente.
     */
    private List<BlocoBoletimDTO> agruparPorBloco(List<Matricula> percurso) {
        Map<Bloco, List<Matricula>> porBloco = new LinkedHashMap<>();
        for (Matricula matricula : percurso) {
            porBloco.computeIfAbsent(matricula.getDisciplina().getBloco(), b -> new ArrayList<>())
                    .add(matricula);
        }

        List<BlocoBoletimDTO> blocos = new ArrayList<>();
        porBloco.forEach((bloco, matriculas) -> blocos.add(montarBloco(bloco, matriculas)));
        return blocos;
    }

    private BlocoBoletimDTO montarBloco(Bloco bloco, List<Matricula> matriculas) {
        List<DisciplinaBoletimDTO> disciplinas = matriculas.stream()
                .map(this::montarDisciplina)
                .toList();

        int aprovadas = (int) matriculas.stream()
                .filter(m -> RegrasAprovacao.situacao(m) == SituacaoDisciplina.APROVADO)
                .count();

        StatusAtividade status = statusDoBloco(matriculas);

        return new BlocoBoletimDTO(
                bloco.getNumero(),
                bloco.getTitulo(),
                // O período do bloco é o das matrículas do aluno. Em geral todas
                // coincidem; se o aluno repetiu uma disciplina em outro
                // trimestre, o mais antigo é o que marca quando ele cursou o bloco.
                matriculas.stream().map(Matricula::getPeriodo).min(Comparator.naturalOrder()).orElse(null),
                status.name(),
                status.getDescricao(),
                aprovadas,
                matriculas.size(),
                disciplinas
        );
    }

    /**
     * O bloco só está concluído quando todas as suas disciplinas foram
     * aprovadas. Havendo qualquer uma em curso, ele está em curso; caso
     * contrário — restando alguma reprovada e nenhuma cursando — não concluído.
     */
    private StatusAtividade statusDoBloco(List<Matricula> matriculas) {
        List<SituacaoDisciplina> situacoes = matriculas.stream().map(RegrasAprovacao::situacao).toList();
        if (situacoes.stream().allMatch(s -> s == SituacaoDisciplina.APROVADO)) {
            return StatusAtividade.CONCLUIDO;
        }
        if (situacoes.stream().anyMatch(s -> s == SituacaoDisciplina.CURSANDO)) {
            return StatusAtividade.EM_CURSO;
        }
        return StatusAtividade.NAO_CONCLUIDO;
    }

    private DisciplinaBoletimDTO montarDisciplina(Matricula matricula) {
        Disciplina disciplina = matricula.getDisciplina();
        SituacaoDisciplina situacao = RegrasAprovacao.situacao(matricula);
        Conceito conceitoFinal = RegrasAprovacao.conceitoFinal(matricula.getAvaliacoes());

        List<CompetenciaAvaliadaDTO> competencias = matricula.getAvaliacoes().stream()
                .sorted(Comparator.comparing(a -> a.getCompetencia().getOrdem()))
                .map(CompetenciaAvaliadaDTO::fromEntity)
                .toList();

        return new DisciplinaBoletimDTO(
                matricula.getId(),
                disciplina.getId(),
                disciplina.getNome(),
                disciplina.getTipo().name(),
                disciplina.getTipo().getDescricao(),
                disciplina.getCargaHoraria(),
                matricula.getPeriodo(),
                matricula.getPresencaPercentual(),
                disciplina.isIsentaFrequencia(),
                TpsDTO.fromEntity(matricula),
                conceitoFinal == null ? null : conceitoFinal.name(),
                situacao.name(),
                situacao.getDescricao(),
                RegrasAprovacao.motivo(matricula),
                RegrasAprovacao.avisoTp(matricula),
                competencias
        );
    }

    private ResumoBoletimDTO resumir(List<Matricula> percurso) {
        List<Avaliacao> avaliacoes = percurso.stream().flatMap(m -> m.getAvaliacoes().stream()).toList();
        List<Conceito> lancados = avaliacoes.stream().map(Avaliacao::getConceito).filter(c -> c != null).toList();

        Map<SituacaoDisciplina, Long> porSituacao = new LinkedHashMap<>();
        for (Matricula matricula : percurso) {
            porSituacao.merge(RegrasAprovacao.situacao(matricula), 1L, Long::sum);
        }

        int cargaAprovada = percurso.stream()
                .filter(m -> RegrasAprovacao.situacao(m) == SituacaoDisciplina.APROVADO)
                .mapToInt(m -> m.getDisciplina().getCargaHoraria())
                .sum();

        int presencaMedia = (int) Math.round(percurso.stream()
                .mapToInt(Matricula::getPresencaPercentual)
                .average()
                .orElse(0));

        return new ResumoBoletimDTO(
                lancados.size(),
                avaliacoes.size() - lancados.size(),
                contar(lancados, Conceito.DML),
                contar(lancados, Conceito.DL),
                contar(lancados, Conceito.D),
                contar(lancados, Conceito.ND),
                porSituacao.getOrDefault(SituacaoDisciplina.APROVADO, 0L).intValue(),
                porSituacao.getOrDefault(SituacaoDisciplina.CURSANDO, 0L).intValue(),
                porSituacao.getOrDefault(SituacaoDisciplina.REPROVADO, 0L).intValue(),
                presencaMedia,
                cargaAprovada,
                RegrasAprovacao.CARGA_DISCIPLINAS_EXIGIDA,
                RegrasAprovacao.PRESENCA_MINIMA
        );
    }

    private int contar(List<Conceito> conceitos, Conceito alvo) {
        return (int) conceitos.stream().filter(c -> c == alvo).count();
    }
}
