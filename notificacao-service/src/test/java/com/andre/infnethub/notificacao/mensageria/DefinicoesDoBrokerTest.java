package com.andre.infnethub.notificacao.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.SalaDeEspera;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O arquivo de definições do broker e o código dizem a mesma coisa sobre a sala
 * de espera.
 *
 * <p>A topologia existe em dois lugares por uma boa razão: o
 * {@code definitions.json} a carrega na subida do RabbitMQ, antes de qualquer
 * serviço, e os {@code Declarables} a garantem quando o serviço sobe contra um
 * broker vazio. O risco é as duas divergirem. Com a sala de espera isso é
 * silencioso — uma ligação faltando num dos 22 níveis faz a mensagem sumir no
 * meio da cascata, sem erro, dias depois de publicada — e argumentos diferentes
 * numa fila fazem a declaração do serviço falhar na subida. Este teste lê o
 * arquivo e o confere nível a nível contra {@link SalaDeEspera}, a mesma
 * definição que o relay usa para montar a chave.
 */
@DisplayName("definitions.json — a sala de espera confere com o código")
class DefinicoesDoBrokerTest {

    private static JsonNode definicoes;

    @BeforeAll
    static void ler() throws Exception {
        // O Maven executa os testes a partir da pasta do módulo.
        definicoes = new ObjectMapper().readTree(Files.readString(Path.of("../infra/rabbitmq/definitions.json")));
    }

    @Test
    @DisplayName("cada nível tem a fila com o prazo do nível, dead-letter para o seguinte e as duas ligações")
    void niveis() {
        for (int nivel = 0; nivel < SalaDeEspera.NIVEIS; nivel++) {
            String nome = SalaDeEspera.filaDoNivel(nivel);
            JsonNode args = no("queues", "name", nome).get("arguments");

            assertThat(args.get("x-queue-type").asString()).as(nome).isEqualTo("quorum");
            assertThat(args.get("x-message-ttl").asLong()).as(nome).isEqualTo(SalaDeEspera.ttlDoNivelMs(nivel));
            assertThat(args.get("x-dead-letter-exchange").asString()).as(nome)
                    .isEqualTo(SalaDeEspera.seguinteAo(nivel));
            assertThat(args.get("x-dead-letter-strategy").asString()).as(nome).isEqualTo("at-least-once");
            assertThat(args.get("x-overflow").asString()).as(nome).isEqualTo("reject-publish");

            String exchange = SalaDeEspera.exchangeDoNivel(nivel);
            assertThat(no("exchanges", "name", exchange).get("type").asString()).isEqualTo("topic");
            assertThat(temLigacao(exchange, nome, "queue", SalaDeEspera.padraoParaEsperar(nivel)))
                    .as("bit 1 do nível %d leva à fila", nivel).isTrue();
            assertThat(temLigacao(exchange, SalaDeEspera.seguinteAo(nivel), "exchange",
                    SalaDeEspera.padraoParaSeguir(nivel)))
                    .as("bit 0 do nível %d segue adiante", nivel).isTrue();
        }
    }

    @Test
    @DisplayName("a saída da cascata entrega na fila de trabalho dos avisos")
    void entrega() {
        assertThat(temLigacao(SalaDeEspera.EXCHANGE_ENTREGA, Canais.FILA_NOTIFICACAO_COMANDOS, "queue",
                SalaDeEspera.padraoDeEntrega(Canais.ROTA_ENVIAR_AVISO))).isTrue();
    }

    @Test
    @DisplayName("a sala de espera antiga, com bloqueio de cabeça de fila, não existe mais")
    void semASalaAntiga() {
        assertThat(StreamSupport.stream(definicoes.get("queues").spliterator(), false)
                .map(q -> q.get("name").asString()))
                .doesNotContain("notificacao.comandos.espera");
    }

    @Test
    @DisplayName("toda fila de mensagens mortas tem prazo de retenção — dado pessoal não fica lá para sempre")
    void retencaoDasMensagensMortas() {
        JsonNode politica = no("policies", "name", "retencao-das-mensagens-mortas");
        java.util.regex.Pattern padrao = java.util.regex.Pattern.compile(politica.get("pattern").asString());
        assertThat(politica.get("definition").get("message-ttl").asLong()).isPositive();

        var mortas = StreamSupport.stream(definicoes.get("queues").spliterator(), false)
                .map(q -> q.get("name").asString())
                .filter(nome -> nome.endsWith(".dlq"))
                .toList();
        assertThat(mortas).isNotEmpty().allMatch(nome -> padrao.matcher(nome).find());

        // E nenhuma fila de trabalho cai na política por engano.
        assertThat(StreamSupport.stream(definicoes.get("queues").spliterator(), false)
                .map(q -> q.get("name").asString())
                .filter(nome -> !nome.endsWith(".dlq")))
                .noneMatch(nome -> padrao.matcher(nome).find());
    }

    private static JsonNode no(String colecao, String campo, String valor) {
        return StreamSupport.stream(definicoes.get(colecao).spliterator(), false)
                .filter(n -> valor.equals(n.get(campo).asString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("%s sem %s=%s".formatted(colecao, campo, valor)));
    }

    private static boolean temLigacao(String origem, String destino, String tipo, String chave) {
        return StreamSupport.stream(definicoes.get("bindings").spliterator(), false)
                .anyMatch(b -> origem.equals(b.get("source").asString())
                        && destino.equals(b.get("destination").asString())
                        && tipo.equals(b.get("destination_type").asString())
                        && chave.equals(b.get("routing_key").asString()));
    }
}
