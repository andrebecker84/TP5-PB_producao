package com.andre.infnethub.seguranca;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Traduz os papéis do Keycloak para as autoridades do Spring Security.
 *
 * <p>O Spring, por padrão, lê o claim {@code scope} — que descreve o que o
 * cliente pediu, não quem a pessoa é. Os papéis institucionais (ALUNO,
 * PROFESSOR, SECRETARIA, COORDENADOR) vêm do Keycloak em
 * {@code realm_access.roles}, e é isso que interessa para autorizar.
 *
 * <p>O prefixo {@code ROLE_} é convenção do Spring: sem ele, {@code hasRole}
 * não encontra nada, e a regra passa a barrar o que deveria liberar — ou, se
 * escrita ao contrário, a liberar o que deveria barrar.
 */
public class PapeisDoKeycloak implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> autoridades = papeisDe(jwt).stream()
                .map(papel -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + papel))
                .toList();
        return new JwtAuthenticationToken(jwt, autoridades, jwt.getClaimAsString("preferred_username"));
    }

    @SuppressWarnings("unchecked")
    public static List<String> papeisDe(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> papeis)) {
            return List.of();
        }
        return papeis.stream().map(String::valueOf).toList();
    }

    /**
     * O id do usuário no infnethub-core, posto no token por um mapeador do
     * realm. É o que liga a identidade do Keycloak — um UUID — ao cadastro que
     * o sistema já tinha.
     */
    public static Long usuarioIdDe(Jwt jwt) {
        Object claim = jwt.getClaim("usuario_id");
        if (claim instanceof Number numero) {
            return numero.longValue();
        }
        if (claim instanceof String texto && !texto.isBlank()) {
            return Long.valueOf(texto.trim());
        }
        return null;
    }
}
