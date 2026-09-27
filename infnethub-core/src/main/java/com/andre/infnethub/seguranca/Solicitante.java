package com.andre.infnethub.seguranca;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Quem faz a requisição, lido do token já validado pela cadeia de segurança.
 *
 * <p>Até a 1.0.0 o autor de posts, comentários e curtidas vinha do corpo ou da
 * query string ({@code autorId}, {@code usuarioId}): qualquer conta autenticada
 * podia publicar em nome de outra, e editar ou apagar o que não era seu. Agora
 * o cliente não opina sobre quem é — o id sai do claim {@code usuario_id}.
 *
 * <p>Secretaria e coordenação moderam o feed: podem editar e apagar o conteúdo
 * de qualquer pessoa, como já administram as vagas.
 */
public record Solicitante(Long id, boolean moderador) {

    private static final List<String> MODERACAO = List.of("SECRETARIA", "COORDENADOR");

    /**
     * @throws AccessDeniedException se o token não liga a conta a um cadastro
     *         da plataforma (contas técnicas, como a do suporte de TI)
     */
    public static Solicitante de(Jwt token) {
        Long id = token == null ? null : PapeisDoKeycloak.usuarioIdDe(token);
        if (id == null) {
            throw new AccessDeniedException("A conta não está ligada a um cadastro da plataforma.");
        }
        boolean moderador = PapeisDoKeycloak.papeisDe(token).stream().anyMatch(MODERACAO::contains);
        return new Solicitante(id, moderador);
    }

    /** O dono do conteúdo, ou a moderação. */
    public boolean podeAlterar(Long autorId) {
        return moderador || id.equals(autorId);
    }

    public void exigirQuePossaAlterar(Long autorId) {
        if (!podeAlterar(autorId)) {
            throw new AccessDeniedException("Só o autor ou a moderação podem alterar este conteúdo.");
        }
    }
}
