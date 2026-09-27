package com.andre.infnethub.service;

import com.andre.infnethub.dto.PostRequestDTO;
import com.andre.infnethub.dto.PostResponseDTO;
import com.andre.infnethub.model.Papel;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.PostRepository;
import com.andre.infnethub.repository.UsuarioRepository;
import com.andre.infnethub.seguranca.Solicitante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Autoria dos posts: quem publica é quem pede, e só o autor ou a moderação
 * alteram. Cobre o defeito da 1.0.0 em que o autor vinha do corpo da
 * requisição e qualquer conta editava ou apagava o post de outra.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PostService — autoria vem do token")
class PostServiceAutoriaTest {

    @Autowired
    private PostService postService;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private Solicitante autor;
    private Solicitante outro;
    private Solicitante secretaria;

    @BeforeEach
    void preparar() {
        long marca = System.nanoTime();
        autor = new Solicitante(salvar("Autor", "autor", marca, Papel.ALUNO), false);
        outro = new Solicitante(salvar("Outro", "outro", marca, Papel.ALUNO), false);
        secretaria = new Solicitante(salvar("Secretaria", "secretaria", marca, Papel.SECRETARIA), true);
    }

    private Long salvar(String nome, String prefixo, long marca, Papel papel) {
        return usuarioRepository.save(Usuario.builder()
                .nome(nome).email("%s-%d@hub.infnet.local".formatted(prefixo, marca))
                .papel(papel).build()).getId();
    }

    private PostResponseDTO publicar() {
        return postService.criar(new PostRequestDTO("Título", "conteúdo", null), autor);
    }

    @Test
    @DisplayName("o post é gravado em nome de quem pede")
    void autorEhQuemPede() {
        assertThat(publicar().autorId()).isEqualTo(autor.id());
    }

    @Test
    @DisplayName("outra conta não edita nem apaga o post")
    void outraContaEhBarrada() {
        Long id = publicar().id();
        PostRequestDTO alteracao = new PostRequestDTO("Invadido", "x", null);

        assertThatThrownBy(() -> postService.atualizar(id, alteracao, outro))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> postService.deletar(id, outro))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(postRepository.findById(id)).get()
                .extracting("titulo").isEqualTo("Título");
    }

    @Test
    @DisplayName("o autor e a moderação alteram")
    void autorEModeracaoAlteram() {
        Long id = publicar().id();

        assertThat(postService.atualizar(id, new PostRequestDTO("Revisto", "c", null), autor).titulo())
                .isEqualTo("Revisto");
        postService.deletar(id, secretaria);

        assertThat(postRepository.existsById(id)).isFalse();
    }
}
