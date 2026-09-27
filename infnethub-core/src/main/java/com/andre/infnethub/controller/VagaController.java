package com.andre.infnethub.controller;

import com.andre.infnethub.dto.VagaRequestDTO;
import com.andre.infnethub.dto.VagaResponseDTO;
import com.andre.infnethub.dto.historico.PaginaDTO;
import com.andre.infnethub.service.VagaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vagas")
@RequiredArgsConstructor
public class VagaController {

    private final VagaService vagaService;

    @GetMapping
    public ResponseEntity<List<VagaResponseDTO>> listarAtivas() {
        return ResponseEntity.ok(vagaService.listarAtivas());
    }

    /** Listagem paginada de vagas ativas. */
    @GetMapping("/paginado")
    public ResponseEntity<PaginaDTO<VagaResponseDTO>> listarAtivasPaginado(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return ResponseEntity.ok(vagaService.listarAtivasPaginado(
                PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, 100))));
    }

    @GetMapping("/tipo/{tipo}")
    public ResponseEntity<List<VagaResponseDTO>> listarPorTipo(@PathVariable String tipo) {
        return ResponseEntity.ok(vagaService.listarPorTipo(tipo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VagaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(vagaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<VagaResponseDTO> criar(@Valid @RequestBody VagaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vagaService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VagaResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody VagaRequestDTO dto) {
        return ResponseEntity.ok(vagaService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        vagaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
