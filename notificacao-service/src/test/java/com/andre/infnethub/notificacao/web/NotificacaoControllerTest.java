package com.andre.infnethub.notificacao.web;

import com.andre.infnethub.notificacao.model.TipoNotificacao;
import com.andre.infnethub.notificacao.service.CentralDeNotificacoes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A API de notificações: cada um vê e altera só as suas.
 *
 * <p>A identidade vem do token — {@code jwt()} monta um como o que o Keycloak
 * emitiria, sem precisar dele no ar. Note que nenhum teste consegue pedir "as
 * notificações do usuário X": não há como, e é esse o ponto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API de notificações — só as minhas")
class NotificacaoControllerTest {

    private static final AtomicLong PROXIMO = new AtomicLong(20_000);

    @Autowired private MockMvc mvc;
    @Autowired private CentralDeNotificacoes central;

    private long novoUsuario() {
        return PROXIMO.incrementAndGet();
    }

    /** Um token do Keycloak para este usuário, com o claim usuario_id. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario(long usuarioId) {
        return jwt().jwt(token -> token.claim("usuario_id", usuarioId));
    }

    private void notificar(long destinatario, String texto) {
        central.notificar(destinatario, TipoNotificacao.CURTIDA, texto, "/feed", UUID.randomUUID());
    }

    @Test
    @DisplayName("sem token, 401 no mesmo formato de erro dos outros serviços")
    void semToken() throws Exception {
        mvc.perform(get("/api/v1/notificacoes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Não autenticado")));
    }

    @Test
    @DisplayName("token válido de conta sem vínculo com o cadastro é 403, não 500")
    void tokenSemUsuarioId() throws Exception {
        mvc.perform(get("/api/v1/notificacoes").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("não está vinculada")));
    }

    @Test
    @DisplayName("lista as notificações do usuário, das mais novas para as mais velhas, e conta as não lidas")
    void listaEConta() throws Exception {
        long eu = novoUsuario();
        long outro = novoUsuario();
        notificar(eu, "primeira");
        notificar(eu, "segunda");
        notificar(outro, "de outra pessoa");

        mvc.perform(get("/api/v1/notificacoes").with(comoUsuario(eu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].texto", is("segunda")))
                .andExpect(jsonPath("$[0].lida", is(false)));

        mvc.perform(get("/api/v1/notificacoes/nao-lidas").with(comoUsuario(eu)))
                .andExpect(jsonPath("$.total", is(2)));
    }

    @Test
    @DisplayName("marcar como lida ignora ids de notificações alheias")
    void naoMarcaAlheias() throws Exception {
        long eu = novoUsuario();
        long outro = novoUsuario();
        notificar(eu, "minha");
        notificar(outro, "alheia");
        long minha = central.recentes(eu, 1).getFirst().id();
        long alheia = central.recentes(outro, 1).getFirst().id();

        mvc.perform(post("/api/v1/notificacoes/lidas").with(comoUsuario(eu))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%d,%d]}".formatted(minha, alheia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atualizadas", is(1)));

        assertThat(central.naoLidas(eu)).isZero();
        assertThat(central.naoLidas(outro)).isEqualTo(1);
    }

    @Test
    @DisplayName("marcar como não lida devolve o destaque só às notificações do próprio usuário")
    void desmarcaSoAsMinhas() throws Exception {
        long eu = novoUsuario();
        long outro = novoUsuario();
        notificar(eu, "minha");
        notificar(outro, "alheia");
        long minha = central.recentes(eu, 1).getFirst().id();
        long alheia = central.recentes(outro, 1).getFirst().id();
        central.marcarTodasComoLidas(eu);
        central.marcarTodasComoLidas(outro);

        mvc.perform(post("/api/v1/notificacoes/nao-lidas").with(comoUsuario(eu))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%d,%d]}".formatted(minha, alheia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atualizadas", is(1)));

        assertThat(central.naoLidas(eu)).isEqualTo(1);
        assertThat(central.naoLidas(outro)).isZero();
    }

    @Test
    @DisplayName("excluir em lote remove só as do usuário")
    void excluiSoAsMinhas() throws Exception {
        long eu = novoUsuario();
        long outro = novoUsuario();
        notificar(eu, "vai sair");
        notificar(outro, "fica");
        long minha = central.recentes(eu, 1).getFirst().id();
        long alheia = central.recentes(outro, 1).getFirst().id();

        mvc.perform(post("/api/v1/notificacoes/excluir").with(comoUsuario(eu))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[%d,%d]}".formatted(minha, alheia)))
                .andExpect(jsonPath("$.excluidas", is(1)));

        assertThat(central.recentes(eu, 10)).isEmpty();
        assertThat(central.recentes(outro, 10)).hasSize(1);
    }

    @Test
    @DisplayName("seleção vazia é 400, não uma operação sobre tudo")
    void selecaoVazia() throws Exception {
        mvc.perform(post("/api/v1/notificacoes/excluir").with(comoUsuario(novoUsuario()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.ids").exists());
    }

    @Test
    @DisplayName("a conexão ao vivo abre como text/event-stream e confirma com o evento 'conectado'")
    void conexaoAoVivo() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/notificacoes/ao-vivo").with(comoUsuario(novoUsuario())))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(resultado.getResponse().getContentAsString()).contains("event:conectado");
    }
}
