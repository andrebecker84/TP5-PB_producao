package com.andre.infnethub.auditoria;

import com.andre.infnethub.seguranca.PapeisDoKeycloak;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Identifica o autor da requisição e o publica no {@link ContextoAuditoria}.
 *
 * <p>Até o TP3 o cliente informava quem estava agindo pelo cabeçalho
 * {@code X-Usuario-Id}, sem verificação: bastava trocar o número para assinar
 * uma alteração em nome de outra pessoa. Com a autenticação do TP4, o autor
 * sai do token validado — e ninguém escolhe o próprio nome na auditoria.
 *
 * <p>Foi exatamente a troca que o comentário anterior deste arquivo previa, e o
 * resto da cadeia de auditoria não mudou: o {@link ContextoAuditoria} continua
 * recebendo um texto pronto.
 *
 * <p>A consulta ao usuário só acontece em métodos de escrita: são os únicos que
 * geram revisão no Envers, e assim nenhuma leitura paga uma consulta a mais.
 */
@Component
public class ContextoAuditoriaFilter extends OncePerRequestFilter {

    private static final String HEADER_ORIGEM = "X-Forwarded-For";
    private static final Set<String> METODOS_DE_ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (METODOS_DE_ESCRITA.contains(request.getMethod())) {
                ContextoAuditoria.definirAutor(resolverAutor());
                ContextoAuditoria.definirOrigem(resolverOrigem(request));
            }
            filterChain.doFilter(request, response);
        } finally {
            // Obrigatório: a thread volta para o pool e atenderia a próxima
            // requisição carregando o autor da anterior.
            ContextoAuditoria.limpar();
        }
    }

    /**
     * O endereço de quem originou a requisição.
     *
     * <p>Com a entrada do API Gateway no TP3, {@code getRemoteAddr()} deixou de
     * responder a essa pergunta: toda requisição passa a chegar do gateway, e o
     * campo passaria a registrar sempre o mesmo IP interno — uma coluna de
     * auditoria que existe, é preenchida, e não distingue nada.
     *
     * <p>{@code X-Forwarded-For} preserva a cadeia, e o primeiro elemento é o
     * cliente original; os seguintes são os proxies que a requisição atravessou.
     * O gateway o acrescenta sozinho, sem configuração.
     *
     * <p>O header é informado pelo cliente e pode ser forjado — enquanto não
     * houver autenticação, ele serve como indício, não como prova. É a mesma
     * ressalva que já vale para o {@code X-Usuario-Id}. Quando o acesso direto
     * às portas internas for fechado, apenas o gateway poderá escrevê-lo.
     */
    private String resolverOrigem(HttpServletRequest request) {
        String encaminhado = request.getHeader(HEADER_ORIGEM);
        if (encaminhado == null || encaminhado.isBlank()) {
            return request.getRemoteAddr();
        }
        return encaminhado.split(",")[0].trim();
    }

    /**
     * Quem está agindo, segundo o token.
     *
     * <p>Grava nome e e-mail por extenso — como antes —, e não a referência ao
     * usuário: o registro precisa continuar respondendo "quem fez isto" mesmo
     * depois de a pessoa ser removida do sistema.
     *
     * <p>Os dados saem dos claims, sem consultar o banco: eles já foram
     * verificados pelo Keycloak, e uma consulta por requisição de escrita seria
     * paga para confirmar o que a assinatura do token já garante. O id do
     * usuário entra junto porque é ele que liga o registro ao cadastro do core.
     */
    private String resolverAutor() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            // Chamada interna, tarefa agendada ou a carga de demonstração.
            return ContextoAuditoria.AUTOR_ANONIMO;
        }

        Jwt jwt = token.getToken();
        Long usuarioId = PapeisDoKeycloak.usuarioIdDe(jwt);
        String nome = jwt.getClaimAsString("name");
        String email = jwt.getClaimAsString("email");

        if (nome == null || nome.isBlank()) {
            nome = jwt.getClaimAsString("preferred_username");
        }
        if (usuarioId == null) {
            // Conta do Keycloak sem vínculo com o cadastro do core: o registro
            // fica identificado assim mesmo, e o vazio aparece na auditoria.
            return "%s <%s> (sem usuario_id)".formatted(nome, email);
        }
        return "%s <%s> [usuario:%d]".formatted(nome, email, usuarioId);
    }
}
