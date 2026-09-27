package com.andre.infnethub.notificacao.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tradutor único de exceção para resposta HTTP.
 *
 * <p>Mesmo contrato de erro do infnethub-core e do boletim-service, e de
 * propósito: o front-end consome os três serviços através do mesmo gateway, e um cliente não deveria
 * precisar saber de qual deles veio a falha para conseguir ler a resposta. Dois
 * formatos de erro numa arquitetura distribuída significam dois caminhos de
 * tratamento no cliente para o mesmo problema.
 *
 * <p>A classe é duplicada em vez de compartilhada por um módulo comum. É uma
 * escolha: uma biblioteca de código compartilhada entre serviços recria o
 * acoplamento que a separação existe para desfazer — mudar o formato de erro
 * passaria a exigir reimplantar os dois ao mesmo tempo. Duplicar cem linhas
 * estáveis custa menos que amarrar de novo os ciclos de vida.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Falhas com status já decidido por quem as lançou — hoje, a requisição
     * sem identificação do usuário (401). Sem este tratamento, a rede de
     * segurança final as transformaria em 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(corpo(status.value(), status.getReasonPhrase(), ex.getReason()));
    }

    /**
     * Autorização negada por regra de método ({@code @PreAuthorize}).
     *
     * <p>Precisa estar aqui, e não só na configuração de segurança: a exceção é
     * lançada quando a requisição já entrou no controller, e este tratador a
     * alcança primeiro. Sem ele, "você não pode ver o boletim de outra pessoa"
     * chegaria ao cliente como erro interno do servidor — escondendo uma regra
     * funcionando como se fosse defeito.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAcessoNegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(corpo(403,
                "Sem permissão",
                "Sua conta não tem permissão para esta operação."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 400);
        body.put("error", "Dados inválidos");
        body.put("campos", campos);

        return ResponseEntity.badRequest().body(body);
    }

    // ── Falhas anteriores ao controller ───────────────────────────────────

    /**
     * Rota inexistente. Duas exceções para o mesmo sintoma por razão histórica:
     * até o Spring 6.0 uma URL sem mapeamento gerava {@link NoHandlerFoundException};
     * a partir do 6.1 o tratamento de recurso estático passou a lançar
     * {@link NoResourceFoundException}.
     *
     * <p>A resposta não repete o caminho solicitado: ecoar entrada não validada
     * é o ponto de partida de XSS refletido caso algum consumidor renderize a
     * mensagem como HTML.
     */
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<Map<String, Object>> handleRotaInexistente(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(404,
                "Rota não encontrada",
                "O endereço solicitado não existe nesta API. Consulte a documentação dos endpoints disponíveis."));
    }

    /** Verbo não suportado. O cabeçalho {@code Allow} acompanha por exigência do HTTP. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex) {
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null) {
            resposta.allow(ex.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return resposta.body(corpo(405,
                "Método não permitido",
                "O método %s não é aceito neste endereço.".formatted(ex.getMethod())));
    }

    /** Parâmetro de tipo incompatível — {@code ?limite=abc} onde se espera um número. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        String tipo = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "esperado";
        return ResponseEntity.badRequest().body(corpo(400,
                "Parâmetro inválido",
                "O valor informado para '%s' não é um %s válido.".formatted(ex.getName(), tipo)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParametroAusente(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(corpo(400,
                "Parâmetro obrigatório ausente",
                "O parâmetro '%s' é obrigatório.".formatted(ex.getParameterName())));
    }

    /**
     * Corpo ilegível — JSON malformado ou com tipo incompatível. A mensagem do
     * Jackson traz nomes de classe e posição no fluxo de bytes: detalhe interno
     * que não ajuda o cliente a corrigir a requisição.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(corpo(400,
                "Corpo da requisição inválido",
                "Não foi possível interpretar o corpo enviado. Verifique se é um JSON válido e se os tipos dos campos estão corretos."));
    }

    /**
     * Rede de segurança final: qualquer exceção não prevista vira 500 em JSON,
     * com mensagem genérica para o cliente e rastreamento completo no log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInesperado(Exception ex, HttpServletRequest req) {
        log.error("Erro não tratado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo(500,
                "Erro interno",
                "Ocorreu um erro inesperado ao processar a requisição."));
    }

    private Map<String, Object> corpo(int status, String erro, String mensagem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", erro);
        body.put("message", mensagem);
        return body;
    }
}
