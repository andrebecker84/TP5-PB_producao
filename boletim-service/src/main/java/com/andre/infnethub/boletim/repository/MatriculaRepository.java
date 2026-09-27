package com.andre.infnethub.boletim.repository;

import com.andre.infnethub.boletim.model.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** O percurso do aluno — é por aqui que o boletim começa a ser montado. */
@Repository
public interface MatriculaRepository extends JpaRepository<Matricula, Long> {

    /**
     * Todo o percurso do aluno em uma consulta só.
     *
     * <p>É a consulta central do serviço: alimenta tanto o boletim quanto o
     * painel de desempenho. Traz matrícula, disciplina, bloco, avaliações e as
     * competências correspondentes de uma vez — montar isso por navegação
     * preguiçosa dispararia dezenas de consultas para uma única página.
     *
     * <p>Apoiada em {@code idx_matricula_aluno}. O {@code DISTINCT} elimina a
     * repetição da linha da matrícula causada pelo produto cartesiano com as
     * avaliações.
     */
    @Query("""
            SELECT DISTINCT m FROM Matricula m
            JOIN FETCH m.disciplina d
            JOIN FETCH d.bloco b
            LEFT JOIN FETCH m.avaliacoes a
            LEFT JOIN FETCH a.competencia
            WHERE m.alunoId = :alunoId
            ORDER BY b.numero ASC, d.id ASC
            """)
    List<Matricula> findPercursoDoAluno(Long alunoId);

    Optional<Matricula> findByAlunoIdAndDisciplinaIdAndPeriodo(Long alunoId, Long disciplinaId, String periodo);

    boolean existsByAlunoId(Long alunoId);

    /** Quantas matrículas o aluno tem — responde à pergunta que precede a exclusão. */
    long countByAlunoId(Long alunoId);

    @Query("""
            SELECT m FROM Matricula m
            JOIN FETCH m.disciplina d
            LEFT JOIN FETCH m.avaliacoes a
            LEFT JOIN FETCH a.competencia
            WHERE m.id = :id
            """)
    Optional<Matricula> findByIdCompleta(Long id);
}
