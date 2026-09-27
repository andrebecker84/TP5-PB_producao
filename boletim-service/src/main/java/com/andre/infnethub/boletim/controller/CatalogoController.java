package com.andre.infnethub.boletim.controller;

import com.andre.infnethub.boletim.dto.CatalogoBlocoDTO;
import com.andre.infnethub.boletim.dto.ConceitoDTO;
import com.andre.infnethub.boletim.service.CatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A estrutura do curso, independente de aluno.
 *
 * <p>O boletim parte das matrículas e por isso só mostra o que o aluno cursou.
 * O mapa do curso precisa também dos blocos ainda não iniciados — é para isso
 * que este recurso existe.
 */
@RestController
@RequestMapping("/api/v1/catalogo")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping("/blocos")
    public ResponseEntity<List<CatalogoBlocoDTO>> listarBlocos() {
        return ResponseEntity.ok(catalogoService.listarBlocos());
    }

    @GetMapping("/blocos/{numero}")
    public ResponseEntity<CatalogoBlocoDTO> buscarBloco(@PathVariable Integer numero) {
        return ResponseEntity.ok(catalogoService.buscarBloco(numero));
    }

    /**
     * A escala de conceitos, do pior para o melhor, com nome e regra de cada um.
     *
     * <p>É o que permite à legenda do boletim ser montada a partir do serviço em
     * vez de manter sua própria cópia dos textos.
     */
    @GetMapping("/conceitos")
    public ResponseEntity<List<ConceitoDTO>> listarConceitos() {
        return ResponseEntity.ok(catalogoService.listarConceitos());
    }
}
