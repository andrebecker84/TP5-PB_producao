package com.andre.infnethub.expurgo;

import com.andre.infnethub.auditoria.ContextoAuditoria;
import com.andre.infnethub.dto.UsuarioRequestDTO;
import com.andre.infnethub.dto.UsuarioResponseDTO;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.mensageria.MensagemNoOutbox;
import com.andre.infnethub.mensageria.OutboxRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import com.andre.infnethub.service.UsuarioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A saga de expurgo nos dois desfechos, no prazo, e no que ela recusa fazer.
 *
 * <p>Aqui não há broker: o que está em teste é a <em>máquina de estados</em> —
 * o que o core faz ao receber cada resposta possível, e quando nenhuma chega. O
 * caminho pelo RabbitMQ, com o boletim de verdade do outro lado, é verificado
 * no {@code SagaDeExpurgoIntegracaoTest} do boletim-service e no cenário 9 do
 * roteiro de demonstração.
 */
@SpringBootTest(properties = "app.outbox.relay.habilitado=false")
@ActiveProfiles("test")
@DisplayName("Saga de expurgo — bloqueio, conclusão, compensação e prazo")
class SagaDeExpurgoTest {

    private static final Duration PRAZO = Duration.ofHours(24);

    @Autowired private UsuarioService usuarios;
    @Autowired private UsuarioRepository repositorio;
    @Autowired private SagaDeExpurgo saga;
    @Autowired private ExpurgoRepository expurgos;
    @Autowired private OutboxRepository outbox;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limparContexto() {
        ContextoAuditoria.limpar();
    }

    private UsuarioResponseDTO novoUsuario(String nome) {
        return usuarios.criar(new UsuarioRequestDTO(nome,
                "expurgo-%d@hub.infnet.local".formatted(System.nanoTime()),
                "Faculdade Infnet", "Bloco 5", "26E2"));
    }

    @Test
    @DisplayName("o pedido bloqueia a pessoa sem apagar nada")
    void pedidoBloqueia() {
        UsuarioResponseDTO criado = novoUsuario("Vai Sair");

        ExpurgoLgpd processo = saga.solicitar(criado.id(), 3L);

        assertThat(processo.getEstado()).isEqualTo(EstadoDoExpurgo.SOLICITADO);
        assertThat(processo.emAndamento()).isTrue();

        // O registro continua — é o que torna a compensação possível.
        assertThat(repositorio.findById(criado.id())).isPresent();
        // Mas para a API já não existe.
        assertThat(repositorio.findByIdAndRemovidoFalse(criado.id())).isEmpty();
        assertThatThrownBy(() -> usuarios.buscarPorId(criado.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        // O pedido leva o processo, para as respostas voltarem casadas a ele.
        MensagemNoOutbox pedido = mensagensSobre(criado.id()).getLast();
        assertThat(pedido.getTipo()).isEqualTo("ExpurgoSolicitadoV1");
        assertThat(pedido.getPayload()).contains("\"processoId\":" + processo.getId());
    }

    @Test
    @DisplayName("a confirmação anonimiza o cadastro e publica o fato consumado")
    void confirmacaoConclui() {
        UsuarioResponseDTO criado = novoUsuario("Vai Ser Anonimizado");
        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);

        saga.concluir(aberto.getId(), criado.id(), 9);

        var usuario = repositorio.findById(criado.id()).orElseThrow();
        assertThat(usuario.getNome()).isEqualTo("Usuário removido");
        assertThat(usuario.getEmail()).isEqualTo("removido+" + criado.id() + "@infnethub.invalid");
        assertThat(usuario.getEscola()).isNull();
        assertThat(usuario.isRemovido()).isTrue();

        var processo = expurgos.findById(aberto.getId()).orElseThrow();
        assertThat(processo.getEstado()).isEqualTo(EstadoDoExpurgo.CONCLUIDO);
        assertThat(processo.getRegistrosMantidos()).isEqualTo(9);
        assertThat(processo.getEncerradoEm()).isNotNull();

        // UsuarioRemovidoV1 só existe agora: é o fato consumado, e quem reage a
        // ele pode apagar sem medo de que a remoção seja desfeita.
        assertThat(tipos(criado.id()))
                .containsExactly("UsuarioCadastradoV1", "ExpurgoSolicitadoV1", "UsuarioRemovidoV1");
    }

    @Test
    @DisplayName("a conclusão tira a identificação também da trilha de auditoria, sem apagar a trilha")
    void conclusaoAnonimizaATrilha() {
        UsuarioResponseDTO criado = novoUsuario("Nome Que Deve Sumir");
        String email = criado.email();

        // A própria pessoa age no sistema: a revisão e o `atualizado_por`
        // passam a carregar o nome e o e-mail dela por extenso.
        ContextoAuditoria.definirAutor("Nome Que Deve Sumir <%s> [usuario:%d]".formatted(email, criado.id()));
        usuarios.atualizar(criado.id(), new UsuarioRequestDTO("Nome Que Deve Sumir", email,
                "Outra Escola", "Bloco 6", "26E2"));
        ContextoAuditoria.limpar();

        assertThat(ocorrencias("Nome Que Deve Sumir", email)).as("antes: a pessoa está na trilha").isPositive();
        int revisoesAntes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios_aud WHERE id = ?", Integer.class, criado.id());

        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);
        saga.concluir(aberto.getId(), criado.id(), 0);

        assertThat(ocorrencias("Nome Que Deve Sumir", email))
                .as("depois: nem o nome nem o e-mail aparecem em lugar nenhum da trilha")
                .isZero();

        // A trilha continua inteira — só sem a identidade. Cada ato segue
        // ligado ao mesmo identificador, que já não leva a ninguém.
        int revisoesDepois = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios_aud WHERE id = ?", Integer.class, criado.id());
        assertThat(revisoesDepois).isGreaterThan(revisoesAntes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revisao_auditoria WHERE autor = ?",
                Integer.class, AnonimizacaoDaTrilha.autorAnonimo(criado.id()))).isPositive();
    }

    @Test
    @DisplayName("a recusa desfaz o bloqueio — a compensação — e avisa quem pediu")
    void recusaCompensa() {
        UsuarioResponseDTO criado = novoUsuario("Ainda Cursando");
        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);

        saga.reverter(aberto.getId(), criado.id(), "o aluno tem 2 competência(s) em avaliação");

        // A pessoa voltou a existir, com os dados intactos.
        var usuario = repositorio.findByIdAndRemovidoFalse(criado.id()).orElseThrow();
        assertThat(usuario.getNome()).isEqualTo("Ainda Cursando");
        assertThat(usuario.isRemovido()).isFalse();

        var processo = expurgos.findById(aberto.getId()).orElseThrow();
        assertThat(processo.getEstado()).isEqualTo(EstadoDoExpurgo.REVERTIDO);
        assertThat(processo.getMotivo()).contains("competência");

        // Nenhum UsuarioRemovidoV1: a remoção não aconteceu, e anunciá-la teria
        // feito os outros serviços apagarem dados de quem continua existindo.
        assertThat(tipos(criado.id())).doesNotContain("UsuarioRemovidoV1");

        // E quem pediu é avisado, pelo mesmo caminho de qualquer outro aviso.
        assertThat(outbox.findAll()).extracting(MensagemNoOutbox::getTipo).contains("EnviarAvisoV1");
    }

    @Test
    @DisplayName("sem resposta no prazo, o bloqueio é desfeito — ninguém fica bloqueado para sempre")
    void prazoVencidoCompensa() {
        UsuarioResponseDTO criado = novoUsuario("Participante Calado");
        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);

        // Ainda dentro do prazo: nada acontece.
        assertThat(saga.expirarAbertosAntesDe(LocalDateTime.now().minus(PRAZO), PRAZO)).isZero();
        assertThat(expurgos.findById(aberto.getId()).orElseThrow().emAndamento()).isTrue();

        // Um instante depois do pedido, com o prazo "vencido".
        int encerrados = saga.expirarAbertosAntesDe(LocalDateTime.now().plusSeconds(1), PRAZO);

        assertThat(encerrados).isPositive();
        var processo = expurgos.findById(aberto.getId()).orElseThrow();
        assertThat(processo.getEstado()).isEqualTo(EstadoDoExpurgo.REVERTIDO);
        assertThat(processo.getMotivo()).contains("não responderam em 1 dia(s)");
        assertThat(repositorio.findByIdAndRemovidoFalse(criado.id())).isPresent();
        assertThat(tipos(criado.id())).doesNotContain("UsuarioRemovidoV1");
    }

    @Test
    @DisplayName("confirmação que chega depois do prazo não anonimiza — e faz o participante restaurar")
    void confirmacaoDepoisDoPrazoRestaura() {
        UsuarioResponseDTO criado = novoUsuario("Resposta Depois Do Prazo");
        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);
        saga.expirarAbertosAntesDe(LocalDateTime.now().plusSeconds(1), PRAZO);

        // O boletim voltou, processou o pedido antigo e apagou a réplica.
        saga.concluir(aberto.getId(), criado.id(), 2);

        // O core não conclui um processo encerrado...
        assertThat(expurgos.findById(aberto.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoDoExpurgo.REVERTIDO);
        var usuario = repositorio.findByIdAndRemovidoFalse(criado.id()).orElseThrow();
        assertThat(usuario.getNome()).isEqualTo("Resposta Depois Do Prazo");

        // ...e reenvia o estado da pessoa, para o participante desfazer o que
        // fez. Sem isto, a réplica do boletim ficaria apagada para sempre.
        assertThat(tipos(criado.id())).last().isEqualTo("UsuarioAtualizadoV1");
        assertThat(tipos(criado.id())).doesNotContain("UsuarioRemovidoV1");
    }

    @Test
    @DisplayName("resposta de um processo anterior não decide o processo atual da mesma pessoa")
    void respostaDeOutroProcessoNaoDecide() {
        UsuarioResponseDTO criado = novoUsuario("Dois Pedidos");
        ExpurgoLgpd primeiro = saga.solicitar(criado.id(), 3L);
        saga.expirarAbertosAntesDe(LocalDateTime.now().plusSeconds(1), PRAZO);
        ExpurgoLgpd segundo = saga.solicitar(criado.id(), 3L);

        // A recusa atrasada do primeiro pedido chega com o segundo em curso.
        saga.reverter(primeiro.getId(), criado.id(), "resposta do pedido antigo");

        assertThat(expurgos.findById(segundo.getId()).orElseThrow().emAndamento())
                .as("o segundo pedido continua esperando a própria resposta")
                .isTrue();
        assertThat(repositorio.findByIdAndRemovidoFalse(criado.id())).as("continua bloqueado").isEmpty();
    }

    @Test
    @DisplayName("resposta com processo e pessoa que não se correspondem é descartada")
    void respostaIncoerenteDescartada() {
        UsuarioResponseDTO criado = novoUsuario("Alvo Certo");
        UsuarioResponseDTO outro = novoUsuario("Alvo Errado");
        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);

        saga.concluir(aberto.getId(), outro.id(), 0);

        assertThat(expurgos.findById(aberto.getId()).orElseThrow().emAndamento()).isTrue();
        assertThat(repositorio.findById(outro.id()).orElseThrow().getNome()).isEqualTo("Alvo Errado");
    }

    @Test
    @DisplayName("dois pedidos para a mesma pessoa não abrem dois processos")
    void pedidoDuplicadoERecusado() {
        UsuarioResponseDTO criado = novoUsuario("Pedido Em Dobro");
        saga.solicitar(criado.id(), 3L);

        assertThatThrownBy(() -> saga.solicitar(criado.id(), 3L))
                .isInstanceOf(SagaDeExpurgo.ExpurgoEmAndamentoException.class);

        assertThat(expurgos.findByUsuarioIdOrderByIdDesc(criado.id())).hasSize(1);
    }

    @Test
    @DisplayName("resposta que chega depois do processo encerrado é ignorada, não repetida")
    void respostaAtrasadaNaoReabre() {
        UsuarioResponseDTO criado = novoUsuario("Resposta Atrasada");
        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);
        saga.concluir(aberto.getId(), criado.id(), 1);

        // A reentrega de uma confirmação, ou uma recusa que cruzou com a
        // confirmação no caminho. Nenhuma das duas pode reabrir o que terminou.
        saga.concluir(aberto.getId(), criado.id(), 99);
        saga.reverter(aberto.getId(), criado.id(), "chegou tarde");

        var processo = expurgos.findById(aberto.getId()).orElseThrow();
        assertThat(processo.getEstado()).isEqualTo(EstadoDoExpurgo.CONCLUIDO);
        assertThat(processo.getRegistrosMantidos()).as("não foi sobrescrito").isEqualTo(1);
        assertThat(repositorio.findById(criado.id()).orElseThrow().isRemovido())
                .as("uma recusa atrasada não ressuscita quem já foi anonimizado")
                .isTrue();
    }

    @Test
    @DisplayName("não se pede a remoção de quem já foi removido")
    void naoRemoveDuasVezes() {
        UsuarioResponseDTO criado = novoUsuario("Já Saiu");
        ExpurgoLgpd aberto = saga.solicitar(criado.id(), 3L);
        saga.concluir(aberto.getId(), criado.id(), 0);

        assertThatThrownBy(() -> saga.solicitar(criado.id(), 3L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Em quantos lugares da trilha o nome ou o e-mail ainda aparecem. */
    private int ocorrencias(String nome, String email) {
        String n = "%" + nome + "%";
        String e = "%" + email + "%";
        int total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios_aud WHERE nome LIKE ? OR email LIKE ?", Integer.class, n, e);
        total += jdbc.queryForObject(
                "SELECT COUNT(*) FROM revisao_auditoria WHERE autor LIKE ? OR autor LIKE ?", Integer.class, n, e);
        total += jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE criado_por LIKE ? OR atualizado_por LIKE ?"
                        + " OR criado_por LIKE ? OR atualizado_por LIKE ?", Integer.class, n, n, e, e);
        return total;
    }

    private List<MensagemNoOutbox> mensagensSobre(Long usuarioId) {
        return outbox.findByChaveOrderByIdAsc(String.valueOf(usuarioId));
    }

    private List<String> tipos(Long usuarioId) {
        return mensagensSobre(usuarioId).stream().map(MensagemNoOutbox::getTipo).toList();
    }
}
