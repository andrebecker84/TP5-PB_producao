package com.andre.infnethub.gateway.erro;

// Boot 4 reorganizou as autoconfigurações em módulos por tecnologia: o que
// estava em org.springframework.boot.web.reactive.error passou para
// org.springframework.boot.webflux.error.
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O contrato de erro em JSON também vale para o que o gateway responde sozinho.
 *
 * <p>Nem toda falha chega a um serviço. Uma rota que não casa com nenhum
 * predicado, ou um destino sem instância registrada no Eureka, é respondida
 * pelo próprio gateway — e a resposta padrão do WebFlux tem outro formato:
 * traz {@code path} e {@code requestId}, e não traz {@code message}. Para o
 * cliente, o mesmo erro passava a ter dois formatos conforme o lugar onde
 * nascia, que é justamente o que o contrato existe para evitar.
 *
 * <p>O campo {@code path} sai de propósito. Devolvê-lo faz o servidor ecoar
 * texto arbitrário vindo do cliente, ponto de partida de XSS refletido caso
 * algum consumidor renderize a mensagem como HTML — quem enviou a requisição
 * já sabe qual endereço pediu.
 */
@Component
public class AtributosDeErroDoGateway extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Map<String, Object> padrao = super.getErrorAttributes(request, options);
        HttpStatus status = resolverStatus(padrao.get("status"));

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now().toString());
        corpo.put("status", status.value());
        corpo.put("error", status.getReasonPhrase());
        corpo.put("message", mensagem(status));
        return corpo;
    }

    private HttpStatus resolverStatus(Object codigo) {
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
     * <p>Um erro respondido pelo gateway não tem contexto de domínio para
     * oferecer: ele não chegou a ser processado por nenhum serviço. Detalhar a
     * causa exporia a topologia interna a quem apenas enviou uma requisição — e
     * este é justamente o caminho de quem sonda a API em busca de sua estrutura.
     *
     * <p>O 503 é o caso que mais interessa ao aluno que lê este código: é o que
     * aparece quando um serviço não está registrado no Eureka.
     */
    private String mensagem(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "O endereço solicitado não existe nesta API.";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "O serviço responsável por este endereço está indisponível no momento. Tente novamente em instantes.";
        }
        if (status.is4xxClientError()) {
            return "A requisição não pôde ser processada. Verifique o endereço, o método e os dados enviados.";
        }
        return "Ocorreu um erro inesperado ao processar a requisição.";
    }
}
