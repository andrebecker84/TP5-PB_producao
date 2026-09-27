package com.andre.infnethub.controller;

import com.andre.infnethub.dto.historico.*;
import com.andre.infnethub.service.HistoricoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * Consulta do histórico de alterações — a face de leitura da auditoria.
 *
 * <p>Só expõe verbos GET, e de propósito: o histórico é escrito pelo Envers como
 * efeito das operações do domínio. Um endpoint capaz de criar ou alterar
 * revisões destruiria a garantia que torna o registro confiável.
 */
@RestController
@RequestMapping("/api/v1/historico")
@RequiredArgsConstructor
public class HistoricoController {

    /** Teto de página: uma entidade muito editada pode ter centenas de revisões. */
    private static final int TAMANHO_MAXIMO = 100;

    private final HistoricoService historicoService;

    @GetMapping("/usuarios/{id}")
    public PaginaDTO<RevisaoDTO<UsuarioSnapshot>> historicoUsuario(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return historicoService.historicoUsuario(id, paginacao(pagina, tamanho));
    }

    @GetMapping("/posts/{id}")
    public PaginaDTO<RevisaoDTO<PostSnapshot>> historicoPost(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return historicoService.historicoPost(id, paginacao(pagina, tamanho));
    }

    @GetMapping("/vagas/{id}")
    public PaginaDTO<RevisaoDTO<VagaSnapshot>> historicoVaga(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return historicoService.historicoVaga(id, paginacao(pagina, tamanho));
    }

    @GetMapping("/comentarios/{id}")
    public PaginaDTO<RevisaoDTO<ComentarioSnapshot>> historicoComentario(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return historicoService.historicoComentario(id, paginacao(pagina, tamanho));
    }

    // ── Revisão específica ─────────────────────────────────────────────────

    @GetMapping("/usuarios/{id}/revisoes/{revisao}")
    public RevisaoDTO<UsuarioSnapshot> revisaoUsuario(@PathVariable Long id, @PathVariable Integer revisao) {
        return historicoService.revisaoUsuario(id, revisao);
    }

    @GetMapping("/posts/{id}/revisoes/{revisao}")
    public RevisaoDTO<PostSnapshot> revisaoPost(@PathVariable Long id, @PathVariable Integer revisao) {
        return historicoService.revisaoPost(id, revisao);
    }

    @GetMapping("/vagas/{id}/revisoes/{revisao}")
    public RevisaoDTO<VagaSnapshot> revisaoVaga(@PathVariable Long id, @PathVariable Integer revisao) {
        return historicoService.revisaoVaga(id, revisao);
    }

    @GetMapping("/comentarios/{id}/revisoes/{revisao}")
    public RevisaoDTO<ComentarioSnapshot> revisaoComentario(@PathVariable Long id, @PathVariable Integer revisao) {
        return historicoService.revisaoComentario(id, revisao);
    }

    /** Último estado registrado de um post, mesmo que ele já tenha sido excluído. */
    @GetMapping("/posts/{id}/ultima-revisao")
    public RevisaoDTO<PostSnapshot> ultimaRevisaoPost(@PathVariable Long id) {
        return historicoService.ultimaRevisaoPost(id);
    }

    /**
     * Linha do tempo global: toda alteração registrada, da mais recente para a
     * mais antiga. Com {@code ?autor=}, restringe ao que aquele autor alterou —
     * busca parcial por nome ou e-mail.
     */
    @GetMapping("/revisoes")
    public PaginaDTO<RevisaoResumoDTO> linhaDoTempo(
            @RequestParam(required = false) String autor,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        Pageable paginacao = paginacao(pagina, tamanho);
        return (autor == null || autor.isBlank())
                ? historicoService.linhaDoTempo(paginacao)
                : historicoService.revisoesPorAutor(autor, paginacao);
    }

    private Pageable paginacao(int pagina, int tamanho) {
        return PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, TAMANHO_MAXIMO));
    }
}
