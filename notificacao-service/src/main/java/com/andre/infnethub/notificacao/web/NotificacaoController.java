package com.andre.infnethub.notificacao.web;

import com.andre.infnethub.notificacao.aovivo.ConexoesAoVivo;
import com.andre.infnethub.notificacao.dto.NotificacaoDTO;
import com.andre.infnethub.notificacao.service.CentralDeNotificacoes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * As notificações de quem está logado.
 *
 * <p>Nenhum endpoint recebe o id do destinatário no caminho: é sempre "as
 * minhas". Não existe URL para ler as notificações de outra pessoa, nem por
 * engano.
 *
 * <p>Não há endpoint para <em>criar</em> notificação. Elas nascem de eventos, e
 * só de eventos — um serviço que quisesse avisar alguém chamando esta API
 * reintroduziria o acoplamento síncrono que a arquitetura removeu.
 */
@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final CentralDeNotificacoes central;
    private final ConexoesAoVivo conexoes;

    @GetMapping
    public List<NotificacaoDTO> minhas(@UsuarioAtual Long usuarioId,
                                       @RequestParam(defaultValue = "" + CentralDeNotificacoes.LIMITE_PADRAO) int limite) {
        return central.recentes(usuarioId, limite);
    }

    @GetMapping("/nao-lidas")
    public Map<String, Long> naoLidas(@UsuarioAtual Long usuarioId) {
        return Map.of("total", central.naoLidas(usuarioId));
    }

    /**
     * Conexão ao vivo (Server-Sent Events). Fica aberta; cada notificação nova
     * chega como um evento {@code notificacao} com o mesmo corpo da listagem.
     */
    @GetMapping(path = "/ao-vivo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aoVivo(@UsuarioAtual Long usuarioId) {
        return conexoes.conectar(usuarioId);
    }

    @PostMapping("/lidas")
    public Map<String, Integer> marcarComoLidas(@UsuarioAtual Long usuarioId, @Valid @RequestBody SelecaoDTO selecao) {
        return Map.of("atualizadas", central.marcarComoLidas(usuarioId, selecao.ids()));
    }

    /** O contrário de {@code /lidas}: só as do próprio usuário, as demais são ignoradas. */
    @PostMapping("/nao-lidas")
    public Map<String, Integer> marcarComoNaoLidas(@UsuarioAtual Long usuarioId, @Valid @RequestBody SelecaoDTO selecao) {
        return Map.of("atualizadas", central.marcarComoNaoLidas(usuarioId, selecao.ids()));
    }

    @PostMapping("/lidas/todas")
    public Map<String, Integer> marcarTodasComoLidas(@UsuarioAtual Long usuarioId) {
        return Map.of("atualizadas", central.marcarTodasComoLidas(usuarioId));
    }

    /**
     * Exclusão em lote por POST, e não DELETE com corpo: o HTTP não dá
     * semântica a corpo em DELETE, e proxies e clientes podem descartá-lo.
     */
    @PostMapping("/excluir")
    public ResponseEntity<Map<String, Integer>> excluir(@UsuarioAtual Long usuarioId, @Valid @RequestBody SelecaoDTO selecao) {
        return ResponseEntity.ok(Map.of("excluidas", central.excluir(usuarioId, selecao.ids())));
    }

    public record SelecaoDTO(@NotEmpty @Size(max = CentralDeNotificacoes.LIMITE_MAXIMO) List<Long> ids) {
    }
}
