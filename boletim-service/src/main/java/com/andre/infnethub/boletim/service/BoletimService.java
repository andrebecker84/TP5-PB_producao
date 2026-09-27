package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.dto.BoletimDTO;

/** Porta de serviço — Bounded Context: Desempenho Acadêmico (SRP + DIP). */
public interface BoletimService {

    /** O boletim completo do aluno: blocos, disciplinas, competências e resumo. */
    BoletimDTO montarBoletim(Long alunoId);
}
