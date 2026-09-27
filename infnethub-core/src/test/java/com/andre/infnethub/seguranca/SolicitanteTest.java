package com.andre.infnethub.seguranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Solicitante — quem pede vem do token")
class SolicitanteTest {

    private static Jwt token(Object usuarioId, String... papeis) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .claim("realm_access", Map.of("roles", List.of(papeis)));
        if (usuarioId != null) {
            builder.claim("usuario_id", usuarioId);
        }
        return builder.build();
    }

    @Test
    @DisplayName("lê o id do claim usuario_id, número ou texto")
    void leOId() {
        assertThat(Solicitante.de(token(7, "ALUNO")).id()).isEqualTo(7L);
        assertThat(Solicitante.de(token("8", "ALUNO")).id()).isEqualTo(8L);
    }

    @Test
    @DisplayName("conta sem cadastro na plataforma é barrada")
    void semUsuarioIdEhBarrado() {
        assertThatThrownBy(() -> Solicitante.de(token(null, "SUPORTE_TI")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> Solicitante.de(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("o autor altera o que é seu, e só isso")
    void autorAlteraSoOProprio() {
        Solicitante aluno = Solicitante.de(token(1, "ALUNO"));

        assertThat(aluno.podeAlterar(1L)).isTrue();
        assertThat(aluno.podeAlterar(2L)).isFalse();
        assertThatThrownBy(() -> aluno.exigirQuePossaAlterar(2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("secretaria e coordenação moderam o conteúdo de qualquer pessoa")
    void moderacaoAlteraQualquerUm() {
        assertThat(Solicitante.de(token(3, "SECRETARIA")).podeAlterar(99L)).isTrue();
        assertThat(Solicitante.de(token(4, "COORDENADOR")).podeAlterar(99L)).isTrue();
        assertThat(Solicitante.de(token(2, "PROFESSOR")).podeAlterar(99L)).isFalse();
    }
}
