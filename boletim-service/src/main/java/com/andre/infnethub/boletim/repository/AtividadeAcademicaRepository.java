package com.andre.infnethub.boletim.repository;

import com.andre.infnethub.boletim.model.AtividadeAcademica;
import com.andre.infnethub.boletim.model.CategoriaAtividade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AtividadeAcademicaRepository extends JpaRepository<AtividadeAcademica, Long> {

    /** Apoiada em idx_atividade_aluno_categoria. */
    List<AtividadeAcademica> findByAlunoIdOrderByCategoriaAscPeriodoAsc(Long alunoId);

    List<AtividadeAcademica> findByAlunoIdAndCategoriaOrderByPeriodoAsc(Long alunoId, CategoriaAtividade categoria);

    Optional<AtividadeAcademica> findByIdAndAlunoId(Long id, Long alunoId);

    /**
     * Carga horária concluída por categoria, somada no banco.
     *
     * <p>Só CONCLUIDO conta: atividade em curso ainda não integraliza. Somar em
     * Java exigiria trazer todas as linhas para descartar a maioria — aqui volta
     * uma linha por categoria.
     */
    @Query("""
            SELECT a.categoria, COALESCE(SUM(a.cargaHoraria), 0)
            FROM AtividadeAcademica a
            WHERE a.alunoId = :alunoId AND a.status = com.andre.infnethub.boletim.model.StatusAtividade.CONCLUIDO
            GROUP BY a.categoria
            """)
    List<Object[]> somarCargaConcluidaPorCategoria(Long alunoId);
}
