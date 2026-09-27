package com.andre.infnethub.repository;

import com.andre.infnethub.config.AuditoriaConfig;
import com.andre.infnethub.model.Comentario;
import com.andre.infnethub.model.Papel;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consultas de {@link PostRepository}.
 *
 * <p>{@link AuditoriaConfig} precisa ser importado mesmo aqui: sem
 * {@code @EnableEnversRepositories} o Spring Data não consegue instanciar
 * repositórios que estendem {@code RevisionRepository}.
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@ActiveProfiles("test")
@DisplayName("PostRepository")
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ComentarioRepository comentarioRepository;

    private Usuario autor;

    @BeforeEach
    void preparar() {
        autor = usuarioRepository.save(Usuario.builder()
                .nome("Autor Teste").email("autor.teste@hub.infnet.local")
                .papel(Papel.PROFESSOR).build());
    }

    private Post novoPost(String titulo) {
        return postRepository.save(Post.builder()
                .titulo(titulo).conteudo("Conteúdo de " + titulo)
                .autor(autor).curtidas(0).build());
    }

    private void comentar(Post post, int quantidade) {
        for (int i = 0; i < quantidade; i++) {
            comentarioRepository.save(Comentario.builder()
                    .conteudo("Comentário " + i).post(post).autor(autor).build());
        }
    }

    @Test
    @DisplayName("carrega o autor junto dos posts, sem consulta adicional")
    void carregaAutorNaMesmaConsulta() {
        novoPost("Post A");

        List<Post> posts = postRepository.findAllWithAutorOrderByDataDesc();

        assertThat(posts).hasSize(1);
        // Se o JOIN FETCH não trouxesse o autor, isto dispararia outra consulta —
        // ou falharia fora da sessão, com open-in-view=false.
        assertThat(posts.getFirst().getAutor().getNome()).isEqualTo("Autor Teste");
    }

    @Test
    @DisplayName("o feed vem do mais recente para o mais antigo")
    void feedOrdenadoPorDataDecrescente() {
        novoPost("Primeiro");
        novoPost("Segundo");
        novoPost("Terceiro");

        List<Post> posts = postRepository.findAllWithAutorOrderByDataDesc();

        assertThat(posts).hasSize(3);
        assertThat(posts).isSortedAccordingTo(
                Comparator.comparing(Post::getCriadoEm).reversed());
    }

    @Test
    @DisplayName("conta os comentários de vários posts em uma única consulta")
    void contaComentariosAgregadoPorPost() {
        Post comTres = novoPost("Com três");
        Post comUm = novoPost("Com um");
        Post semNenhum = novoPost("Sem nenhum");

        comentar(comTres, 3);
        comentar(comUm, 1);

        Map<Long, Long> contagens = postRepository
                .contarComentariosPorPost(List.of(comTres.getId(), comUm.getId(), semNenhum.getId()))
                .stream()
                .collect(Collectors.toMap(
                        PostRepository.ContagemPorPost::getPostId,
                        PostRepository.ContagemPorPost::getTotal));

        assertThat(contagens).containsEntry(comTres.getId(), 3L);
        assertThat(contagens).containsEntry(comUm.getId(), 1L);
        // Post sem comentários não aparece no GROUP BY — o serviço trata como zero.
        assertThat(contagens).doesNotContainKey(semNenhum.getId());
    }

    @Test
    @DisplayName("busca paginada devolve a fatia e o total corretos")
    void paginacao() {
        for (int i = 0; i < 7; i++) {
            novoPost("Post " + i);
        }

        Page<Post> primeira = postRepository.findAllByOrderByCriadoEmDesc(PageRequest.of(0, 3));

        assertThat(primeira.getContent()).hasSize(3);
        assertThat(primeira.getTotalElements()).isEqualTo(7);
        assertThat(primeira.getTotalPages()).isEqualTo(3);
        assertThat(primeira.isFirst()).isTrue();

        Page<Post> ultima = postRepository.findAllByOrderByCriadoEmDesc(PageRequest.of(2, 3));
        assertThat(ultima.getContent()).hasSize(1);
        assertThat(ultima.isLast()).isTrue();
    }

    @Test
    @DisplayName("filtra os posts de um autor")
    void buscaPorAutor() {
        novoPost("Do autor 1");
        Usuario outro = usuarioRepository.save(Usuario.builder()
                .nome("Outro").email("outro@hub.infnet.local").papel(Papel.ALUNO).build());
        postRepository.save(Post.builder()
                .titulo("De outro").conteudo("x").autor(outro).curtidas(0).build());

        List<Post> doAutor = postRepository.findByAutorId(autor.getId());

        assertThat(doAutor).hasSize(1);
        assertThat(doAutor.getFirst().getTitulo()).isEqualTo("Do autor 1");
    }

    @Test
    @DisplayName("os carimbos de auditoria são preenchidos automaticamente")
    void carimbosDeAuditoriaPreenchidos() {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

        Post post = novoPost("Com carimbo");

        assertThat(post.getCriadoEm()).isNotNull().isAfter(antes);
        assertThat(post.getAtualizadoEm()).isNotNull();
        assertThat(post.getCriadoPor()).isNotNull();
        assertThat(post.getVersao()).isNotNull();
    }
}
