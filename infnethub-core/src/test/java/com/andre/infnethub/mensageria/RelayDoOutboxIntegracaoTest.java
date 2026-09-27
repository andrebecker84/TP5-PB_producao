package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.usuario.UsuarioAtualizadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import com.andre.infnethub.dto.UsuarioRequestDTO;
import com.andre.infnethub.dto.UsuarioResponseDTO;
import com.andre.infnethub.service.UsuarioService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * O relay contra um RabbitMQ real: publica, espera a confirmação, marca.
 *
 * <p>Precisa de Docker. É o único jeito honesto de provar confirmação e
 * devolução — um dublê do {@code RabbitTemplate} responderia o que o teste
 * mandasse, e o teste provaria só a si mesmo.
 */
@SpringBootTest(properties = {
        "app.outbox.relay.habilitado=true",
        "app.outbox.intervalo-ms=100",
        // Lote pequeno de propósito: com ele, o teste de pico atravessa vários
        // lotes e várias ondas, que é o que ele precisa exercitar.
        "app.outbox.lote=5"
})
@ActiveProfiles("test")
@Import(InfraDeMensageria.class)
@DisplayName("Relay do outbox — entrega confirmada ao RabbitMQ")
class RelayDoOutboxIntegracaoTest {

    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Autowired private UsuarioService usuarioService;
    @Autowired private OutboxRepository outbox;
    @Autowired private CaixaDeSaida caixa;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private AmqpAdmin admin;
    @Autowired private TransactionTemplate transacao;
    @Autowired private Tracer tracer;

    private final List<Long> linhasParaLimpar = new ArrayList<>();

    @BeforeEach
    void esvaziarFila() {
        admin.purgeQueue(InfraDeMensageria.FILA_ESPIA, false);
    }

    @AfterEach
    void limparPendentesDeProposito() {
        // Um evento sem destino ficaria sendo tentado para sempre, poluindo o
        // log dos testes seguintes.
        outbox.deleteAllById(linhasParaLimpar);
    }

    private UsuarioResponseDTO novoUsuario(String nome) {
        String email = "relay-%d@hub.infnet.local".formatted(System.nanoTime());
        return usuarioService.criar(new UsuarioRequestDTO(nome, email, "Faculdade Infnet", "Bloco 5", "26E2"));
    }

    private Message receberDoUsuario(Long usuarioId) {
        List<Message> vistas = new ArrayList<>();
        await().atMost(ESPERA).until(() -> {
            Message m = rabbit.receive(InfraDeMensageria.FILA_ESPIA, 200);
            if (m != null) {
                vistas.add(m);
            }
            return vistas.stream().anyMatch(v -> String.valueOf(usuarioId)
                    .equals(v.getMessageProperties().getHeader("cloudEvents:subject")));
        });
        return vistas.stream()
                .filter(v -> String.valueOf(usuarioId).equals(v.getMessageProperties().getHeader("cloudEvents:subject")))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("o evento chega à fila com cabeçalhos AMQP e CloudEvents, e a linha é marcada como publicada")
    void publicaEMarca() {
        UsuarioResponseDTO criado = novoUsuario("Aluno Publicado");

        Message recebida = receberDoUsuario(criado.id());
        MensagemNoOutbox linha = outbox.findByChaveOrderByIdAsc(String.valueOf(criado.id())).getFirst();

        var p = recebida.getMessageProperties();
        assertThat(p.getMessageId()).isEqualTo(linha.getMensagemId().toString());
        assertThat(p.getType()).isEqualTo("UsuarioCadastradoV1");
        assertThat(p.getAppId()).isEqualTo("infnethub-core");
        assertThat(p.getContentType()).isEqualTo("application/json");
        assertThat((String) p.getHeader("cloudEvents:type")).isEqualTo("br.infnet.hub.UsuarioCadastradoV1");
        assertThat((String) p.getHeader("cloudEvents:source")).isEqualTo("/infnethub-core");
        assertThat(new String(recebida.getBody(), StandardCharsets.UTF_8)).contains("\"nome\":\"Aluno Publicado\"");

        await().atMost(ESPERA).until(() -> outbox.findById(linha.getId()).orElseThrow().isPublicada());
    }

    @Test
    @DisplayName("sem fila de destino, o broker devolve e o evento continua pendente — sem travar os demais")
    void semDestinoFicaPendente() {
        // Rota que ninguém escuta. Sem mandatory, o RabbitMQ confirmaria e
        // descartaria; o relay marcaria como publicado um evento perdido.
        var evento = new UsuarioRemovidoV1(UUID.randomUUID(), Instant.now(), 900_000L + System.nanoTime() % 1000);
        transacao.executeWithoutResult(s -> caixa.depositar(evento, "ninguem.escuta"));
        MensagemNoOutbox semDestino = outbox.findByChaveOrderByIdAsc(evento.chave()).getFirst();
        linhasParaLimpar.add(semDestino.getId());

        await().atMost(ESPERA).until(() -> outbox.findById(semDestino.getId()).orElseThrow().getTentativas() > 0);
        MensagemNoOutbox depois = outbox.findById(semDestino.getId()).orElseThrow();
        assertThat(depois.isPublicada()).isFalse();
        assertThat(depois.getUltimoErro()).contains("sem fila de destino");

        // O evento preso é de outro usuário: não pode reter este.
        UsuarioResponseDTO outro = novoUsuario("Aluno Não Retido");
        assertThat(receberDoUsuario(outro.id()).getMessageProperties().getType()).isEqualTo("UsuarioCadastradoV1");
    }

    @Test
    @DisplayName("os eventos de um mesmo usuário saem na ordem em que aconteceram")
    void ordemPorUsuario() {
        UsuarioResponseDTO criado = novoUsuario("Ordem Original");
        UsuarioRequestDTO alteracao = new UsuarioRequestDTO("Ordem Alterada",
                "relay-ordem-%d@hub.infnet.local".formatted(System.nanoTime()), "Faculdade Infnet", "Bloco 6", "26E3");
        usuarioService.atualizar(criado.id(), alteracao);
        usuarioService.deletar(criado.id());

        List<String> tipos = new ArrayList<>();
        await().atMost(ESPERA).until(() -> {
            Message m = rabbit.receive(InfraDeMensageria.FILA_ESPIA, 200);
            if (m != null && String.valueOf(criado.id()).equals(m.getMessageProperties().getHeader("cloudEvents:subject"))) {
                tipos.add(m.getMessageProperties().getType());
            }
            return tipos.size() == 3;
        });
        // O terceiro é o pedido de expurgo, e não a remoção: desde a saga, o
        // fato consumado só é publicado quando os participantes confirmam.
        assertThat(tipos).containsExactly("UsuarioCadastradoV1", "UsuarioAtualizadoV1", "ExpurgoSolicitadoV1");
    }

    @Test
    @DisplayName("um pico de eventos de várias chaves atravessa lotes e ondas sem trocar a ordem de nenhuma")
    void picoMantemAOrdemPorChave() {
        // Sete agregados, quatro versões cada, depositados intercalados numa
        // transação só: 28 mensagens, lote de 5. Cada onda leva no máximo uma
        // mensagem por chave, então a ordem só se mantém se a onda seguinte
        // esperar a confirmação da anterior.
        long base = 800_000L + (System.nanoTime() % 10_000) * 10;
        int chaves = 7;
        int versoes = 4;
        transacao.executeWithoutResult(s -> {
            for (int v = 1; v <= versoes; v++) {
                for (int k = 0; k < chaves; k++) {
                    caixa.depositar(new UsuarioAtualizadoV1(UUID.randomUUID(), Instant.now(), base + k, v,
                            "Pico " + k, "Faculdade Infnet", "Bloco 5", "26E2", "ALUNO", "Aluno(a)"),
                            Canais.ROTA_USUARIO_ATUALIZADO);
                }
            }
        });

        Map<String, List<Long>> recebidasPorChave = new LinkedHashMap<>();
        await().atMost(ESPERA).until(() -> {
            Message m = rabbit.receive(InfraDeMensageria.FILA_ESPIA, 200);
            if (m != null) {
                String chave = m.getMessageProperties().getHeader("cloudEvents:subject");
                long id = Long.parseLong(chave);
                if (id >= base && id < base + chaves) {
                    String corpo = new String(m.getBody(), StandardCharsets.UTF_8);
                    long versao = Long.parseLong(corpo.replaceAll(".*\"versao\":(\\d+).*", "$1"));
                    recebidasPorChave.computeIfAbsent(chave, c -> new ArrayList<>()).add(versao);
                }
            }
            return recebidasPorChave.values().stream().mapToInt(List::size).sum() == chaves * versoes;
        });

        assertThat(recebidasPorChave).hasSize(chaves);
        recebidasPorChave.forEach((chave, versoesRecebidas) ->
                assertThat(versoesRecebidas).as("ordem da chave " + chave).containsExactly(1L, 2L, 3L, 4L));
        assertThat(outbox.findByPublicadoEmIsNullOrderByIdAsc()).as("nada ficou para trás")
                .noneMatch(m -> m.getTipo().equals("UsuarioAtualizadoV1")
                        && Long.parseLong(m.getChave()) >= base && Long.parseLong(m.getChave()) < base + chaves);
    }

    @Test
    @DisplayName("o trace da requisição de origem atravessa a caixa de saída e chega ao cabeçalho da mensagem")
    void rastreamentoAtravessaOOutbox() {
        // A requisição HTTP, simulada pelo span que o servidor abriria.
        Span requisicao = tracer.nextSpan().name("POST /api/v1/usuarios").start();
        UsuarioResponseDTO criado;
        try (Tracer.SpanInScope escopo = tracer.withSpan(requisicao)) {
            criado = novoUsuario("Aluno Rastreado");
        } finally {
            requisicao.end();
        }
        String traceId = requisicao.context().traceId();

        MensagemNoOutbox linha = outbox.findByChaveOrderByIdAsc(String.valueOf(criado.id())).getFirst();
        assertThat(linha.getRastreamento()).startsWith("00-" + traceId + "-");

        // A publicação saiu da thread do relay, meio segundo depois — e ainda
        // assim leva o mesmo traceId, agora como filha da requisição.
        String traceparent = receberDoUsuario(criado.id()).getMessageProperties().getHeader("traceparent");
        assertThat(traceparent).startsWith("00-" + traceId + "-");
        assertThat(traceparent).doesNotContain(requisicao.context().spanId());
    }
}
