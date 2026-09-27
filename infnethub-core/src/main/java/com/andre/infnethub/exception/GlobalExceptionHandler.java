package com.andre.infnethub.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.andre.infnethub.expurgo.SagaDeExpurgo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tradutor único de exceção para resposta HTTP.
 *
 * <p>Trata dois grupos distintos. O primeiro são as exceções do domínio, que
 * nascem depois de a requisição alcançar um controller. O segundo são as que o
 * Spring lança <em>antes</em> disso — rota inexistente, verbo incorreto, JSON
 * malformado, identificador de tipo errado. Sem o segundo grupo, essas falhas
 * caem no tratamento padrão e o servidor devolve HTML, quebrando o contrato de
 * uma API que se anuncia como JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(404, "Recurso não encontrado", ex.getMessage()));
    }

    @ExceptionHandler(ConflitoDeDadosException.class)
    public ResponseEntity<Map<String, Object>> handleConflito(ConflitoDeDadosException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo(409, "Conflito com os dados existentes", ex.getMessage()));
    }

    /**
     * Pedir a remoção de quem já está saindo.
     *
     * <p>É 409, e não 400: o pedido está bem formado e seria válido em outro
     * momento — o que impede é o estado atual do recurso. Repetir a mesma
     * requisição depois que a saga anterior encerrar funciona.
     */
    @ExceptionHandler(SagaDeExpurgo.ExpurgoEmAndamentoException.class)
    public ResponseEntity<Map<String, Object>> handleExpurgoEmAndamento(SagaDeExpurgo.ExpurgoEmAndamentoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(corpo(409, "Remoção já em andamento", ex.getMessage()));
    }

    /**
     * Rede de segurança para as constraints do banco.
     *
     * <p>Os serviços checam antes de gravar, mas a checagem não é atômica: duas
     * requisições simultâneas passam juntas pela verificação e só a constraint
     * as separa. Quem perde a corrida chega aqui — e sem este tratamento levaria
     * um 500, transformando uma regra de negócio funcionando em erro de servidor.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegridade(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo(409,
                "Conflito com os dados existentes",
                traduzir(ex)));
    }

    /**
     * Perda de corrida no bloqueio otimista ({@code @Version}).
     *
     * <p>Outra transação gravou por cima entre a leitura e a escrita. Não é falha
     * do servidor: é a proteção contra atualização perdida funcionando. O cliente
     * deve reler e tentar de novo.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcorrencia(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo(409,
                "Conflito de concorrência",
                "O registro foi alterado por outra operação enquanto esta era processada. Recarregue os dados e tente novamente."));
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
    // Chegam aqui vindas do DispatcherServlet. Antes deste bloco, todas
    // terminavam na Whitelabel Error Page — uma página HTML devolvida por uma
    // API JSON, que o cliente não consegue interpretar.

    /**
     * Rota inexistente.
     *
     * <p>Duas exceções para o mesmo sintoma por razão histórica: até o Spring 6.0
     * uma URL sem mapeamento gerava {@link NoHandlerFoundException}; a partir do
     * 6.1 o tratamento de recurso estático passou a lançar
     * {@link NoResourceFoundException}. Qual das duas ocorre depende de
     * configuração, então ambas são tratadas.
     *
     * <p>A resposta não repete o caminho solicitado. Devolver a URL faz o
     * servidor ecoar texto arbitrário do cliente, e ecoar entrada não validada é
     * o ponto de partida de XSS refletido — inofensivo em JSON puro, perigoso se
     * algum consumidor renderizar a mensagem como HTML.
     */
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<Map<String, Object>> handleRotaInexistente(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo(404,
                "Rota não encontrada",
                "O endereço solicitado não existe nesta API. Consulte a documentação dos endpoints disponíveis."));
    }

    /**
     * Verbo não suportado pela rota — por exemplo, POST onde só existe GET.
     *
     * <p>O cabeçalho {@code Allow} acompanha a resposta porque a especificação
     * HTTP o exige no 405, e é ele que informa ao cliente quais verbos tentar.
     */
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

    /**
     * Parâmetro de tipo incompatível — {@code /posts/abc} onde se espera um id
     * numérico. É erro do cliente (400), não falha do servidor.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        String tipo = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "esperado";
        return ResponseEntity.badRequest().body(corpo(400,
                "Parâmetro inválido",
                "O valor informado para '%s' não é um %s válido.".formatted(ex.getName(), tipo)));
    }

    /** Parâmetro obrigatório ausente na query string. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParametroAusente(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(corpo(400,
                "Parâmetro obrigatório ausente",
                "O parâmetro '%s' é obrigatório.".formatted(ex.getParameterName())));
    }

    /**
     * Corpo ilegível — JSON malformado, vazio ou com tipo incompatível.
     *
     * <p>A mensagem original do Jackson traz nomes de classe e posição no fluxo
     * de bytes. É detalhe interno, e revelar a estrutura de pacotes a quem sonda
     * a API não ajuda ninguém a corrigir a requisição.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(corpo(400,
                "Corpo da requisição inválido",
                "Não foi possível interpretar o corpo enviado. Verifique se é um JSON válido e se os tipos dos campos estão corretos."));
    }

    /**
     * Rede de segurança final.
     *
     * <p>Qualquer exceção não prevista viraria um 500 em HTML. Aqui ela vira 500
     * em JSON, com mensagem genérica para o cliente e o rastreamento completo no
     * log do servidor — que é onde ele serve para diagnóstico sem entregar a
     * estrutura interna da aplicação a quem fez a requisição.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleInesperado(Exception ex, HttpServletRequest req) {
        log.error("Erro não tratado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo(500,
                "Erro interno",
                "Ocorreu um erro inesperado ao processar a requisição."));
    }

    /**
     * Converte a violação de constraint em uma mensagem de domínio.
     *
     * <p>A mensagem crua do banco expõe nomes de tabela e de constraint — ruído
     * para o cliente e informação a mais para quem estiver sondando a API.
     */
    private String traduzir(DataIntegrityViolationException ex) {
        String causa = ex.getMostSpecificCause().getMessage();
        if (causa == null) {
            return "A operação viola uma restrição de integridade dos dados.";
        }
        String normalizado = causa.toLowerCase();
        if (normalizado.contains("uk_usuarios_email")) {
            return "Já existe um usuário cadastrado com este e-mail.";
        }
        if (normalizado.contains("uk_curtidas_post_usuario")) {
            return "Este usuário já curtiu esta publicação.";
        }
        if (normalizado.contains("fk_")) {
            return "O registro não pode ser removido ou alterado porque está referenciado por outros dados.";
        }
        return "A operação viola uma restrição de integridade dos dados.";
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
