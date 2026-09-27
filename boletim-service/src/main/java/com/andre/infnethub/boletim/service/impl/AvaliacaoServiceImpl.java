package com.andre.infnethub.boletim.service.impl;

import com.andre.infnethub.boletim.dto.CompetenciaAvaliadaDTO;
import com.andre.infnethub.boletim.exception.ConflitoDeDadosException;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import com.andre.infnethub.boletim.model.Avaliacao;
import com.andre.infnethub.boletim.model.Competencia;
import com.andre.infnethub.boletim.model.Conceito;
import com.andre.infnethub.boletim.model.Matricula;
import com.andre.infnethub.boletim.repository.AvaliacaoRepository;
import com.andre.infnethub.boletim.repository.CompetenciaRepository;
import com.andre.infnethub.boletim.repository.MatriculaRepository;
import com.andre.infnethub.boletim.service.AvaliacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvaliacaoServiceImpl implements AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final MatriculaRepository matriculaRepository;
    private final CompetenciaRepository competenciaRepository;

    @Override
    @Transactional
    public CompetenciaAvaliadaDTO registrarConceito(Long matriculaId, Long competenciaId, String conceito) {
        Matricula matricula = matriculaRepository.findById(matriculaId)
                .orElseThrow(() -> new ResourceNotFoundException("Matrícula não encontrada: " + matriculaId));

        Competencia competencia = competenciaRepository.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competência não encontrada: " + competenciaId));

        // A competência precisa pertencer à disciplina que o aluno cursou. Sem
        // esta checagem seria possível lançar, numa matrícula de Back-End, o
        // conceito de uma competência de Machine Learning — o banco aceitaria,
        // porque as duas FKs são válidas isoladamente, e o boletim passaria a
        // exibir uma competência que não faz parte da disciplina.
        if (!competencia.getDisciplina().getId().equals(matricula.getDisciplina().getId())) {
            throw new ConflitoDeDadosException(
                    "A competência %d não pertence à disciplina desta matrícula.".formatted(competenciaId));
        }

        Avaliacao avaliacao = avaliacaoRepository
                .findByMatriculaIdAndCompetenciaId(matriculaId, competenciaId)
                .orElseGet(() -> Avaliacao.builder()
                        .matricula(matricula)
                        .competencia(competencia)
                        .build());

        avaliacao.registrar(conceitoDe(conceito));

        return CompetenciaAvaliadaDTO.fromEntity(avaliacaoRepository.save(avaliacao));
    }

    /** Nulo é valor legítimo: devolve a competência ao estado "em avaliação". */
    private Conceito conceitoDe(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Conceito.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Conceito desconhecido: " + valor);
        }
    }
}
