package com.andre.infnethub.boletim.repository;

import com.andre.infnethub.boletim.model.Competencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetenciaRepository extends JpaRepository<Competencia, Long> {

    List<Competencia> findByDisciplinaIdOrderByOrdemAsc(Long disciplinaId);
}
