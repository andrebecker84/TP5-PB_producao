package com.andre.infnethub.boletim.seguranca;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Quem pode ver o boletim de quem.
 *
 * <p>Boletim é o dado mais sensível deste sistema: conceitos, frequência,
 * reprovações. Até o TP3 bastava trocar o número na URL para ler o de outra
 * pessoa — não havia sequer com o que comparar, porque a identidade vinha de um
 * cabeçalho informado pelo próprio cliente.
 *
 * <p>A regra é a da secretaria: o aluno vê o seu; professor, secretaria e
 * coordenação veem o de qualquer um, porque é o trabalho deles. Quem tem token
 * válido, mas nenhum dos dois casos, não vê nada.
 *
 * <p>Fica num bean com nome curto para poder ser chamada direto nas anotações:
 * {@code @PreAuthorize("@acesso.aoAluno(#alunoId)")}. A regra é escrita uma vez
 * e usada em todos os endpoints que recebem um aluno.
 */
@Component("acesso")
public class Acesso {

    private static final List<String> PAPEIS_QUE_VEEM_TODOS = List.of("PROFESSOR", "SECRETARIA", "COORDENADOR");

    public boolean aoAluno(Long alunoId) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            return false;
        }
        List<String> papeis = PapeisDoKeycloak.papeisDe(token.getToken());
        if (papeis.stream().anyMatch(PAPEIS_QUE_VEEM_TODOS::contains)) {
            return true;
        }
        return Objects.equals(alunoId, PapeisDoKeycloak.usuarioIdDe(token.getToken()));
    }
}
