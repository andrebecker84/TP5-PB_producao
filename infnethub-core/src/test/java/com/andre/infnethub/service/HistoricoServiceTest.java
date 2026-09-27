package com.andre.infnethub.service;

import com.andre.infnethub.auditoria.ContextoAuditoria;
import com.andre.infnethub.dto.historico.*;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.model.Papel;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.PostRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Camada de consulta do histórico.
 *
 * <p>Sobe o contexto inteiro porque o ponto sob teste é justamente a interação
 * entre as transações do serviço e o carregamento tardio dos snapshots do
 * Envers — com {@code open-in-view=false}, resolver o autor de um post
 * histórico só funciona dentro da transação do serviço. Uma fatia de teste não
 * exerceria isso.
 *
 * <p>Sem transação de teste ({@code NOT_SUPPORTED}): o Envers grava as revisões
 * no commit, e o rollback automático apagaria o histórico antes da verificação.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("HistoricoService")
class HistoricoServiceTest {

    @Autowired
    private HistoricoService historicoService;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario autor;

    @BeforeEach
    void preparar() {
        ContextoAuditoria.limpar();
        autor = usuarioRepository.save(Usuario.builder()
                .nome("Autora Histórico")
                .email("historico-%d@hub.infnet.local".formatted(System.nanoTime()))
                .papel(Papel.PROFESSOR).build());
    }

    @AfterEach
    void limpar() {
        ContextoAuditoria.limpar();
    }

    @Test
    @DisplayName("devolve a linha do tempo de um post, da criação à alteração")
    void historicoDePostComSnapshots() {
        ContextoAuditoria.definirAutor("Autora Histórico <autora@hub.infnet.local>");

        Post post = postRepository.save(Post.builder()
                .titulo("Título original").conteudo("Conteúdo original")
                .autor(autor).curtidas(0).build());

        Post paraEditar = postRepository.findById(post.getId()).orElseThrow();
        paraEditar.setTitulo("Título revisado");
        postRepository.save(paraEditar);

        PaginaDTO<RevisaoDTO<PostSnapshot>> historico =
                historicoService.historicoPost(post.getId(), PageRequest.of(0, 10));

        assertThat(historico.totalElementos()).isEqualTo(2);

        RevisaoDTO<PostSnapshot> criacao = historico.conteudo().get(0);
        assertThat(criacao.tipo()).isEqualTo(TipoAlteracao.CRIACAO);
        assertThat(criacao.tipoDescricao()).isEqualTo("Criação");
        assertThat(criacao.autor()).isEqualTo("Autora Histórico <autora@hub.infnet.local>");
        assertThat(criacao.snapshot().titulo()).isEqualTo("Título original");
        // O autor do post é resolvido a partir de usuarios_aud dentro da transação
        // do serviço — é o que o open-in-view=false torna delicado.
        assertThat(criacao.snapshot().autorNome()).isEqualTo("Autora Histórico");

        RevisaoDTO<PostSnapshot> alteracao = historico.conteudo().get(1);
        assertThat(alteracao.tipo()).isEqualTo(TipoAlteracao.ALTERACAO);
        assertThat(alteracao.snapshot().titulo()).isEqualTo("Título revisado");
    }

    @Test
    @DisplayName("recupera o conteúdo de um post já excluído")
    void ultimaRevisaoDePostExcluido() {
        Post post = postRepository.save(Post.builder()
                .titulo("Post que será apagado").conteudo("Conteúdo a preservar")
                .autor(autor).curtidas(0).build());
        Long id = post.getId();

        postRepository.deleteById(id);
        assertThat(postRepository.findById(id)).isEmpty();

        // O registro sumiu da tabela viva, mas a auditoria ainda responde o que
        // ele dizia — a razão de ser do store_data_at_delete.
        RevisaoDTO<PostSnapshot> ultima = historicoService.ultimaRevisaoPost(id);

        assertThat(ultima.tipo()).isEqualTo(TipoAlteracao.EXCLUSAO);
        assertThat(ultima.snapshot().conteudo()).isEqualTo("Conteúdo a preservar");
    }

    @Test
    @DisplayName("id inexistente resulta em recurso não encontrado")
    void historicoDeIdInexistente() {
        assertThatThrownBy(() -> historicoService.historicoPost(999_999L, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Nenhum histórico encontrado");
    }

    @Test
    @DisplayName("a linha do tempo global lista as revisões da mais recente para a mais antiga")
    void linhaDoTempoGlobal() {
        postRepository.save(Post.builder()
                .titulo("Para a linha do tempo").conteudo("x").autor(autor).curtidas(0).build());

        PaginaDTO<RevisaoResumoDTO> linhaDoTempo =
                historicoService.linhaDoTempo(PageRequest.of(0, 5));

        assertThat(linhaDoTempo.conteudo()).isNotEmpty();
        assertThat(linhaDoTempo.conteudo()).isSortedAccordingTo(
                (a, b) -> Integer.compare(b.revisao(), a.revisao()));
        assertThat(linhaDoTempo.conteudo().getFirst().autor()).isNotBlank();
    }

    @Test
    @DisplayName("uma revisão específica devolve o estado daquele momento")
    void revisaoEspecifica() {
        Post post = postRepository.save(Post.builder()
                .titulo("Versão 1").conteudo("c").autor(autor).curtidas(0).build());

        Post paraEditar = postRepository.findById(post.getId()).orElseThrow();
        paraEditar.setTitulo("Versão 2");
        postRepository.save(paraEditar);

        int primeiraRevisao = historicoService
                .historicoPost(post.getId(), PageRequest.of(0, 10))
                .conteudo().getFirst().revisao();

        RevisaoDTO<PostSnapshot> revisao = historicoService.revisaoPost(post.getId(), primeiraRevisao);

        assertThat(revisao.snapshot().titulo()).isEqualTo("Versão 1");
    }
}
