package com.andre.infnethub.boletim.aluno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As regras da réplica, sem broker: versão, papel e remoção.
 *
 * <p>O caminho da fila até aqui é coberto por {@link OuvinteDeUsuariosIntegracaoTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Réplica de alunos — o estado mais novo vence")
class ReplicaDeAlunosTest {

    private static final AtomicLong PROXIMO_ALUNO = new AtomicLong(5000);

    @Autowired private ReplicaDeAlunos replica;
    @Autowired private AlunoReplicaRepository repositorio;

    private static AlunoReplica estado(long id, String nome, String papel, long versao) {
        return new AlunoReplica(id, nome, "Faculdade Infnet", "Bloco 5", "26E2",
                papel, "Aluno(a)", versao, LocalDateTime.now());
    }

    @Test
    @DisplayName("o primeiro estado recebido cria a réplica, e o boletim passa a ter o nome")
    void primeiroEstadoCria() {
        long id = PROXIMO_ALUNO.incrementAndGet();
        assertThat(replica.buscar(id).isIndisponivel()).isTrue();

        assertThat(replica.aplicar(estado(id, "Aluno Novo", "ALUNO", 0))).isTrue();

        assertThat(replica.buscar(id).nome()).isEqualTo("Aluno Novo");
    }

    @Test
    @DisplayName("versão mais nova substitui; versão mais velha chegando depois é ignorada")
    void versaoVelhaNaoSobrescreve() {
        long id = PROXIMO_ALUNO.incrementAndGet();
        replica.aplicar(estado(id, "Nome v0", "ALUNO", 0));
        replica.aplicar(estado(id, "Nome v2", "ALUNO", 2));

        // Chegou atrasada — por exemplo, reprocessada da fila de mensagens mortas.
        assertThat(replica.aplicar(estado(id, "Nome v1", "ALUNO", 1))).isFalse();

        AlunoReplica guardada = repositorio.findById(id).orElseThrow();
        assertThat(guardada.getNome()).isEqualTo("Nome v2");
        assertThat(guardada.getVersaoOrigem()).isEqualTo(2);
    }

    @Test
    @DisplayName("a mesma versão aplicada duas vezes não muda nada")
    void mesmaVersaoEhRepeticao() {
        long id = PROXIMO_ALUNO.incrementAndGet();
        replica.aplicar(estado(id, "Original", "ALUNO", 3));

        assertThat(replica.aplicar(estado(id, "Outro nome, mesma versão", "ALUNO", 3))).isFalse();
        assertThat(repositorio.findById(id).orElseThrow().getNome()).isEqualTo("Original");
    }

    @Test
    @DisplayName("quem não é aluno não entra na réplica, e quem deixa de ser aluno sai dela")
    void soAlunosSaoReplicados() {
        long professor = PROXIMO_ALUNO.incrementAndGet();
        assertThat(replica.aplicar(estado(professor, "Prof. Fulano", "PROFESSOR", 0))).isFalse();
        assertThat(repositorio.existsById(professor)).isFalse();

        long ex = PROXIMO_ALUNO.incrementAndGet();
        replica.aplicar(estado(ex, "Virou Monitor", "ALUNO", 0));
        assertThat(replica.aplicar(estado(ex, "Virou Monitor", "PROFESSOR", 1))).isTrue();
        assertThat(repositorio.existsById(ex)).isFalse();
    }

    @Test
    @DisplayName("remover apaga a réplica; remover o que não existe não é erro")
    void remocao() {
        long id = PROXIMO_ALUNO.incrementAndGet();
        replica.aplicar(estado(id, "Será Removido", "ALUNO", 0));

        assertThat(replica.remover(id)).isTrue();
        assertThat(replica.buscar(id).isIndisponivel()).isTrue();
        // A mesma remoção entregue de novo: nada a fazer, e nada quebra.
        assertThat(replica.remover(id)).isFalse();
    }
}
