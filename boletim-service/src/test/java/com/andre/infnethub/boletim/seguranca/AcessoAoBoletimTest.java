package com.andre.infnethub.boletim.seguranca;

import com.andre.infnethub.boletim.CenarioAcademico;
import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Conceito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem pode ler o boletim de quem.
 *
 * <p>É o teste que o TP3 não tinha como escrever: sem autenticação, a resposta
 * para "e se outro aluno pedir?" era sempre "recebe o boletim". A identidade
 * vinha de um cabeçalho que o próprio cliente preenchia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Acesso ao boletim — o dado mais sensível do sistema")
class AcessoAoBoletimTest {

    private static final AtomicInteger PROXIMO_BLOCO = new AtomicInteger(500);
    private static final long ALUNA = 8_001L;
    private static final long OUTRO_ALUNO = 8_002L;
    private static final long PROFESSOR = 8_003L;

    @Autowired private MockMvc mvc;
    @Autowired private CenarioAcademico cenario;

    @BeforeEach
    void percursoDaAluna() {
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco protegido");
        cenario.cursouEPassou(ALUNA, bloco, "Disciplina da aluna", 60, "26E2", Conceito.DML, Conceito.DL);
        cenario.alunoSincronizado(ALUNA, "Aluna Protegida");
    }

    /** Token como o do Keycloak: o id do usuário num claim, os papéis no realm_access. */
    private static RequestPostProcessor como(long usuarioId, String... papeis) {
        return jwt().jwt(token -> token
                .claim("usuario_id", usuarioId)
                .claim("realm_access", Map.of("roles", List.of(papeis))));
    }

    @Test
    @DisplayName("a aluna vê o próprio boletim")
    void oProprioBoletim() throws Exception {
        mvc.perform(get("/api/v1/boletim/{id}", ALUNA).with(como(ALUNA, "ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aluno.nome", is("Aluna Protegida")));
    }

    @Test
    @DisplayName("outro aluno recebe 403 — e não o boletim, como acontecia no TP3")
    void boletimAlheioEhNegado() throws Exception {
        mvc.perform(get("/api/v1/boletim/{id}", ALUNA).with(como(OUTRO_ALUNO, "ALUNO")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Sem permissão")));
    }

    @Test
    @DisplayName("o mesmo vale para o painel de desempenho e para as atividades")
    void desempenhoEAtividadesTambem() throws Exception {
        mvc.perform(get("/api/v1/desempenho/{id}", ALUNA).with(como(OUTRO_ALUNO, "ALUNO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alunos/{id}/atividades", ALUNA).with(como(OUTRO_ALUNO, "ALUNO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("professor, secretaria e coordenação veem o boletim de qualquer aluno")
    void quemEnsinaVeTodos() throws Exception {
        mvc.perform(get("/api/v1/boletim/{id}", ALUNA).with(como(PROFESSOR, "PROFESSOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/boletim/{id}", ALUNA).with(como(PROFESSOR, "SECRETARIA")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/boletim/{id}", ALUNA).with(como(PROFESSOR, "COORDENADOR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sem token não se lê boletim nenhum")
    void semTokenNaoLe() throws Exception {
        mvc.perform(get("/api/v1/boletim/{id}", ALUNA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Não autenticado")));
    }

    @Test
    @DisplayName("o catálogo do curso é institucional: qualquer pessoa autenticada consulta")
    void catalogoEhDeTodos() throws Exception {
        mvc.perform(get("/api/v1/catalogo/conceitos").with(como(OUTRO_ALUNO, "ALUNO")))
                .andExpect(status().isOk());
    }
}
