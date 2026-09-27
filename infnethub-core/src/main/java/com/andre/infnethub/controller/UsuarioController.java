package com.andre.infnethub.controller;

import com.andre.infnethub.contratos.consulta.SituacaoAcademicaRespostaV1;
import com.andre.infnethub.mensageria.ConsultaAoBoletim;
import com.andre.infnethub.dto.ExpurgoResponseDTO;
import com.andre.infnethub.expurgo.SagaDeExpurgo;
import com.andre.infnethub.dto.UsuarioRequestDTO;
import com.andre.infnethub.dto.UsuarioResponseDTO;
import com.andre.infnethub.model.Papel;
import com.andre.infnethub.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final ConsultaAoBoletim consultaAoBoletim;
    private final SagaDeExpurgo saga;

    /**
     * Lista os usuários, opcionalmente filtrando por papel.
     *
     * <p>O filtro é parâmetro opcional em vez de rota separada: sem ele o
     * contrato do TP1 continua valendo, e o front-end existente não precisa
     * mudar.
     */
    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listarTodos(@RequestParam(required = false) Papel papel) {
        return ResponseEntity.ok(papel == null
                ? usuarioService.listarTodos()
                : usuarioService.listarPorPapel(papel));
    }

    /** Busca por nome ou e-mail — sustenta o campo de busca global do front-end. */
    @GetMapping("/buscar")
    public ResponseEntity<List<UsuarioResponseDTO>> buscar(@RequestParam String termo) {
        return ResponseEntity.ok(usuarioService.buscar(termo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioRequestDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizar(id, dto));
    }

    /**
     * Reenvio do estado atual de todos os usuários para os consumidores de
     * eventos — o caminho de sincronização de um serviço que passou a escutar
     * depois dos cadastros. Ver {@link UsuarioService#reenviarEstadoAtual()}.
     */
    @PostMapping("/eventos/reenvio")
    public ResponseEntity<Map<String, Integer>> reenviarEstadoAtual() {
        return ResponseEntity.accepted().body(Map.of("reenviados", usuarioService.reenviarEstadoAtual()));
    }

    /**
     * O que o boletim sabe sobre este aluno, perguntado na hora.
     *
     * <p>É a tela que a secretaria abre antes de excluir alguém, e a única
     * rota do sistema que espera outro serviço responder — ver
     * {@link com.andre.infnethub.mensageria.ConsultaAoBoletim} para o porquê da
     * exceção e para o que acontece quando o boletim não responde.
     *
     * <p>Responde 200 mesmo quando a consulta falha: o corpo diz
     * {@code "consultado": false}, e é a aplicação que decide o que fazer com
     * isso. Um 503 aqui obrigaria o front-end a tratar um erro para exibir uma
     * informação que ele tem — a de que não foi possível verificar.
     */
    @GetMapping("/{id}/situacao-academica")
    public ResponseEntity<SituacaoAcademicaRespostaV1> situacaoAcademica(@PathVariable Long id) {
        return ResponseEntity.ok(consultaAoBoletim.situacaoDe(id));
    }

    /**
     * Abre a saga de expurgo (LGPD) — e responde 202, não 204.
     *
     * <p>{@code 204 No Content} diria "feito, não há mais nada a dizer". Mas há:
     * a pessoa foi bloqueada, e o que acontece com os dados dela nos outros
     * serviços ainda está em curso. {@code 202 Accepted} é o código para isso, e
     * o corpo traz o processo, com o identificador por onde acompanhá-lo.
     *
     * <p>O caso mais interessante é o que <strong>não</strong> acontece mais:
     * até o TP3, remover quem tinha publicado no feed respondia 409, porque a
     * chave estrangeira do post segurava o autor. A saga termina em
     * anonimização, e o 409 desapareceu com o problema que o causava.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ExpurgoResponseDTO> solicitarExpurgo(@PathVariable Long id) {
        usuarioService.deletar(id);
        return ResponseEntity.accepted().body(
                ExpurgoResponseDTO.de(saga.historicoDe(id).getFirst()));
    }

    /**
     * O histórico de pedidos de remoção desta pessoa, do mais recente ao mais
     * antigo.
     *
     * <p>Lista, e não um único registro: um pedido recusado pode ser refeito
     * depois que a matrícula terminar, e o que aconteceu antes continua sendo
     * parte da resposta a "o que foi feito com os meus dados?".
     */
    @GetMapping("/{id}/expurgo")
    public ResponseEntity<List<ExpurgoResponseDTO>> expurgos(@PathVariable Long id) {
        return ResponseEntity.ok(saga.historicoDe(id).stream().map(ExpurgoResponseDTO::de).toList());
    }
}
