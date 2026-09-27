package com.andre.infnethub.boletim.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.andre.infnethub.boletim.dto.BoletimDTO;
import com.andre.infnethub.boletim.dto.CompetenciaAvaliadaDTO;
import com.andre.infnethub.boletim.dto.ConceitoRequestDTO;
import com.andre.infnethub.boletim.service.AvaliacaoService;
import com.andre.infnethub.boletim.service.BoletimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * O boletim de um aluno.
 *
 * <p>Mesmo prefixo {@code /api/v1} do infnethub-core, de propósito: através do
 * gateway os dois serviços aparecem como uma API só, e um versionamento
 * diferente por serviço vazaria a divisão interna para o cliente — que não tem
 * por que saber quantos processos atendem à sua requisição.
 */
@RestController
@RequestMapping("/api/v1/boletim")
@RequiredArgsConstructor
public class BoletimController {

    private final BoletimService boletimService;
    private final AvaliacaoService avaliacaoService;

    /** Boletim completo: blocos, disciplinas, competências, situações e resumo. */
    /**
     * O aluno vê o próprio boletim; professor, secretaria e coordenação veem o
     * de qualquer um. Trocar o número na URL deixou de ser suficiente.
     */
    @GetMapping("/{alunoId}")
    @PreAuthorize("@acesso.aoAluno(#alunoId)")
    public ResponseEntity<BoletimDTO> buscar(@PathVariable Long alunoId) {
        return ResponseEntity.ok(boletimService.montarBoletim(alunoId));
    }

    /**
     * Lança ou corrige o conceito de uma competência.
     *
     * <p>PUT, e não POST: a operação é idempotente — enviar o mesmo conceito
     * duas vezes deixa o sistema no mesmo estado. O recurso é identificado pelo
     * par matrícula/competência, que já existe antes da chamada; o que muda é o
     * resultado atribuído a ele.
     */
    @PutMapping("/matriculas/{matriculaId}/competencias/{competenciaId}")
    public ResponseEntity<CompetenciaAvaliadaDTO> registrarConceito(
            @PathVariable Long matriculaId,
            @PathVariable Long competenciaId,
            @Valid @RequestBody ConceitoRequestDTO dto) {
        return ResponseEntity.ok(
                avaliacaoService.registrarConceito(matriculaId, competenciaId, dto.conceito()));
    }
}
