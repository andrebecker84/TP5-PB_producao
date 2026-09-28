package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import com.andre.infnethub.dto.PostRequestDTO;
import com.andre.infnethub.dto.UsuarioRequestDTO;
import com.andre.infnethub.dto.UsuarioResponseDTO;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.repository.PostRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import com.andre.infnethub.seguranca.Solicitante;
import com.andre.infnethub.service.PostService;
import com.andre.infnethub.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O que entra na caixa de saída — e, principalmente, o que não entra.
 *
 * <p>O relay está desligado neste perfil: nada sai para o broker, e a tabela
 * mostra exatamente o que cada operação depositou. A entrega em si é coberta
 * por {@link RelayDoOutboxIntegracaoTest}, contra um RabbitMQ real.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Caixa de saída — o evento nasce na transação do usuário")
class CaixaDeSaidaTest {

    @Autowired private UsuarioService usuarioService;
    @Autowired private PostService postService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private OutboxRepository outbox;
    @Autowired private CaixaDeSaida caixa;
    @Autowired private ObjectMapper json;

    private UsuarioResponseDTO novoUsuario(String nome) {
        String email = "outbox-%d@hub.infnet.local".formatted(System.nanoTime());
        return usuarioService.criar(new UsuarioRequestDTO(nome, email, "Faculdade Infnet", "Bloco 5", "26E2"));
    }

    private List<MensagemNoOutbox> doUsuario(Long id) {
        return outbox.findByChaveOrderByIdAsc(String.valueOf(id));
    }

    @Test
    @DisplayName("cadastrar deposita UsuarioCadastradoV1, pendente, com o estado completo e sem e-mail")
    void cadastroDepositaEvento() {
        UsuarioResponseDTO criado = novoUsuario("Aluna do Outbox");

        List<MensagemNoOutbox> eventos = doUsuario(criado.id());
        assertThat(eventos).hasSize(1);

        MensagemNoOutbox evento = eventos.getFirst();
        assertThat(evento.getTipo()).isEqualTo("UsuarioCadastradoV1");
        assertThat(evento.getRota()).isEqualTo(Canais.ROTA_USUARIO_CADASTRADO);
        assertThat(evento.isPublicada()).isFalse();

        JsonNode corpo = json.readTree(evento.getPayload());
        assertThat(corpo.get("nome").asString()).isEqualTo("Aluna do Outbox");
        assertThat(corpo.get("papel").asString()).isEqualTo("ALUNO");
        assertThat(corpo.get("versao").asLong()).isZero();
        // Minimização: o contrato não transporta o e-mail.
        assertThat(corpo.has("email")).isFalse();
    }

    @Test
    @DisplayName("atualizar deposita UsuarioAtualizadoV1 com a versão já incrementada")
    void atualizacaoLevaVersaoNova() {
        UsuarioResponseDTO criado = novoUsuario("Nome Antigo");
        String email = usuarioRepository.findById(criado.id()).orElseThrow().getEmail();

        usuarioService.atualizar(criado.id(),
                new UsuarioRequestDTO("Nome Novo", email, "Faculdade Infnet", "Bloco 6", "26E3"));

        List<MensagemNoOutbox> eventos = doUsuario(criado.id());
        assertThat(eventos).extracting(MensagemNoOutbox::getTipo)
                .containsExactly("UsuarioCadastradoV1", "UsuarioAtualizadoV1");

        JsonNode atualizado = json.readTree(eventos.get(1).getPayload());
        assertThat(atualizado.get("nome").asString()).isEqualTo("Nome Novo");
        // Sem o saveAndFlush o evento sairia com 0, igual ao do cadastro, e o
        // consumidor o descartaria como repetição de versão.
        assertThat(atualizado.get("versao").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("pedir a remoção deposita ExpurgoSolicitadoV1 só com o id — e não remove nada ainda")
    void remocaoAbreASaga() {
        UsuarioResponseDTO criado = novoUsuario("Removível");

        usuarioService.deletar(criado.id());

        List<MensagemNoOutbox> eventos = doUsuario(criado.id());
        assertThat(eventos).extracting(MensagemNoOutbox::getTipo)
                .containsExactly("UsuarioCadastradoV1", "ExpurgoSolicitadoV1");

        JsonNode pedido = json.readTree(eventos.get(1).getPayload());
        assertThat(pedido.has("nome"))
                .as("o pedido de expurgo não pode espalhar o dado que se quer eliminar")
                .isFalse();

        // O passo 1 da saga bloqueia; não apaga. É justamente isso que permite
        // desfazer se um participante recusar.
        assertThat(usuarioRepository.existsById(criado.id())).isTrue();
        assertThat(usuarioRepository.findByIdAndRemovidoFalse(criado.id()))
                .as("para a API, já não existe")
                .isEmpty();
    }

    @Test
    @DisplayName("quem publicou no feed também pode ser removido — a saga termina em anonimização")
    void remocaoDeQuemTemConteudo() {
        UsuarioResponseDTO criado = novoUsuario("Autor de Post");
        postRepository.save(Post.builder()
                .titulo("Post que prendia o autor").conteudo("conteúdo")
                .autor(usuarioRepository.findById(criado.id()).orElseThrow())
                .curtidas(0).build());

        // No TP3 isto respondia 409: a chave estrangeira do post segurava o
        // autor, e quem tinha publicado no feed simplesmente não podia ser
        // removido. A saga termina em anonimização, que não esbarra em FK
        // nenhuma — e o 409 desapareceu junto com o problema que o causava.
        usuarioService.deletar(criado.id());

        assertThat(doUsuario(criado.id())).extracting(MensagemNoOutbox::getTipo)
                .containsExactly("UsuarioCadastradoV1", "ExpurgoSolicitadoV1");
    }

    @Test
    @DisplayName("apagar um post deposita PostRemovidoV1, para as notificações dele sumirem junto")
    void remocaoDePost() {
        UsuarioResponseDTO autor = novoUsuario("Autor que Apaga");
        Solicitante solicitante = new Solicitante(autor.id(), false);
        Long postId = postService.criar(new PostRequestDTO("Efêmero", "conteúdo", null), solicitante).id();

        postService.deletar(postId, solicitante);

        // A chave é o id do post, que pode coincidir com o de um usuário:
        // filtra pelo tipo.
        List<MensagemNoOutbox> removidos = outbox.findByChaveOrderByIdAsc(String.valueOf(postId)).stream()
                .filter(m -> m.getTipo().equals("PostRemovidoV1"))
                .toList();
        assertThat(removidos).hasSize(1);
        assertThat(removidos.getFirst().getRota()).isEqualTo(Canais.ROTA_POST_REMOVIDO);
        assertThat(json.readTree(removidos.getFirst().getPayload()).get("postId").asLong()).isEqualTo(postId);
    }

    @Test
    @DisplayName("o reenvio anuncia o estado atual com a versão atual, sem incrementá-la")
    void reenvioDoEstadoAtual() {
        UsuarioResponseDTO criado = novoUsuario("Chegou Antes do Consumidor");

        int reenviados = usuarioService.reenviarEstadoAtual();

        // Os ativos, e não todos: reenviar o estado de quem está em expurgo
        // espalharia de novo, pelo broker, o dado que a saga tirou de
        // circulação. A diferença entre os dois números é justamente quem a
        // saga já removeu.
        assertThat(reenviados)
                .isEqualTo(usuarioRepository.findByRemovidoFalse().size())
                .isLessThanOrEqualTo((int) usuarioRepository.count());
        List<MensagemNoOutbox> eventos = doUsuario(criado.id());
        assertThat(eventos).extracting(MensagemNoOutbox::getTipo)
                .containsExactly("UsuarioCadastradoV1", "UsuarioAtualizadoV1");
        // Mesma versão do cadastro: quem já recebeu o cadastro descarta o
        // reenvio como repetição; quem nunca recebeu, aplica.
        assertThat(json.readTree(eventos.get(1).getPayload()).get("versao").asLong()).isZero();
    }

    @Test
    @DisplayName("depositar fora de uma transação é recusado, em vez de gravar o evento sozinho")
    void exigeTransacao() {
        var evento = new UsuarioRemovidoV1(UUID.randomUUID(), Instant.now(), 42L);

        assertThatThrownBy(() -> caixa.depositar(evento, Canais.ROTA_USUARIO_REMOVIDO))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outbox.existsByMensagemId(evento.mensagemId())).isFalse();
    }
}
