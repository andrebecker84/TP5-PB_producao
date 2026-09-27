package com.andre.infnethub.controller;

import com.andre.infnethub.dto.CurtidaResponseDTO;
import com.andre.infnethub.seguranca.Solicitante;
import com.andre.infnethub.service.CurtidaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/posts/{postId}/curtidas")
@RequiredArgsConstructor
public class CurtidaController {

    private final CurtidaService curtidaService;

    @GetMapping
    public ResponseEntity<List<CurtidaResponseDTO>> listar(@PathVariable Long postId) {
        return ResponseEntity.ok(curtidaService.listarPorPost(postId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> curtirOuDescurtir(
            @PathVariable Long postId,
            @AuthenticationPrincipal Jwt token) {
        CurtidaService.ResultadoCurtida resultado = curtidaService.alternar(postId, Solicitante.de(token).id());
        return ResponseEntity.ok(Map.of("curtido", resultado.curtido(), "total", resultado.total()));
    }
}
