package com.andre.infnethub.boletim.aluno;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.usuario.UsuarioAtualizadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioCadastradoV1;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Do broker até a réplica, contra um RabbitMQ real.
 *
 * <p>Aqui o teste faz o papel do core: publica na exchange de eventos com a
 * mesma chave de roteamento que o relay usaria, e observa o que o boletim faz.
 * As tentativas ficam curtas (100 ms) para o caminho até a fila de mensagens
 * mortas não custar segundos ao teste.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1"
})
@ActiveProfiles("test")
@Import(InfraDeMensageria.class)
@DisplayName("Ouvinte de usuários — o evento do core vira réplica local")
class OuvinteDeUsuariosIntegracaoTest {

    private static final Duration ESPERA = Duration.ofSeconds(15);
    private static final AtomicLong PROXIMO_ALUNO = new AtomicLong(7000);

    @Autowired private RabbitTemplate rabbit;
    @Autowired private AlunoReplicaRepository repositorio;
    @Autowired private ReplicaDeAlunos replica;
    @Autowired private JdbcTemplate jdbc;

    private static UsuarioCadastradoV1 cadastro(long id, String nome) {
        return new UsuarioCadastradoV1(UUID.randomUUID(), Instant.now(), id, 0,
                nome, "Faculdade Infnet", "Bloco 5", "26E2", "ALUNO", "Aluno(a)");
    }

    private void publicar(String rota, Object evento) {
        rabbit.convertAndSend(Canais.EXCHANGE_EVENTOS, rota, evento);
    }

    @Test
    @DisplayName("UsuarioCadastrado publicado pelo core aparece no boletim, sem nenhuma chamada HTTP")
    void cadastroChegaNaReplica() {
        long id = PROXIMO_ALUNO.incrementAndGet();

        publicar(Canais.ROTA_USUARIO_CADASTRADO, cadastro(id, "Aluna Via Evento"));

        await().atMost(ESPERA).until(() -> repositorio.existsById(id));
        assertThat(replica.buscar(id).nome()).isEqualTo("Aluna Via Evento");
    }

    @Test
    @DisplayName("cadastro, alteração e remoção, em sequência, deixam a réplica no estado final")
    void cicloCompleto() {
        long id = PROXIMO_ALUNO.incrementAndGet();

        publicar(Canais.ROTA_USUARIO_CADASTRADO, cadastro(id, "Nome Inicial"));
        publicar(Canais.ROTA_USUARIO_ATUALIZADO, new UsuarioAtualizadoV1(UUID.randomUUID(), Instant.now(), id, 1,
                "Nome Alterado", "Faculdade Infnet", "Bloco 6", "26E3", "ALUNO", "Aluno(a)"));

        await().atMost(ESPERA).until(() -> repositorio.findById(id)
                .map(r -> r.getNome().equals("Nome Alterado")).orElse(false));
        assertThat(repositorio.findById(id).orElseThrow().getUltimoBloco()).isEqualTo("Bloco 6");

        publicar(Canais.ROTA_USUARIO_REMOVIDO, new UsuarioRemovidoV1(UUID.randomUUID(), Instant.now(), id));
        await().atMost(ESPERA).until(() -> !repositorio.existsById(id));
    }

    @Test
    @DisplayName("a mesma mensagem entregue duas vezes produz efeito uma vez só")
    void duplicataDescartada() {
        long id = PROXIMO_ALUNO.incrementAndGet();
        UsuarioCadastradoV1 evento = cadastro(id, "Entregue Duas Vezes");

        publicar(Canais.ROTA_USUARIO_CADASTRADO, evento);
        await().atMost(ESPERA).until(() -> repositorio.existsById(id));

        // Remove a réplica por fora e reentrega a MESMA mensagem. Se o consumidor
        // não descartasse a repetição, a linha voltaria a existir.
        repositorio.deleteById(id);
        publicar(Canais.ROTA_USUARIO_CADASTRADO, evento);

        // Um marcador publicado depois: quando ele chega, a duplicata — que foi
        // na frente, na mesma fila — com certeza já foi processada.
        long marcador = PROXIMO_ALUNO.incrementAndGet();
        publicar(Canais.ROTA_USUARIO_CADASTRADO, cadastro(marcador, "Marcador"));
        await().atMost(ESPERA).until(() -> repositorio.existsById(marcador));

        assertThat(repositorio.existsById(id)).isFalse();
        Integer registros = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mensagem_processada WHERE mensagem_id = ?", Integer.class, evento.mensagemId());
        assertThat(registros).isEqualTo(1);
    }

    @Test
    @DisplayName("mensagem ilegível esgota as tentativas, vai para a DLQ, e a fila segue andando")
    void mensagemVenenosaVaiParaDlq() {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("__TypeId__", UsuarioCadastradoV1.class.getName());
        props.setMessageId("veneno-" + UUID.randomUUID());
        Message veneno = MessageBuilder.withBody("{isto não é json".getBytes(StandardCharsets.UTF_8))
                .andProperties(props).build();

        rabbit.send(Canais.EXCHANGE_EVENTOS, Canais.ROTA_USUARIO_CADASTRADO, veneno);

        Message morta = await().atMost(ESPERA)
                .until(() -> rabbit.receive(Canais.dlqDe(Canais.FILA_BOLETIM_USUARIOS), 200), m -> m != null);
        assertThat(morta.getMessageProperties().getMessageId()).isEqualTo(props.getMessageId());

        // A mensagem problemática saiu do caminho: a seguinte é processada.
        long id = PROXIMO_ALUNO.incrementAndGet();
        publicar(Canais.ROTA_USUARIO_CADASTRADO, cadastro(id, "Depois do Veneno"));
        await().atMost(ESPERA).until(() -> repositorio.existsById(id));
    }
}
