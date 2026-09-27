package com.andre.infnethub.boletim.repository;

import com.andre.infnethub.boletim.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    List<Avaliacao> findByMatriculaId(Long matriculaId);

    Optional<Avaliacao> findByMatriculaIdAndCompetenciaId(Long matriculaId, Long competenciaId);

    /**
     * Conceitos já lançados do aluno, em ordem cronológica de curso.
     *
     * <p>Alimenta o perfil de conceitos do painel de desempenho. Filtra os nulos
     * na consulta, e não em memória: competência em avaliação não entra no
     * cálculo do índice, e trazê-la para descartar depois seria tráfego à toa.
     */
    @Query("""
            SELECT a.conceito FROM Avaliacao a
            JOIN a.matricula m
            JOIN m.disciplina d
            JOIN d.bloco b
            WHERE m.alunoId = :alunoId AND a.conceito IS NOT NULL
            ORDER BY b.numero ASC, d.id ASC, a.id ASC
            """)
    List<com.andre.infnethub.boletim.model.Conceito> findConceitosLancados(Long alunoId);

    /**
     * Quantas competências do aluno ainda estão em avaliação.
     *
     * <p>É a pergunta que decide a saga de expurgo: enquanto houver competência
     * sem conceito, o aluno está cursando, e a instituição precisa manter o
     * registro escolar identificado. Só depois de tudo lançado a identidade
     * pode ser desvinculada do histórico.
     *
     * <p>Não existe coluna de "situação" a consultar: a situação de uma
     * disciplina é apurada a partir dos conceitos, pelas regras de aprovação. O
     * conceito nulo é o sinal de que a apuração ainda não pode ser feita.
     */
    @Query("""
            SELECT COUNT(a) FROM Avaliacao a
            JOIN a.matricula m
            WHERE m.alunoId = :alunoId AND a.conceito IS NULL
            """)
    long contarCompetenciasEmAberto(Long alunoId);
}
