package com.andre.infnethub.boletim.service.impl;

import com.andre.infnethub.boletim.aluno.ReplicaDeAlunos;
import com.andre.infnethub.boletim.dto.DesempenhoDTO;
import com.andre.infnethub.boletim.dto.EvolucaoBlocoDTO;
import com.andre.infnethub.boletim.dto.PresencaDisciplinaDTO;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import com.andre.infnethub.boletim.model.*;
import com.andre.infnethub.boletim.repository.MatriculaRepository;
import com.andre.infnethub.boletim.service.AtividadeService;
import com.andre.infnethub.boletim.service.DesempenhoService;
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
public class DesempenhoServiceImpl implements DesempenhoService {

    private final MatriculaRepository matriculaRepository;
    private final AtividadeService atividadeService;
    private final ReplicaDeAlunos alunos;

    @Override
    @Transactional(readOnly = true)
    public DesempenhoDTO apurar(Long alunoId) {
        List<Matricula> percurso = matriculaRepository.findPercursoDoAluno(alunoId);

        if (percurso.isEmpty()) {
            throw new ResourceNotFoundException("Nenhum registro acadêmico encontrado para o aluno: " + alunoId);
        }

        List<Conceito> lancados = percurso.stream()
                .flatMap(m -> m.getAvaliacoes().stream())
                .map(Avaliacao::getConceito)
                .filter(c -> c != null)
                .toList();

        int indiceGeral = RegrasAprovacao.indice(lancados);

        return new DesempenhoDTO(
                alunoId,
                alunos.buscar(alunoId),
                indiceGeral,
                RegrasAprovacao.conceitoEquivalente(indiceGeral).name(),
                lancados.size(),
                contar(lancados, Conceito.DML),
                contar(lancados, Conceito.DL),
                contar(lancados, Conceito.D),
                contar(lancados, Conceito.ND),
                presencaGeral(percurso),
                entregasNoPrazo(percurso),
                blocoAtual(percurso),
                RegrasAprovacao.PRESENCA_MINIMA,
                evolucaoPorBloco(percurso),
                presencasDoBlocoAtual(percurso),
                atividadeService.apurarCargaHoraria(alunoId)
        );
    }

    /**
     * A frequência das disciplinas do bloco em curso.
     *
     * <p>Só do bloco atual, e não de todo o histórico: frequência é indicador
     * acionável — serve para o aluno decidir se precisa comparecer mais — e a
     * de um bloco encerrado há um ano não muda mais nada. O histórico completo
     * continua no boletim.
     */
    private List<PresencaDisciplinaDTO> presencasDoBlocoAtual(List<Matricula> percurso) {
        Integer atual = blocoAtual(percurso);
        if (atual == null) {
            return List.of();
        }
        return percurso.stream()
                .filter(m -> atual.equals(m.getDisciplina().getBloco().getNumero()))
                .map(m -> new PresencaDisciplinaDTO(
                        m.getDisciplina().getNome(),
                        m.getPeriodo(),
                        m.getPresencaPercentual(),
                        m.getDisciplina().isIsentaFrequencia(),
                        !m.getDisciplina().isIsentaFrequencia()
                                && m.getPresencaPercentual() < RegrasAprovacao.PRESENCA_MINIMA))
                .toList();
    }

    /**
     * Presença média ponderada pela carga horária.
     *
     * <p>Média simples trataria uma disciplina de 30h como equivalente a uma de
     * 60h, e o indicador diria menos do que parece: faltar num encontro de uma
     * disciplina curta pesaria o mesmo que faltar numa longa.
     */
    private int presencaGeral(List<Matricula> percurso) {
        int cargaTotal = percurso.stream().mapToInt(m -> m.getDisciplina().getCargaHoraria()).sum();
        if (cargaTotal == 0) {
            return 0;
        }
        int ponderado = percurso.stream()
                .mapToInt(m -> m.getPresencaPercentual() * m.getDisciplina().getCargaHoraria())
                .sum();
        return Math.round((float) ponderado / cargaTotal);
    }

    /**
     * Percentual de TPs entregues dentro do prazo.
     *
     * <p>Atraso e pendência contam como fora do prazo: o TP pendente ainda pode
     * ser entregue, mas neste instante não está.
     */
    private int entregasNoPrazo(List<Matricula> percurso) {
        int total = percurso.stream().mapToInt(Matricula::getTpsTotal).sum();
        if (total == 0) {
            return 100;
        }
        int foraDoPrazo = percurso.stream()
                .mapToInt(m -> m.getTpsAtraso() + m.getTpsPendentes())
                .sum();
        return Math.round((float) (total - foraDoPrazo) * 100 / total);
    }

    /**
     * O bloco em que o aluno está.
     *
     * <p>É o maior número entre os blocos que ainda têm disciplina em curso.
     * Não havendo nenhuma — curso concluído ou trancado —, vale o último bloco
     * cursado, que é o que a interface precisa mostrar como posição atual.
     */
    private Integer blocoAtual(List<Matricula> percurso) {
        return percurso.stream()
                .filter(m -> RegrasAprovacao.situacao(m) == SituacaoDisciplina.CURSANDO)
                .map(m -> m.getDisciplina().getBloco().getNumero())
                .max(Comparator.naturalOrder())
                .orElseGet(() -> percurso.stream()
                        .map(m -> m.getDisciplina().getBloco().getNumero())
                        .max(Comparator.naturalOrder())
                        .orElse(null));
    }

    /**
     * O índice bloco a bloco — a série que desenha a evolução.
     *
     * <p>Blocos sem nenhum conceito lançado ficam de fora: um ponto em zero
     * seria lido como queda de desempenho, quando na verdade significa que
     * ainda não houve avaliação.
     */
    private List<EvolucaoBlocoDTO> evolucaoPorBloco(List<Matricula> percurso) {
        Map<Bloco, List<Conceito>> porBloco = new LinkedHashMap<>();
        Map<Bloco, String> periodos = new LinkedHashMap<>();

        for (Matricula matricula : percurso) {
            Bloco bloco = matricula.getDisciplina().getBloco();
            List<Conceito> conceitos = porBloco.computeIfAbsent(bloco, b -> new ArrayList<>());
            matricula.getAvaliacoes().stream()
                    .map(Avaliacao::getConceito)
                    .filter(c -> c != null)
                    .forEach(conceitos::add);
            periodos.merge(bloco, matricula.getPeriodo(),
                    (atual, novo) -> atual.compareTo(novo) <= 0 ? atual : novo);
        }

        List<EvolucaoBlocoDTO> evolucao = new ArrayList<>();
        porBloco.forEach((bloco, conceitos) -> {
            if (conceitos.isEmpty()) {
                return;
            }
            int indice = RegrasAprovacao.indice(conceitos);
            evolucao.add(new EvolucaoBlocoDTO(
                    bloco.getNumero(),
                    bloco.getTitulo(),
                    periodos.get(bloco),
                    indice,
                    RegrasAprovacao.conceitoEquivalente(indice).name(),
                    conceitos.size()
            ));
        });
        return evolucao;
    }

    private int contar(List<Conceito> conceitos, Conceito alvo) {
        return (int) conceitos.stream().filter(c -> c == alvo).count();
    }
}
