package com.andre.infnethub.boletim.repository;

import com.andre.infnethub.boletim.model.Bloco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Catálogo do curso — a estrutura de blocos, igual para todos os alunos. */
@Repository
public interface BlocoRepository extends JpaRepository<Bloco, Long> {

    Optional<Bloco> findByNumero(Integer numero);

    List<Bloco> findAllByOrderByNumeroAsc();

    /**
     * Blocos com suas disciplinas, em uma consulta.
     *
     * <p>Traz apenas <em>uma</em> coleção. A tentativa natural — buscar blocos,
     * disciplinas e competências num único {@code JOIN FETCH} — falha com
     * {@code MultipleBagFetchException}: o Hibernate recusa buscar duas
     * coleções do tipo {@code List} ao mesmo tempo, porque o produto cartesiano
     * das duas torna impossível saber quantos elementos cada uma tem de fato.
     *
     * <p>As competências vêm numa segunda consulta, em
     * {@code DisciplinaRepository.findComCompetenciasPorBlocos}. Duas consultas
     * de custo fixo, não N+1 — o número não cresce com o tamanho do curso. A
     * alternativa seria trocar uma das coleções por {@code Set}, o que resolve
     * a exceção mas descarta a ordem do plano de ensino.
     *
     * <p>O {@code DISTINCT} elimina a repetição da linha do bloco causada pelo
     * join com as disciplinas.
     */
    @Query("""
            SELECT DISTINCT b FROM Bloco b
            LEFT JOIN FETCH b.disciplinas
            ORDER BY b.numero ASC
            """)
    List<Bloco> findBlocosComDisciplinas();
}
