package com.andre.infnethub.seguranca;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 401 e 403 no mesmo formato JSON do resto da API.
 *
 * <p>Sem isto, o Spring Security responde com corpo vazio: o cliente recebe um
 * status e nada mais, enquanto todos os outros erros do sistema trazem
 * {@code timestamp}, {@code status}, {@code error} e {@code message}. Um
 * front-end que sabe ler o erro da API passaria a ter um caso especial só para
 * a falha de autenticação — justamente a que mais precisa ser explicada a quem
 * está usando.
 */
final class ErrosDeSeguranca {

    private ErrosDeSeguranca() {
    }

    static AuthenticationEntryPoint naoAutenticado() {
        return (requisicao, resposta, excecao) -> escrever(resposta, HttpStatus.UNAUTHORIZED,
                "Não autenticado",
                "Esta requisição exige um token válido. Entre com sua conta Infnet.");
    }

    static AccessDeniedHandler semPermissao() {
        return (requisicao, resposta, excecao) -> escrever(resposta, HttpStatus.FORBIDDEN,
                "Sem permissão",
                "Sua conta não tem permissão para esta operação.");
    }

    private static void escrever(HttpServletResponse resposta, HttpStatus status,
                                 String erro, String mensagem) throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write("""
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s"}"""
                .formatted(LocalDateTime.now(), status.value(), erro, mensagem));
    }
}
