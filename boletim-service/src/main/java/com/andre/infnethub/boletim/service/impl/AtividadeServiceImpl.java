package com.andre.infnethub.boletim.service.impl;

import com.andre.infnethub.boletim.dto.AtividadeDTO;
import com.andre.infnethub.boletim.dto.AtividadeRequestDTO;
import com.andre.infnethub.boletim.dto.CargaCategoriaDTO;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import com.andre.infnethub.boletim.model.AtividadeAcademica;
import com.andre.infnethub.boletim.model.CategoriaAtividade;
import com.andre.infnethub.boletim.model.StatusAtividade;
import com.andre.infnethub.boletim.repository.AtividadeAcademicaRepository;
import com.andre.infnethub.boletim.service.AtividadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AtividadeServiceImpl implements AtividadeService {

    private final AtividadeAcademicaRepository atividadeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AtividadeDTO> listarPorAluno(Long alunoId) {
        return atividadeRepository.findByAlunoIdOrderByCategoriaAscPeriodoAsc(alunoId)
                .stream().map(AtividadeDTO::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtividadeDTO> listarPorCategoria(Long alunoId, String categoria) {
        return atividadeRepository
                .findByAlunoIdAndCategoriaOrderByPeriodoAsc(alunoId, categoriaDe(categoria))
                .stream().map(AtividadeDTO::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CargaCategoriaDTO> apurarCargaHoraria(Long alunoId) {
        Map<CategoriaAtividade, Integer> concluida = new EnumMap<>(CategoriaAtividade.class);
        for (Object[] linha : atividadeRepository.somarCargaConcluidaPorCategoria(alunoId)) {
            concluida.put((CategoriaAtividade) linha[0], ((Number) linha[1]).intValue());
        }

        List<CargaCategoriaDTO> resultado = new ArrayList<>();
        for (CategoriaAtividade categoria : CategoriaAtividade.values()) {
            // A eletiva não tem meta própria — integra a carga das disciplinas.
            // Sem esta exclusão ela apareceria como 0 de 0, poluindo o painel
            // com uma barra que nunca sai do lugar.
            if (categoria.getCargaHorariaExigida() == 0) {
                continue;
            }
            int feita = concluida.getOrDefault(categoria, 0);
            int exigida = categoria.getCargaHorariaExigida();
            resultado.add(new CargaCategoriaDTO(
                    categoria.name(),
                    categoria.getDescricao(),
                    feita,
                    exigida,
                    Math.min(100, Math.round((float) feita * 100 / exigida))
            ));
        }
        return resultado;
    }

    @Override
    @Transactional
    public AtividadeDTO registrar(Long alunoId, AtividadeRequestDTO dto) {
        AtividadeAcademica atividade = AtividadeAcademica.builder()
                .alunoId(alunoId)
                .categoria(categoriaDe(dto.categoria()))
                .nome(dto.nome())
                .cargaHoraria(dto.cargaHoraria())
                .periodo(dto.periodo())
                .status(StatusAtividade.valueOf(dto.status()))
                .presencaPercentual(presencaValida(dto))
                .build();

        return AtividadeDTO.fromEntity(atividadeRepository.save(atividade));
    }

    @Override
    @Transactional
    public AtividadeDTO atualizar(Long alunoId, Long id, AtividadeRequestDTO dto) {
        AtividadeAcademica atividade = buscarDoAluno(alunoId, id);

        atividade.setCategoria(categoriaDe(dto.categoria()));
        atividade.setNome(dto.nome());
        atividade.setCargaHoraria(dto.cargaHoraria());
        atividade.setPeriodo(dto.periodo());
        atividade.setStatus(StatusAtividade.valueOf(dto.status()));
        atividade.setPresencaPercentual(presencaValida(dto));

        return AtividadeDTO.fromEntity(atividadeRepository.save(atividade));
    }

    @Override
    @Transactional
    public void remover(Long alunoId, Long id) {
        atividadeRepository.delete(buscarDoAluno(alunoId, id));
    }

    /**
     * Busca a atividade exigindo que ela pertença ao aluno informado.
     *
     * <p>Filtrar pelos dois campos, e não só pelo id, é o que impede que o
     * caminho {@code /alunos/7/atividades/42} altere uma atividade do aluno 3.
     * Sem autenticação — adiada para etapa posterior — esta checagem é a única
     * coisa separando os dados de um aluno dos de outro.
     */
    private AtividadeAcademica buscarDoAluno(Long alunoId, Long id) {
        return atividadeRepository.findByIdAndAlunoId(id, alunoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Atividade não encontrada para este aluno: " + id));
    }

    /**
     * A presença só é guardada nas categorias que a exigem.
     *
     * <p>Sem isto, um estágio enviado com presença gravaria um número que
     * nenhuma regra consulta — dado que existe mas não significa nada, e que
     * mais tarde alguém interpretaria como se significasse.
     */
    private Integer presencaValida(AtividadeRequestDTO dto) {
        return categoriaDe(dto.categoria()).isExigePresenca() ? dto.presencaPercentual() : null;
    }

    private CategoriaAtividade categoriaDe(String valor) {
        try {
            return CategoriaAtividade.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Categoria de atividade desconhecida: " + valor);
        }
    }
}
