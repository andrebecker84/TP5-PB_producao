package com.andre.infnethub.repository;

import com.andre.infnethub.model.TipoVaga;
import com.andre.infnethub.model.Vaga;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VagaRepository extends JpaRepository<Vaga, Long>,
        RevisionRepository<Vaga, Long, Integer> {

    @Query("SELECT v FROM Vaga v JOIN FETCH v.criador WHERE v.ativo = true ORDER BY v.criadoEm DESC")
    List<Vaga> findAllAtivasWithCriador();

    @Query("SELECT v FROM Vaga v JOIN FETCH v.criador WHERE v.id = :id")
    Optional<Vaga> findByIdWithCriador(Long id);

    @Query("SELECT v FROM Vaga v JOIN FETCH v.criador WHERE v.tipo = :tipo AND v.ativo = true ORDER BY v.criadoEm DESC")
    List<Vaga> findByTipoWithCriador(TipoVaga tipo);

    /** Listagem paginada de vagas ativas — apoiada em idx_vagas_ativo_criado_em. */
    @EntityGraph(attributePaths = "criador")
    Page<Vaga> findByAtivoTrueOrderByCriadoEmDesc(Pageable pageable);
}
