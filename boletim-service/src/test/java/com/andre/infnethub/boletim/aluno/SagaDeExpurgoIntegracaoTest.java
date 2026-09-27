package com.andre.infnethub.boletim.aluno;

import com.andre.infnethub.boletim.CenarioAcademico;
import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Conceito;
import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.expurgo.AlunoAnonimizadoV1;
import com.andre.infnethub.contratos.expurgo.AnonimizacaoRecusadaV1;
import com.andre.infnethub.contratos.expurgo.ExpurgoSolicitadoV1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Base64UrlNamingStrategy;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A participação do boletim na saga de expurgo, de ponta a ponta, por um
 * broker de verdade.
 *
 * <p>O {@code ParticipacaoNoExpurgoTest} prova a regra; este prova o caminho:
 * que o pedido publicado pelo core na exchange de eventos chega à fila do
 * boletim, que a decisão é tomada, e que a resposta sai pela caixa de saída
 * <em>e chega ao broker</em> com a rota que o core escuta ({@code aluno.*}) e
 * com o processo copiado do pedido. É o que o lado do core — testado sem
 * broker, no {@code SagaDeExpurgoTest} — assume que acontece.
 *
 * <p>No lugar do core, uma fila anônima ligada a {@code aluno.*}: o teste
 * escuta exatamente o que o core escutaria.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1"
})
@ActiveProfiles("test")
@Import(InfraDeMensageria.class)
@DisplayName("Saga de expurgo — o boletim responde pelo broker")
class SagaDeExpurgoIntegracaoTest {

    private static final long ESPERA_MS = 15_000;
    private static final AtomicLong PROXIMO_ALUNO = new AtomicLong(8000);
    private static final AtomicInteger PROXIMO_BLOCO = new AtomicInteger(950);
    private static final AtomicLong PROXIMO_PROCESSO = new AtomicLong(500);

    @Autowired private RabbitTemplate rabbit;
    @Autowired private RabbitAdmin admin;
    @Autowired private CenarioAcademico cenario;
    @Autowired private AlunoReplicaRepository replica;

    private Queue comoOCore;
    /** Tudo o que chegou à fila, casado ou não — é o que prova ausência. */
    private final List<Object> recebidas = new ArrayList<>();

    @BeforeEach
    void escutarComoOCore() {
        // Exclusiva, e NÃO auto-delete: uma fila auto-delete some quando o
        // primeiro consumidor se desliga — e cada receive() é um consumidor
        // que se liga e se desliga. A limpeza fica no @AfterEach.
        comoOCore = new Queue(Base64UrlNamingStrategy.DEFAULT.generateName(), false, true, false);
        admin.declareQueue(comoOCore);
        admin.declareBinding(BindingBuilder.bind(comoOCore)
                .to(new TopicExchange(Canais.EXCHANGE_EVENTOS)).with(Canais.PADRAO_ALUNO));
    }

    @AfterEach
    void pararDeEscutar() {
        admin.deleteQueue(comoOCore.getName());
    }

    private long alunoCom(Conceito... conceitos) {
        long id = PROXIMO_ALUNO.incrementAndGet();
        cenario.alunoSincronizado(id, "Aluno " + id);
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco da saga " + id);
        var matricula = cenario.matricula(id, cenario.disciplina(bloco, "Disciplina", 60, false),
                "26E2", 90, 4, 0, 0);
        cenario.avaliar(matricula, conceitos);
        return id;
    }

    private void pedir(long processo, long alunoId) {
        rabbit.convertAndSend(Canais.EXCHANGE_EVENTOS, Canais.ROTA_EXPURGO_SOLICITADO,
                new ExpurgoSolicitadoV1(UUID.randomUUID(), Instant.now(), processo, alunoId));
    }

    /**
     * A resposta a um processo específico.
     *
     * <p>Casada pelo processo, e não pela ordem de chegada: a caixa de saída
     * publica em lotes, e a resposta de um teste anterior pode chegar à fila
     * deste. É a mesma razão pela qual o core casa as respostas pelo processo.
     */
    private Object respostaDo(long processo) {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            Object recebida = rabbit.receiveAndConvert(comoOCore.getName(), 1_000);
            if (recebida != null) {
                recebidas.add(recebida);
            }
            if (recebida instanceof AlunoAnonimizadoV1 a && a.processoId() == processo) {
                return a;
            }
            if (recebida instanceof AnonimizacaoRecusadaV1 r && r.processoId() == processo) {
                return r;
            }
        }
        return null;
    }

    @Test
    @DisplayName("pedido para quem terminou o curso volta como AlunoAnonimizado, com o processo do pedido")
    void anonimizaEResponde() {
        long id = alunoCom(Conceito.DL, Conceito.DML);
        long processo = PROXIMO_PROCESSO.incrementAndGet();

        pedir(processo, id);

        Object resposta = respostaDo(processo);
        assertThat(resposta).isInstanceOf(AlunoAnonimizadoV1.class);
        AlunoAnonimizadoV1 anonimizado = (AlunoAnonimizadoV1) resposta;
        assertThat(anonimizado.processoId()).isEqualTo(processo);
        assertThat(anonimizado.usuarioId()).isEqualTo(id);
        assertThat(anonimizado.registrosMantidos()).isEqualTo(1);
        assertThat(replica.existsById(id)).as("a identificação saiu do boletim").isFalse();
    }

    @Test
    @DisplayName("pedido para quem está cursando volta como AnonimizacaoRecusada, e nada é tocado")
    void recusaEResponde() {
        long id = alunoCom(Conceito.DL, null);
        long processo = PROXIMO_PROCESSO.incrementAndGet();

        pedir(processo, id);

        Object resposta = respostaDo(processo);
        assertThat(resposta).isInstanceOf(AnonimizacaoRecusadaV1.class);
        AnonimizacaoRecusadaV1 recusa = (AnonimizacaoRecusadaV1) resposta;
        assertThat(recusa.processoId()).isEqualTo(processo);
        assertThat(recusa.motivo()).contains("1 competência(s) em avaliação");
        assertThat(replica.existsById(id)).as("recusar é não mexer").isTrue();
    }

    @Test
    @DisplayName("o mesmo pedido entregue duas vezes produz uma resposta só")
    void pedidoRepetidoRespondeUmaVez() {
        long id = alunoCom(Conceito.DL);
        long processo = PROXIMO_PROCESSO.incrementAndGet();
        var pedido = new ExpurgoSolicitadoV1(UUID.randomUUID(), Instant.now(), processo, id);

        rabbit.convertAndSend(Canais.EXCHANGE_EVENTOS, Canais.ROTA_EXPURGO_SOLICITADO, pedido);
        rabbit.convertAndSend(Canais.EXCHANGE_EVENTOS, Canais.ROTA_EXPURGO_SOLICITADO, pedido);

        assertThat(respostaDo(processo)).isInstanceOf(AlunoAnonimizadoV1.class);
        // Um segundo pedido serve de marcador: quando a resposta dele chega, a
        // repetição — que foi na frente, na mesma fila — já foi processada. Se
        // ela tivesse gerado outra resposta, teria sido publicada antes.
        long marcador = alunoCom(Conceito.DL);
        long processoDoMarcador = PROXIMO_PROCESSO.incrementAndGet();
        pedir(processoDoMarcador, marcador);
        assertThat(respostaDo(processoDoMarcador)).isNotNull();

        long respostasAoPedido = recebidas.stream()
                .filter(m -> m instanceof AlunoAnonimizadoV1 a && a.processoId() == processo)
                .count();
        assertThat(respostasAoPedido).as("uma resposta só para o pedido entregue duas vezes").isEqualTo(1);
    }
}
