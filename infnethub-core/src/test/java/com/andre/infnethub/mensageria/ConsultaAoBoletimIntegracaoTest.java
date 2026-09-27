package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.consulta.SituacaoAcademicaPerguntaV1;
import com.andre.infnethub.contratos.consulta.SituacaoAcademicaRespostaV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O padrão request/reply, com o broker de verdade e um boletim de mentira.
 *
 * <p>O boletim é substituído porque o que está em teste é o <em>mecanismo</em>:
 * que a pergunta sai pela exchange certa, que a resposta volta sozinha pelo
 * {@code reply-to} — sem nenhuma fila de resposta declarada em lugar nenhum —,
 * e que o core sabe distinguir "não tem nada" de "ninguém respondeu".
 *
 * <p>O segundo teste é o que justifica o padrão ter sido escrito com cuidado:
 * sem respondente no ar, o core não trava e não mente.
 */
@SpringBootTest(properties = {
        "app.outbox.relay.habilitado=false",
        // Curto de propósito: o teste da ausência de resposta precisa esperar
        // o prazo inteiro, e o valor de produção faria a suíte demorar.
        "app.consulta.espera-ms=1500",
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
@ActiveProfiles("test")
@Import({InfraDeMensageria.class, ConsultaAoBoletimIntegracaoTest.BoletimDeMentira.class})
@DisplayName("Consulta ao boletim — pergunta e resposta pelo broker")
class ConsultaAoBoletimIntegracaoTest {

    /** O aluno cujo id faz o dublê fingir que o boletim está fora do ar. */
    static final long ALUNO_SEM_RESPOSTA = 99_999L;

    /**
     * Um respondente mínimo, no lugar do boletim-service.
     *
     * <p>Declara a mesma fila e a mesma ligação que ele declara — se os nomes
     * divergirem do que o core publica, a pergunta não encontra ninguém e o
     * teste falha, que é exatamente o erro que se quer apanhar.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class BoletimDeMentira {

        BoletimDeMentira(AmqpAdmin admin) {
            DirectExchange exchange =
                    ExchangeBuilder.directExchange(Canais.EXCHANGE_CONSULTAS).durable(true).build();
            var fila = QueueBuilder.durable(Canais.FILA_BOLETIM_CONSULTAS)
                    .quorum()
                    .ttl(Canais.VALIDADE_DA_PERGUNTA_MS)
                    .build();
            admin.declareExchange(exchange);
            admin.declareQueue(fila);
            admin.declareBinding(BindingBuilder.bind(fila).to(exchange).with(Canais.ROTA_SITUACAO_ACADEMICA));
        }

        @RabbitListener(queues = Canais.FILA_BOLETIM_CONSULTAS)
        SituacaoAcademicaRespostaV1 responder(SituacaoAcademicaPerguntaV1 pergunta) throws InterruptedException {
            if (pergunta.alunoId() == ALUNO_SEM_RESPOSTA) {
                // Não responder seria deixar a mensagem na fila; demorar é o
                // que de fato acontece quando o boletim está sobrecarregado.
                Thread.sleep(Duration.ofSeconds(5));
            }
            return pergunta.alunoId() == 1L
                    ? SituacaoAcademicaRespostaV1.de(1L, 3, 7)
                    : SituacaoAcademicaRespostaV1.de(pergunta.alunoId(), 0, 0);
        }
    }

    @Autowired private ConsultaAoBoletim consulta;

    @Test
    @DisplayName("a resposta volta pelo reply-to, sem fila de resposta declarada")
    void respostaVolta() {
        SituacaoAcademicaRespostaV1 comRegistro = consulta.situacaoDe(1L);

        assertThat(comRegistro.consultado()).isTrue();
        assertThat(comRegistro.matriculas()).isEqualTo(3);
        assertThat(comRegistro.avaliacoes()).isEqualTo(7);
        assertThat(comRegistro.podeSerExcluido()).as("quem tem matrícula não pode ser excluído").isFalse();
    }

    @Test
    @DisplayName("aluno sem nada no boletim pode ser excluído")
    void alunoSemRegistro() {
        SituacaoAcademicaRespostaV1 vazio = consulta.situacaoDe(2L);

        assertThat(vazio.consultado()).isTrue();
        assertThat(vazio.podeSerExcluido()).isTrue();
    }

    @Test
    @DisplayName("sem resposta no prazo, o core desiste e diz que não consultou")
    void semRespostaNoPrazo() {
        long antes = System.currentTimeMillis();
        SituacaoAcademicaRespostaV1 semResposta = consulta.situacaoDe(ALUNO_SEM_RESPOSTA);
        long decorrido = System.currentTimeMillis() - antes;

        assertThat(semResposta.consultado()).as("não pode afirmar que consultou").isFalse();
        // A distinção que evita excluir alguém com matrícula ativa: sem
        // resposta, a porta fica fechada.
        assertThat(semResposta.podeSerExcluido()).isFalse();
        assertThat(decorrido).as("desistiu perto do prazo, sem esperar o respondente").isLessThan(4_000);
    }
}
