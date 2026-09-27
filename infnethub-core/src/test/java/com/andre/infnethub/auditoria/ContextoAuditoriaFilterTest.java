package com.andre.infnethub.auditoria;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexto de auditoria da requisição — autoria e origem.
 *
 * <p>Esta cobertura nasceu de uma regressão do próprio TP3. Com a entrada do API
 * Gateway, toda requisição passou a chegar do mesmo endereço interno, e o campo
 * de origem das revisões virou uma coluna sempre igual: preenchida, e incapaz de
 * distinguir qualquer coisa. Uma auditoria assim é pior que a ausência dela,
 * porque aparenta ter a informação.
 *
 * <p>O defeito não quebrava teste nenhum — só aparecia ao comparar duas revisões
 * gravadas por clientes diferentes. Daí estes testes.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ContextoAuditoriaFilter — autoria e origem da requisição")
class ContextoAuditoriaFilterTest {

    @Autowired private ContextoAuditoriaFilter filtro;

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    /** Põe no contexto um token como o que o Keycloak emite. */
    private void autenticadoComo(String nome, String email, Object usuarioId) {
        Jwt.Builder jwt = Jwt.withTokenValue("token-de-teste")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", email)
                .claim("name", nome)
                .claim("email", email)
                .claim("realm_access", Map.of("roles", List.of("ALUNO")));
        if (usuarioId != null) {
            jwt.claim("usuario_id", usuarioId);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt.build(), List.of(), email));
    }

    /**
     * Executa o filtro e captura o contexto de dentro da cadeia.
     *
     * <p>Tem de ser lido ali dentro: o {@code finally} do filtro limpa o
     * ThreadLocal ao terminar — e é justamente esse cuidado que impede a thread
     * de voltar ao pool carregando o autor da requisição anterior.
     */
    private String[] executar(MockHttpServletRequest requisicao) throws Exception {
        AtomicReference<String[]> capturado = new AtomicReference<>();
        FilterChain cadeia = (req, res) ->
                capturado.set(new String[]{ ContextoAuditoria.autorAtual(), ContextoAuditoria.origemAtual() });

        filtro.doFilter(requisicao, new MockHttpServletResponse(), cadeia);
        return capturado.get();
    }

    private MockHttpServletRequest escrita() {
        MockHttpServletRequest r = new MockHttpServletRequest("PUT", "/api/v1/posts/1");
        r.setRemoteAddr("172.28.0.7");   // o gateway, na rede do Compose
        return r;
    }

    @Test
    @DisplayName("prefere X-Forwarded-For ao endereço da conexão — que agora é sempre o gateway")
    void preferaOForwardedFor() throws Exception {
        MockHttpServletRequest r = escrita();
        r.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(executar(r)[1]).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("com uma cadeia de proxies, vale o PRIMEIRO — o cliente original")
    void primeiroDaCadeia() throws Exception {
        MockHttpServletRequest r = escrita();
        r.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1, 172.28.0.7");

        // Os seguintes são os saltos que a requisição atravessou.
        assertThat(executar(r)[1]).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("sem o header, cai no endereço da conexão")
    void semHeaderUsaRemoteAddr() throws Exception {
        // É o caminho do acesso direto ao serviço, sem gateway à frente.
        assertThat(executar(escrita())[1]).isEqualTo("172.28.0.7");
    }

    @Test
    @DisplayName("header vazio é tratado como ausente, não como origem em branco")
    void headerVazio() throws Exception {
        MockHttpServletRequest r = escrita();
        r.addHeader("X-Forwarded-For", "   ");

        assertThat(executar(r)[1]).isEqualTo("172.28.0.7");
    }

    @Test
    @DisplayName("a autoria vem do token, por extenso, com o id do usuário")
    void autoriaVemDoToken() throws Exception {
        autenticadoComo("Autor de Teste", "autor@hub.infnet.local", 42);

        // Gravado por extenso, e não como FK: é o que faz o registro sobreviver
        // à exclusão do usuário.
        assertThat(executar(escrita())[0])
                .isEqualTo("Autor de Teste <autor@hub.infnet.local> [usuario:42]");
    }

    @Test
    @DisplayName("o cabeçalho X-Usuario-Id não identifica mais ninguém")
    void cabecalhoForjadoNaoValeMais() throws Exception {
        MockHttpServletRequest r = escrita();
        // Era assim que, até o TP3, qualquer cliente assinava em nome de outro.
        r.addHeader("X-Usuario-Id", "1");

        assertThat(executar(r)[0]).isEqualTo(ContextoAuditoria.AUTOR_ANONIMO);
    }

    @Test
    @DisplayName("conta do Keycloak sem vínculo com o cadastro fica marcada como tal")
    void contaSemUsuarioId() throws Exception {
        autenticadoComo("Conta Externa", "externa@parceira.local", null);

        assertThat(executar(escrita())[0]).contains("(sem usuario_id)");
    }

    @Test
    @DisplayName("sem autenticação a alteração fica anônima, mas continua registrada")
    void semAutor() throws Exception {
        assertThat(executar(escrita())[0]).isEqualTo(ContextoAuditoria.AUTOR_ANONIMO);
    }

    @Test
    @DisplayName("usuario_id em texto também é aceito — o mapeador do realm pode mudar de tipo")
    void usuarioIdComoTexto() throws Exception {
        autenticadoComo("Autor de Teste", "autor@hub.infnet.local", "7");

        assertThat(executar(escrita())[0]).endsWith("[usuario:7]");
    }

    @Test
    @DisplayName("o contexto é limpo ao fim da requisição — a thread volta limpa ao pool")
    void limpaOContexto() throws Exception {
        MockHttpServletRequest r = escrita();
        r.addHeader("X-Forwarded-For", "203.0.113.9");
        executar(r);

        // Sem isto, a próxima requisição atendida por esta thread herdaria o
        // autor e a origem da anterior.
        //
        // Com o ThreadLocal vazio o contexto devolve SISTEMA, e não ANONIMO —
        // são coisas diferentes, e a distinção importa: "anonimo" é uma escrita
        // HTTP que não se identificou, enquanto "sistema" é uma gravação que
        // não veio de requisição alguma (a carga inicial, por exemplo). As
        // colunas de autoria são NOT NULL, e nenhum dos dois pode ser vazio.
        assertThat(ContextoAuditoria.autorAtual()).isEqualTo(ContextoAuditoria.AUTOR_SISTEMA);
        assertThat(ContextoAuditoria.origemAtual()).isEqualTo(ContextoAuditoria.ORIGEM_INTERNA);
    }

    @Test
    @DisplayName("requisição de leitura não monta contexto de auditoria")
    void leituraNaoMontaContexto() throws Exception {
        MockHttpServletRequest leitura = new MockHttpServletRequest("GET", "/api/v1/posts");
        leitura.setRemoteAddr("203.0.113.9");
        leitura.addHeader("X-Usuario-Id", "1");

        // GET não gera revisão; montar o contexto seria trabalho sem destino.
        // O ThreadLocal fica intocado, e a leitura enxerga o padrão do sistema —
        // não o autor do header, que é deliberadamente ignorado aqui.
        assertThat(executar(leitura)[0]).isEqualTo(ContextoAuditoria.AUTOR_SISTEMA);
        assertThat(executar(leitura)[1]).isEqualTo(ContextoAuditoria.ORIGEM_INTERNA);
    }
}
