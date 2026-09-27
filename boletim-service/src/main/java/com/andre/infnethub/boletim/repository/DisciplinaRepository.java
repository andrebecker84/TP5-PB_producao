package com.andre.infnethub.boletim.repository;

import com.andre.infnethub.boletim.model.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {

    List<Disciplina> findByBlocoNumeroOrderByIdAsc(Integer numeroBloco);

    Optional<Disciplina> findByBlocoNumeroAndNome(Integer numeroBloco, String nome);

    /** Traz a disciplina já com as competências, para lançar conceitos sem N+1. */
    @Query("""
            SELECT d FROM Disciplina d
            LEFT JOIN FETCH d.competencias
            WHERE d.id = :id
            """)
    Optional<Disciplina> findByIdWithCompetencias(Long id);

    /**
     * Segunda metade do carregamento do catálogo — ver a nota em
     * {@code BlocoRepository.findBlocosComDisciplinas} sobre a
     * {@code MultipleBagFetchException}.
     *
     * <p>O retorno não precisa ser usado: dentro da mesma transação, estas são
     * as mesmas instâncias já carregadas com os blocos, e inicializar aqui a
     * coleção de competências as deixa prontas para quem navegar a partir do
     * bloco. É o padrão de duas consultas contra uma única sessão.
     */
    @Query("""
            SELECT DISTINCT d FROM Disciplina d
            LEFT JOIN FETCH d.competencias
            WHERE d.bloco.id IN :blocoIds
            """)
    List<Disciplina> findComCompetenciasPorBlocos(Collection<Long> blocoIds);
}
