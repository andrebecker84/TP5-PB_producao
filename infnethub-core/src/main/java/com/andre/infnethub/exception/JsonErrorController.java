package com.andre.infnethub.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
// Spring Boot 4 reorganizou as autoconfigurações em módulos por tecnologia: o
// que estava em org.springframework.boot.web.servlet.error passou para
// org.springframework.boot.webmvc.error, no artefato spring-boot-webmvc.
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Última linha de defesa do contrato JSON.
 *
 * <p>O {@link GlobalExceptionHandler} cobre o que passa pelo DispatcherServlet,
 * que é a origem da maioria dos erros. Mas nem tudo passa por lá: uma falha
 * dentro de um filtro — o {@code ContextoAuditoriaFilter}, por exemplo — ocorre
 * antes de o Spring MVC assumir a requisição, e por isso nenhum
 * {@code @ExceptionHandler} a alcança. O contêiner encaminha esses casos para
 * {@code /error}.
 *
 * <p>Com a Whitelabel desativada e sem este controller, tais requisições
 * devolveriam corpo vazio: o cliente receberia um status correto acompanhado de
 * nada, o que é pior que a página HTML — pelo menos aquela dizia alguma coisa.
 * Aqui a resposta mantém o mesmo formato das demais, de modo que o cliente possa
 * tratar erro de forma uniforme, sem precisar saber em que camada ele nasceu.
 */
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> tratar(HttpServletRequest request) {
        HttpStatus status = resolverStatus(request);

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now().toString());
        corpo.put("status", status.value());
        corpo.put("error", status.getReasonPhrase());
        corpo.put("message", mensagem(status));

        return ResponseEntity.status(status).body(corpo);
    }

    private HttpStatus resolverStatus(HttpServletRequest request) {
        Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (codigo == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(Integer.parseInt(codigo.toString()));
        } catch (IllegalArgumentException e) {
            // Status fora da enumeração conhecida: preserva-se a família do erro
            // em vez de propagar um código que o cliente não saberia interpretar.
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Mensagens genéricas de propósito.
     *
     * <p>Um erro que chega até aqui já não tem contexto de domínio para oferecer.
     * Detalhar a causa exigiria expor estado interno da aplicação a quem apenas
     * enviou uma requisição — e este é justamente o caminho percorrido por quem
     * sonda a API em busca de informação sobre sua estrutura.
     */
    private String mensagem(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "O endereço solicitado não existe nesta API.";
        }
        if (status.is4xxClientError()) {
            return "A requisição não pôde ser processada. Verifique o endereço, o método e os dados enviados.";
        }
        return "Ocorreu um erro inesperado ao processar a requisição.";
    }
}
