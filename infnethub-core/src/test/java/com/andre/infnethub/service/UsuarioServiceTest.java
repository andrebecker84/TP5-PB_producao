package com.andre.infnethub.service;

import com.andre.infnethub.dto.UsuarioRequestDTO;
import com.andre.infnethub.dto.UsuarioResponseDTO;
import com.andre.infnethub.exception.ConflitoDeDadosException;
import com.andre.infnethub.model.Papel;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras de integridade de {@link com.andre.infnethub.model.Usuario}.
 *
 * <p>Cobre um defeito herdado do TP1: o serviço gravava sem verificar o e-mail,
 * então a constraint do banco estourava crua e o cliente recebia 500 — erro de
 * servidor para o que é, na verdade, uma regra de negócio funcionando.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("UsuarioService — unicidade de e-mail e busca")
class UsuarioServiceTest {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;

    /** O DTO não expõe papel: quem entra pela API nasce ALUNO, por padrão da entidade. */
    private UsuarioRequestDTO novo(String nome, String email) {
        return new UsuarioRequestDTO(nome, email, "Faculdade Infnet", "Bloco 5", "26E2");
    }

    @Test
    @DisplayName("e-mail duplicado vira conflito de domínio, não erro de servidor")
    void emailDuplicadoNaCriacao() {
        String email = "duplicado-%d@hub.infnet.local".formatted(System.nanoTime());
        usuarioService.criar(novo("Primeiro", email));

        assertThatThrownBy(() -> usuarioService.criar(novo("Segundo", email)))
                .isInstanceOf(ConflitoDeDadosException.class)
                .hasMessageContaining(email);
    }

    @Test
    @DisplayName("atualizar mantendo o próprio e-mail é permitido")
    void atualizarMantendoProprioEmail() {
        String email = "proprio-%d@hub.infnet.local".formatted(System.nanoTime());
        UsuarioResponseDTO criado = usuarioService.criar(novo("Nome Original", email));

        // O e-mail não mudou: não pode ser tratado como conflito consigo mesmo.
        UsuarioResponseDTO atualizado = usuarioService.atualizar(criado.id(), novo("Nome Alterado", email));

        assertThat(atualizado.nome()).isEqualTo("Nome Alterado");
        assertThat(atualizado.email()).isEqualTo(email);
    }

    @Test
    @DisplayName("atualizar para o e-mail de outro usuário é rejeitado")
    void atualizarParaEmailDeOutro() {
        long marca = System.nanoTime();
        String emailA = "a-%d@hub.infnet.local".formatted(marca);
        String emailB = "b-%d@hub.infnet.local".formatted(marca);
        usuarioService.criar(novo("Usuário A", emailA));
        UsuarioResponseDTO b = usuarioService.criar(novo("Usuário B", emailB));

        assertThatThrownBy(() -> usuarioService.atualizar(b.id(), novo("Usuário B", emailA)))
                .isInstanceOf(ConflitoDeDadosException.class)
                .hasMessageContaining("já pertence a outro usuário");
    }

    @Test
    @DisplayName("busca encontra por nome e por e-mail, sem diferenciar maiúsculas")
    void buscaPorTermo() {
        long marca = System.nanoTime();
        usuarioService.criar(novo("Zoraide Buscavel", "zoraide-%d@hub.infnet.local".formatted(marca)));

        assertThat(usuarioService.buscar("zoraide")).hasSize(1);
        assertThat(usuarioService.buscar("BUSCAVEL")).hasSize(1);
        assertThat(usuarioService.buscar("zoraide-%d@hub".formatted(marca))).hasSize(1);
        assertThat(usuarioService.buscar("inexistente-xyz")).isEmpty();
    }

    @Test
    @DisplayName("filtro por papel devolve apenas quem tem aquele papel")
    void filtroPorPapel() {
        // Gravado pelo repositório porque o DTO da API não expõe papel.
        usuarioRepository.save(Usuario.builder()
                .nome("Coordenador Teste")
                .email("coord-%d@hub.infnet.local".formatted(System.nanoTime()))
                .escola("Faculdade Infnet")
                .papel(Papel.COORDENADOR)
                .build());

        List<UsuarioResponseDTO> coordenadores = usuarioService.listarPorPapel(Papel.COORDENADOR);

        assertThat(coordenadores).isNotEmpty();
        assertThat(coordenadores).allMatch(u -> u.papel().equals(Papel.COORDENADOR.name()));
    }
}
