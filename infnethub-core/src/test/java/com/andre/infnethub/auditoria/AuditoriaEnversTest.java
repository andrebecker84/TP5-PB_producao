package com.andre.infnethub.auditoria;

import com.andre.infnethub.config.AuditoriaConfig;
import com.andre.infnethub.model.Papel;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.history.Revision;
import org.springframework.data.history.RevisionMetadata.RevisionType;
import org.springframework.data.history.Revisions;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria por snapshot com Hibernate Envers.
 *
 * <p>Estes testes rodam <b>sem</b> a transação de teste do Spring
 * ({@code NOT_SUPPORTED}). É indispensável: o Envers grava as revisões no commit
 * da transação, então o rollback automático do {@code @DataJpaTest} faria as
 * tabelas {@code _aud} nunca receberem nada e os testes passariam a verificar o
 * vazio. Sem a transação ambiente, cada {@code save} do repositório commita por
 * conta própria — que é exatamente o comportamento de produção.
 *
 * <p>Como não há rollback, cada teste usa e-mails próprios e só afirma sobre o
 * histórico das entidades que ele mesmo criou.
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Auditoria por snapshot (Hibernate Envers)")
class AuditoriaEnversTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    @AfterEach
    void limparContexto() {
        ContextoAuditoria.limpar();
    }

    private Usuario novoUsuario(String email, String nome) {
        return Usuario.builder()
                .nome(nome).email(email)
                .escola("Faculdade Infnet").classe("26E2")
                .papel(Papel.ALUNO)
                .build();
    }

    @Test
    @DisplayName("criar uma entidade gera uma revisão de criação com o estado completo")
    void criacaoGeraRevisaoComSnapshot() {
        Usuario salvo = usuarioRepository.save(novoUsuario("criacao@hub.infnet.local", "Ana Souza"));

        Revisions<Integer, Usuario> revisoes = usuarioRepository.findRevisions(salvo.getId());

        assertThat(revisoes).hasSize(1);
        Revision<Integer, Usuario> revisao = revisoes.getLatestRevision();
        assertThat(revisao.getMetadata().getRevisionType()).isEqualTo(RevisionType.INSERT);

        // O snapshot é o estado inteiro, não apenas a chave.
        Usuario snapshot = revisao.getEntity();
        assertThat(snapshot.getNome()).isEqualTo("Ana Souza");
        assertThat(snapshot.getEmail()).isEqualTo("criacao@hub.infnet.local");
        assertThat(snapshot.getEscola()).isEqualTo("Faculdade Infnet");
        assertThat(snapshot.getPapel()).isEqualTo(Papel.ALUNO);
    }

    @Test
    @DisplayName("alterar preserva o valor anterior na revisão antiga")
    void alteracaoPreservaEstadoAnterior() {
        Usuario salvo = usuarioRepository.save(novoUsuario("alteracao@hub.infnet.local", "Bruno Lima"));

        Usuario paraAlterar = usuarioRepository.findById(salvo.getId()).orElseThrow();
        paraAlterar.setNome("Bruno Lima Filho");
        paraAlterar.setPapel(Papel.PROFESSOR);
        usuarioRepository.save(paraAlterar);

        List<Revision<Integer, Usuario>> revisoes = usuarioRepository.findRevisions(salvo.getId()).getContent();

        assertThat(revisoes).hasSize(2);

        // Este é o ponto central do histórico por snapshot: a revisão antiga
        // continua respondendo como o registro estava, e não apenas que mudou.
        Usuario antes = revisoes.get(0).getEntity();
        assertThat(revisoes.get(0).getMetadata().getRevisionType()).isEqualTo(RevisionType.INSERT);
        assertThat(antes.getNome()).isEqualTo("Bruno Lima");
        assertThat(antes.getPapel()).isEqualTo(Papel.ALUNO);

        Usuario depois = revisoes.get(1).getEntity();
        assertThat(revisoes.get(1).getMetadata().getRevisionType()).isEqualTo(RevisionType.UPDATE);
        assertThat(depois.getNome()).isEqualTo("Bruno Lima Filho");
        assertThat(depois.getPapel()).isEqualTo(Papel.PROFESSOR);
    }

    @Test
    @DisplayName("excluir mantém o último estado conhecido no histórico")
    void exclusaoPreservaUltimoEstado() {
        Usuario salvo = usuarioRepository.save(novoUsuario("exclusao@hub.infnet.local", "Carla Dias"));
        Long id = salvo.getId();

        usuarioRepository.deleteById(id);

        assertThat(usuarioRepository.findById(id)).isEmpty();

        List<Revision<Integer, Usuario>> revisoes = usuarioRepository.findRevisions(id).getContent();
        assertThat(revisoes).hasSize(2);

        Revision<Integer, Usuario> exclusao = revisoes.get(1);
        assertThat(exclusao.getMetadata().getRevisionType()).isEqualTo(RevisionType.DELETE);

        // Depende de store_data_at_delete=true. No padrão do Envers este snapshot
        // viria só com o id e o resto nulo — inútil para auditoria.
        assertThat(exclusao.getEntity().getNome()).isEqualTo("Carla Dias");
        assertThat(exclusao.getEntity().getEmail()).isEqualTo("exclusao@hub.infnet.local");
    }

    @Test
    @DisplayName("a revisão registra quem fez a alteração")
    void revisaoRegistraAutor() {
        ContextoAuditoria.definirAutor("Prof. Carlos <carlos@hub.infnet.local>");
        ContextoAuditoria.definirOrigem("192.168.0.10");

        Usuario salvo = usuarioRepository.save(novoUsuario("autor@hub.infnet.local", "Diana Reis"));

        RevisaoAuditoria metadados = usuarioRepository.findRevisions(salvo.getId())
                .getLatestRevision().getMetadata().getDelegate();

        assertThat(metadados.getAutor()).isEqualTo("Prof. Carlos <carlos@hub.infnet.local>");
        assertThat(metadados.getOrigem()).isEqualTo("192.168.0.10");
        assertThat(metadados.getDataHora()).isNotNull();
    }

    @Test
    @DisplayName("sem requisição HTTP a alteração é atribuída ao sistema")
    void alteracaoSemContextoEhAtribuidaAoSistema() {
        Usuario salvo = usuarioRepository.save(novoUsuario("sistema@hub.infnet.local", "Eduardo Melo"));

        RevisaoAuditoria metadados = usuarioRepository.findRevisions(salvo.getId())
                .getLatestRevision().getMetadata().getDelegate();

        assertThat(metadados.getAutor()).isEqualTo(ContextoAuditoria.AUTOR_SISTEMA);
    }

    @Test
    @DisplayName("é possível recuperar o estado em uma revisão específica")
    void recuperaEstadoEmRevisaoEspecifica() {
        Usuario salvo = usuarioRepository.save(novoUsuario("especifica@hub.infnet.local", "Original"));

        Usuario paraAlterar = usuarioRepository.findById(salvo.getId()).orElseThrow();
        paraAlterar.setNome("Renomeado");
        usuarioRepository.save(paraAlterar);

        Integer primeiraRevisao = usuarioRepository.findRevisions(salvo.getId())
                .getContent().get(0).getRevisionNumber().orElseThrow();

        Optional<Revision<Integer, Usuario>> revisao =
                usuarioRepository.findRevision(salvo.getId(), primeiraRevisao);

        assertThat(revisao).isPresent();
        assertThat(revisao.get().getEntity().getNome()).isEqualTo("Original");
    }

    @Test
    @DisplayName("findLastChangeRevision devolve a revisão mais recente")
    void ultimaRevisao() {
        Usuario salvo = usuarioRepository.save(novoUsuario("ultima@hub.infnet.local", "Primeiro Nome"));

        Usuario paraAlterar = usuarioRepository.findById(salvo.getId()).orElseThrow();
        paraAlterar.setNome("Nome Atual");
        usuarioRepository.save(paraAlterar);

        Revision<Integer, Usuario> ultima = usuarioRepository.findLastChangeRevision(salvo.getId()).orElseThrow();

        assertThat(ultima.getEntity().getNome()).isEqualTo("Nome Atual");
        assertThat(ultima.getMetadata().getRevisionType()).isEqualTo(RevisionType.UPDATE);
    }
}
