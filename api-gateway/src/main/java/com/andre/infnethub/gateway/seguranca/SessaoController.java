package com.andre.infnethub.gateway.seguranca;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * O que o front-end pode saber sobre a própria sessão.
 *
 * <p>É o único endereço do gateway que não é proxy. Existe porque, no padrão
 * BFF, o navegador não tem o token: ele não consegue ler o próprio nome nem o
 * próprio papel de dentro de um JWT, como faria se o guardasse. Pergunta aqui.
 *
 * <p>Repare no que <strong>não</strong> é devolvido: nem o token de acesso, nem
 * o de atualização. Eles ficam na sessão do servidor. Devolvê-los ao navegador
 * desfaria o motivo de existir deste desenho.
 */
@RestController
@RequestMapping("/bff")
class SessaoController {

    /** Os papéis do Infnet Hub, na ordem em que prevalecem quando há mais de um. */
    private static final List<String> PAPEIS = List.of("SUPORTE_TI", "COORDENADOR", "SECRETARIA", "PROFESSOR", "ALUNO");

    /**
     * A sessão atual.
     *
     * <p>O cliente autorizado não é usado no corpo, e está na assinatura de
     * propósito: para entregá-lo, o Spring confere o token de acesso e, se
     * venceu, o renova no Keycloak. Assim, "há sessão" quer dizer que o
     * provedor ainda a reconhece — e não só que o cookie existe. Se o Keycloak
     * recusar, {@link SessaoEncerradaNoKeycloak} descarta a sessão e responde
     * 401, e a tela de entrada mostra o botão em vez de mandar para o feed.
     */
    @GetMapping("/eu")
    ResponseEntity<Sessao> eu(@AuthenticationPrincipal OidcUser usuario,
                              @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient sessaoNoProvedor) {
        return ResponseEntity.ok(new Sessao(
                usuario.getClaimAsString("usuario_id") == null ? null : Long.valueOf(usuario.getClaimAsString("usuario_id")),
                usuario.getPreferredUsername(),
                usuario.getFullName(),
                usuario.getEmail(),
                papelDe(usuario)));
    }

    /**
     * O papel institucional, tirado dos papéis do realm.
     *
     * <p>A ordem importa: uma pessoa pode ser professora e coordenadora ao mesmo
     * tempo, e a interface precisa de um rótulo só. A autorização de verdade
     * continua acontecendo nos serviços, sobre a lista inteira de papéis do
     * token — isto aqui é rótulo de tela.
     */
    private static String papelDe(OidcUser usuario) {
        Collection<String> doRealm = papeisDoRealm(usuario);
        return PAPEIS.stream().filter(doRealm::contains).findFirst().orElse("ALUNO");
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> papeisDoRealm(OidcUser usuario) {
        Object realmAccess = usuario.getClaims().get("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> mapa && mapa.get("roles") instanceof Collection<?> papeis) {
            return papeis.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    record Sessao(Long usuarioId, String username, String nome, String email, String papel) {
    }
}
