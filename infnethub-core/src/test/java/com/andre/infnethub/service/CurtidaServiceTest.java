package com.andre.infnethub.service;

import com.andre.infnethub.model.Papel;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.CurtidaRepository;
import com.andre.infnethub.repository.PostRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coerência entre as curtidas e o contador desnormalizado de {@code posts}.
 *
 * <p>Cobre um defeito herdado do TP1: o toggle criava e removia a linha em
 * {@code curtidas} mas nunca atualizava {@code posts.curtidas}. A contagem
 * exibida no feed voltava ao valor antigo assim que a página era recarregada.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CurtidaService — contador e linhas coerentes")
class CurtidaServiceTest {

    @Autowired
    private CurtidaService curtidaService;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private CurtidaRepository curtidaRepository;

    private Usuario autor;
    private Usuario leitor;
    private Post post;

    @BeforeEach
    void preparar() {
        long marca = System.nanoTime();
        autor = usuarioRepository.save(Usuario.builder()
                .nome("Autor").email("autor-%d@hub.infnet.local".formatted(marca))
                .papel(Papel.PROFESSOR).build());
        leitor = usuarioRepository.save(Usuario.builder()
                .nome("Leitor").email("leitor-%d@hub.infnet.local".formatted(marca))
                .papel(Papel.ALUNO).build());
        post = postRepository.save(Post.builder()
                .titulo("Post curtível").conteudo("c").autor(autor).curtidas(0).build());
    }

    @Test
    @DisplayName("curtir grava a linha e atualiza o contador do post")
    void curtirSincronizaContador() {
        CurtidaService.ResultadoCurtida resultado = curtidaService.alternar(post.getId(), leitor.getId());

        assertThat(resultado.curtido()).isTrue();
        assertThat(resultado.total()).isEqualTo(1);
        assertThat(curtidaRepository.countByPostId(post.getId())).isEqualTo(1);

        // O ponto do teste: o contador persistido — o que o feed lê — acompanha.
        Post recarregado = postRepository.findById(post.getId()).orElseThrow();
        assertThat(recarregado.getCurtidas()).isEqualTo(1);
    }

    @Test
    @DisplayName("descurtir remove a linha e o contador volta a zero")
    void descurtirSincronizaContador() {
        curtidaService.alternar(post.getId(), leitor.getId());
        CurtidaService.ResultadoCurtida resultado = curtidaService.alternar(post.getId(), leitor.getId());

        assertThat(resultado.curtido()).isFalse();
        assertThat(resultado.total()).isZero();
        assertThat(curtidaRepository.countByPostId(post.getId())).isZero();
        assertThat(postRepository.findById(post.getId()).orElseThrow().getCurtidas()).isZero();
    }

    @Test
    @DisplayName("o contador continua fiel após várias alternâncias")
    void contadorFielAposVariasAlternancias() {
        Usuario outro = usuarioRepository.save(Usuario.builder()
                .nome("Outro").email("outro-%d@hub.infnet.local".formatted(System.nanoTime()))
                .papel(Papel.ALUNO).build());

        curtidaService.alternar(post.getId(), leitor.getId());   // 1
        curtidaService.alternar(post.getId(), outro.getId());    // 2
        curtidaService.alternar(post.getId(), leitor.getId());   // 1 (descurtiu)
        curtidaService.alternar(post.getId(), autor.getId());    // 2

        long real = curtidaRepository.countByPostId(post.getId());
        int contador = postRepository.findById(post.getId()).orElseThrow().getCurtidas();

        assertThat(real).isEqualTo(2);
        assertThat(contador).isEqualTo((int) real);
    }

    @Test
    @DisplayName("curtir não gera revisão de auditoria do post")
    void curtirNaoPoluiHistoricoDoPost() {
        long revisoesAntes = postRepository.findRevisions(post.getId()).getContent().size();

        curtidaService.alternar(post.getId(), leitor.getId());

        // posts.curtidas é @NotAudited: atualizar só o contador não muda nenhuma
        // propriedade auditada, então o Envers não abre revisão. É o que mantém
        // o histórico editorial livre do ruído de engajamento.
        long revisoesDepois = postRepository.findRevisions(post.getId()).getContent().size();
        assertThat(revisoesDepois).isEqualTo(revisoesAntes);
    }
}
