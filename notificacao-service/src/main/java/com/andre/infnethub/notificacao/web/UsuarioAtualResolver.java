package com.andre.infnethub.notificacao.web;

import com.andre.infnethub.notificacao.seguranca.PapeisDoKeycloak;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Resolve {@link UsuarioAtual} a partir do token.
 *
 * <p>Quando este resolver foi escrito, a identidade vinha do cabeçalho
 * {@code X-Usuario-Id} — e o comentário dizia que, ao chegar a autenticação,
 * bastaria mudar aqui. É o que aconteceu: nenhum controller mudou.
 *
 * <p>O valor vem do claim {@code usuario_id}, posto pelo Keycloak a partir do
 * cadastro do usuário. Não há mais como pedir as notificações de outra pessoa:
 * o número não é mais informado por quem chama.
 *
 * <p>A conexão ao vivo (SSE) funciona pelo mesmo caminho, e é por isso que o
 * front-end fala com o gateway: o {@code EventSource} do navegador não envia
 * cabeçalho de autorização, mas envia o cookie de sessão — e é o gateway que
 * troca esse cookie pelo token antes de encaminhar.
 */
@Component
class UsuarioAtualResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return parametro.hasParameterAnnotation(UsuarioAtual.class)
                && Long.class.equals(parametro.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parametro, ModelAndViewContainer mav,
                                NativeWebRequest requisicao, WebDataBinderFactory binder) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Usuário não autenticado.");
        }

        Long usuarioId = PapeisDoKeycloak.usuarioIdDe(token.getToken());
        if (usuarioId == null) {
            // Token legítimo, de uma conta sem vínculo com o cadastro do Infnet
            // Hub. Não é falha de autenticação: é falta de autorização para
            // este recurso, que é sempre de alguém.
            throw new ResponseStatusException(FORBIDDEN,
                    "Esta conta não está vinculada a um usuário do Infnet Hub.");
        }
        return usuarioId;
    }
}
