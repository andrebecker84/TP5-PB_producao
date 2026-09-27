package com.andre.infnethub.boletim.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.andre.infnethub.boletim.dto.DesempenhoDTO;
import com.andre.infnethub.boletim.service.DesempenhoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Indicadores derivados dos conceitos — alimenta o painel Meu Desempenho. */
@RestController
@RequestMapping("/api/v1/desempenho")
@RequiredArgsConstructor
public class DesempenhoController {

    private final DesempenhoService desempenhoService;

    @GetMapping("/{alunoId}")
    @PreAuthorize("@acesso.aoAluno(#alunoId)")
    public ResponseEntity<DesempenhoDTO> apurar(@PathVariable Long alunoId) {
        return ResponseEntity.ok(desempenhoService.apurar(alunoId));
    }
}
