package com.andre.infnethub.boletim.service.impl;

import com.andre.infnethub.boletim.dto.CatalogoBlocoDTO;
import com.andre.infnethub.boletim.dto.ConceitoDTO;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Conceito;
import com.andre.infnethub.boletim.repository.BlocoRepository;
import com.andre.infnethub.boletim.repository.DisciplinaRepository;
import com.andre.infnethub.boletim.service.CatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogoServiceImpl implements CatalogoService {

    private final BlocoRepository blocoRepository;
    private final DisciplinaRepository disciplinaRepository;

    /**
     * O catálogo inteiro em duas consultas de custo fixo.
     *
     * <p>A divisão em duas não é escolha de estilo: o Hibernate recusa buscar
     * duas coleções {@code List} num único {@code JOIN FETCH}
     * ({@code MultipleBagFetchException}). A segunda consulta parece não ter
     * uso — seu retorno é descartado —, mas roda dentro da mesma transação e
     * portanto da mesma sessão: as disciplinas que ela carrega são as mesmas
     * instâncias já presas aos blocos, e é ela que deixa as competências
     * inicializadas antes de a sessão fechar.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CatalogoBlocoDTO> listarBlocos() {
        List<Bloco> blocos = blocoRepository.findBlocosComDisciplinas();
        inicializarCompetencias(blocos);
        return blocos.stream().map(CatalogoBlocoDTO::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoBlocoDTO buscarBloco(Integer numero) {
        Bloco bloco = blocoRepository.findByNumero(numero)
                .orElseThrow(() -> new ResourceNotFoundException("Bloco não encontrado: " + numero));
        inicializarCompetencias(List.of(bloco));
        return CatalogoBlocoDTO.fromEntity(bloco);
    }

    /**
     * Sem acesso ao banco: a escala de conceitos é regra do modelo de avaliação,
     * não dado variável. Vive na enumeração {@code Conceito} e é publicada aqui
     * para que o cliente a leia de um lugar só.
     */
    @Override
    public List<ConceitoDTO> listarConceitos() {
        return Arrays.stream(Conceito.values()).map(ConceitoDTO::fromEnum).toList();
    }

    private void inicializarCompetencias(List<Bloco> blocos) {
        if (blocos.isEmpty()) {
            return;
        }
        disciplinaRepository.findComCompetenciasPorBlocos(blocos.stream().map(Bloco::getId).toList());
    }
}
