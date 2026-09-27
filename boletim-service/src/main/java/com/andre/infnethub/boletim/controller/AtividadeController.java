package com.andre.infnethub.boletim.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.andre.infnethub.boletim.dto.AtividadeDTO;
import com.andre.infnethub.boletim.dto.AtividadeRequestDTO;
import com.andre.infnethub.boletim.dto.CargaCategoriaDTO;
import com.andre.infnethub.boletim.service.AtividadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Extensão, eletivas, estágio e atividades complementares.
 *
 * <p>O aluno está no caminho ({@code /alunos/{alunoId}/atividades}) porque a
 * atividade só existe em relação a ele — não há atividade sem dono, e um
 * {@code /atividades/{id}} solto permitiria alcançar a atividade de qualquer
 * aluno conhecendo apenas o id.
 */
@RestController
@RequestMapping("/api/v1/alunos/{alunoId}/atividades")
@RequiredArgsConstructor
public class AtividadeController {

    private final AtividadeService atividadeService;

    @GetMapping
    @PreAuthorize("@acesso.aoAluno(#alunoId)")
    public ResponseEntity<List<AtividadeDTO>> listar(
            @PathVariable Long alunoId,
            @RequestParam(required = false) String categoria) {
        return ResponseEntity.ok(categoria == null
                ? atividadeService.listarPorAluno(alunoId)
                : atividadeService.listarPorCategoria(alunoId, categoria));
    }

    /** Progresso de integralização por categoria. */
    @GetMapping("/carga-horaria")
    @PreAuthorize("@acesso.aoAluno(#alunoId)")
    public ResponseEntity<List<CargaCategoriaDTO>> cargaHoraria(@PathVariable Long alunoId) {
        return ResponseEntity.ok(atividadeService.apurarCargaHoraria(alunoId));
    }

    @PostMapping
    @PreAuthorize("@acesso.aoAluno(#alunoId)")
    public ResponseEntity<AtividadeDTO> registrar(
            @PathVariable Long alunoId,
            @Valid @RequestBody AtividadeRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(atividadeService.registrar(alunoId, dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@acesso.aoAluno(#alunoId)")
    public ResponseEntity<AtividadeDTO> atualizar(
            @PathVariable Long alunoId,
            @PathVariable Long id,
            @Valid @RequestBody AtividadeRequestDTO dto) {
        return ResponseEntity.ok(atividadeService.atualizar(alunoId, id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@acesso.aoAluno(#alunoId)")
    public ResponseEntity<Void> remover(@PathVariable Long alunoId, @PathVariable Long id) {
        atividadeService.remover(alunoId, id);
        return ResponseEntity.noContent().build();
    }
}
