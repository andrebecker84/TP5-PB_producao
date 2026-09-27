package com.andre.infnethub.notificacao.mensageria;

import com.andre.infnethub.contratos.feed.PostComentadoV1;
import com.andre.infnethub.contratos.feed.PostCurtidoV1;
import com.andre.infnethub.contratos.usuario.UsuarioAtualizadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioCadastradoV1;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import com.andre.infnethub.contratos.vaga.VagaPublicadaV1;
import com.andre.infnethub.notificacao.dto.NotificacaoDTO;
import com.andre.infnethub.notificacao.repository.DestinatarioRepository;
import com.andre.infnethub.notificacao.service.CentralDeNotificacoes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As regras de quem recebe o quê, sem broker.
 *
 * <p>Chama os métodos do ouvinte diretamente, como o Spring AMQP faria depois de
 * converter a mensagem. O caminho pela fila de verdade está em
 * {@link FluxoAoVivoIntegracaoTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Ouvinte de eventos — quem é avisado, e de quê")
class OuvinteDeEventosTest {

    private static final AtomicLong PROXIMO = new AtomicLong(10_000);

    @Autowired private OuvinteDeEventos ouvinte;
    @Autowired private CentralDeNotificacoes central;
    @Autowired private DestinatarioRepository destinatarios;

    private static long novoId() {
        return PROXIMO.incrementAndGet();
    }

    private static UsuarioCadastradoV1 cadastro(long id, String nome, String papel) {
        return new UsuarioCadastradoV1(UUID.randomUUID(), Instant.now(), id, 0, nome,
                "Faculdade Infnet", "Bloco 5", "26E2", papel, papel);
    }

    private static PostCurtidoV1 curtida(long autor, long quemCurtiu) {
        return new PostCurtidoV1(UUID.randomUUID(), Instant.now(), 77L, "Bem-vindos ao Bloco 5!",
                autor, quemCurtiu, "Mariana Ferreira");
    }

    private List<NotificacaoDTO> de(long usuarioId) {
        return central.recentes(usuarioId, CentralDeNotificacoes.LIMITE_MAXIMO);
    }

    @Test
    @DisplayName("o cadastro gera boas-vindas para o próprio usuário, pelo primeiro nome")
    void boasVindas() {
        long id = novoId();
        ouvinte.cadastrado(cadastro(id, "Lucas Mendonça", "ALUNO"));

        assertThat(de(id)).singleElement().satisfies(n -> {
            assertThat(n.tipo()).isEqualTo("BOAS_VINDAS");
            assertThat(n.texto()).startsWith("Boas-vindas ao Infnet Hub, Lucas!");
            assertThat(n.lida()).isFalse();
        });
    }

    @Test
    @DisplayName("a curtida avisa o autor do post; curtir o próprio post não avisa ninguém")
    void curtidaAvisaOAutor() {
        long autor = novoId();
        long outro = novoId();

        ouvinte.curtido(curtida(autor, outro));
        ouvinte.curtido(curtida(autor, autor));

        assertThat(de(autor)).singleElement().satisfies(n -> {
            assertThat(n.tipo()).isEqualTo("CURTIDA");
            assertThat(n.texto()).isEqualTo("Mariana Ferreira curtiu sua publicação \"Bem-vindos ao Bloco 5!\"");
            assertThat(n.link()).isEqualTo("/feed#post-77");
        });
        assertThat(de(outro)).isEmpty();
    }

    @Test
    @DisplayName("o comentário avisa o autor com um trecho do que foi dito")
    void comentarioAvisaOAutor() {
        long autor = novoId();
        ouvinte.comentado(new PostComentadoV1(UUID.randomUUID(), Instant.now(), 88L, "Dúvida no TP4",
                autor, 5L, novoId(), "Prof. Carlos Oliveira", "Olhe a fila de mensagens mortas"));

        assertThat(de(autor)).singleElement().extracting(NotificacaoDTO::texto)
                .isEqualTo("Prof. Carlos Oliveira comentou em \"Dúvida no TP4\": Olhe a fila de mensagens mortas");
    }

    @Test
    @DisplayName("o mesmo evento entregue duas vezes gera uma notificação só")
    void eventoRepetido() {
        long autor = novoId();
        PostCurtidoV1 mesma = curtida(autor, novoId());

        ouvinte.curtido(mesma);
        ouvinte.curtido(mesma);

        assertThat(de(autor)).hasSize(1);
    }

    @Test
    @DisplayName("vaga nova avisa cada aluno ativo — não professores, não removidos, não quem publicou")
    void vagaFanOut() {
        long aluna = novoId();
        long aluno = novoId();
        long professor = novoId();
        long removido = novoId();
        long secretariaQuePublicou = novoId();
        ouvinte.cadastrado(cadastro(aluna, "Ana", "ALUNO"));
        ouvinte.cadastrado(cadastro(aluno, "Bruno", "ALUNO"));
        ouvinte.cadastrado(cadastro(professor, "Prof. Dias", "PROFESSOR"));
        ouvinte.cadastrado(cadastro(removido, "Ex-aluno", "ALUNO"));
        ouvinte.removido(new UsuarioRemovidoV1(UUID.randomUUID(), Instant.now(), removido));

        ouvinte.vagaPublicada(new VagaPublicadaV1(UUID.randomUUID(), Instant.now(), 9L,
                "Estágio Java", "TechSolutions", "ESTAGIO", secretariaQuePublicou));

        assertThat(de(aluna)).extracting(NotificacaoDTO::tipo).contains("VAGA");
        assertThat(de(aluno)).extracting(NotificacaoDTO::texto).contains("Nova vaga: Estágio Java — TechSolutions (ESTAGIO)");
        assertThat(de(professor)).extracting(NotificacaoDTO::tipo).doesNotContain("VAGA");
        assertThat(de(removido)).isEmpty();
    }

    @Test
    @DisplayName("remover apaga as notificações; um cadastro atrasado não ressuscita o removido")
    void remocaoComLapide() {
        long id = novoId();
        ouvinte.cadastrado(cadastro(id, "Será Removida", "ALUNO"));
        assertThat(de(id)).isNotEmpty();

        ouvinte.removido(new UsuarioRemovidoV1(UUID.randomUUID(), Instant.now(), id));
        assertThat(de(id)).isEmpty();

        // Fora de ordem: a atualização chega depois da remoção.
        ouvinte.atualizado(new UsuarioAtualizadoV1(UUID.randomUUID(), Instant.now(), id, 7, "Voltou?",
                null, null, null, "ALUNO", "Aluno(a)"));
        assertThat(destinatarios.findById(id).orElseThrow().isRemovido()).isTrue();
    }

    @Test
    @DisplayName("remoção processada antes do cadastro deixa uma lápide, e o cadastro é ignorado")
    void remocaoAntesDoCadastro() {
        long id = novoId();
        ouvinte.removido(new UsuarioRemovidoV1(UUID.randomUUID(), Instant.now(), id));
        ouvinte.cadastrado(cadastro(id, "Chegou Tarde", "ALUNO"));

        assertThat(destinatarios.findById(id).orElseThrow().isRemovido()).isTrue();
        ouvinte.vagaPublicada(new VagaPublicadaV1(UUID.randomUUID(), Instant.now(), 10L,
                "Trainee", "Banco Digital", "TRAINEE", novoId()));
        // Nem boas-vindas, nem vaga: para este serviço, a pessoa não existe.
        assertThat(de(id)).isEmpty();
    }
}
