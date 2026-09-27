package com.andre.infnethub.repository;

import com.andre.infnethub.config.AuditoriaConfig;
import com.andre.infnethub.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garantias de integridade sustentadas pelo banco.
 *
 * <p>Cada regra aqui é verificada no banco, e não apenas na aplicação: validação
 * em código é conferida antes da escrita e não impede que duas requisições
 * concorrentes passem juntas pela checagem. A constraint é o que realmente
 * decide.
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@ActiveProfiles("test")
@DisplayName("Integridade dos dados")
class IntegridadeDadosTest {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CurtidaRepository curtidaRepository;
    @Autowired
    private EntityManager entityManager;

    private Usuario usuario;
    private Post post;

    @BeforeEach
    void preparar() {
        usuario = usuarioRepository.save(Usuario.builder()
                .nome("Usuário Base").email("base@hub.infnet.local").papel(Papel.ALUNO).build());
        post = postRepository.save(Post.builder()
                .titulo("Post base").conteudo("conteúdo").autor(usuario).curtidas(0).build());
    }

    @Test
    @DisplayName("e-mail duplicado é barrado pela constraint de unicidade")
    void emailDuplicadoEhRejeitado() {
        Usuario duplicado = Usuario.builder()
                .nome("Outro Nome").email("base@hub.infnet.local").papel(Papel.PROFESSOR).build();

        assertThatThrownBy(() -> {
            usuarioRepository.save(duplicado);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a mesma pessoa não curte o mesmo post duas vezes")
    void curtidaDuplicadaEhRejeitada() {
        curtidaRepository.save(Curtida.builder().post(post).usuario(usuario).build());

        assertThatThrownBy(() -> {
            curtidaRepository.save(Curtida.builder().post(post).usuario(usuario).build());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("post sem autor é rejeitado")
    void postSemAutorEhRejeitado() {
        Post orfao = Post.builder().titulo("Órfão").conteudo("sem autor").curtidas(0).build();

        assertThatThrownBy(() -> {
            postRepository.save(orfao);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("conteúdo obrigatório é exigido pelo banco")
    void conteudoNuloEhRejeitado() {
        Post semConteudo = Post.builder().titulo("Sem conteúdo").autor(usuario).curtidas(0).build();

        assertThatThrownBy(() -> {
            postRepository.save(semConteudo);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("a versão avança a cada alteração, habilitando o bloqueio otimista")
    void versaoIncrementaACadaAlteracao() {
        Long versaoInicial = post.getVersao();
        assertThat(versaoInicial).isNotNull();

        post.setTitulo("Título alterado");
        postRepository.saveAndFlush(post);

        assertThat(post.getVersao()).isGreaterThan(versaoInicial);
    }
}
