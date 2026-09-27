package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.dto.AtividadeDTO;
import com.andre.infnethub.boletim.dto.AtividadeRequestDTO;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extensão, eletivas, estágio e atividades complementares.
 *
 * <p>Sem autenticação — adiada para etapa posterior —, a checagem de posse
 * dentro do serviço é a única coisa separando os dados de um aluno dos de
 * outro. É o teste mais importante desta classe.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AtividadeService — atividades acadêmicas e integralização")
class AtividadeServiceTest {

    private static final AtomicInteger PROXIMO_ALUNO = new AtomicInteger(3000);

    @Autowired private AtividadeService atividadeService;

    private long novoAluno() {
        return PROXIMO_ALUNO.incrementAndGet();
    }

    private AtividadeRequestDTO pedido(String categoria, String nome, int carga,
                                       String status, Integer presenca) {
        return new AtividadeRequestDTO(categoria, nome, carga, "26E2", status, presenca);
    }

    @Test
    @DisplayName("só as atividades CONCLUÍDAS contam carga para a integralização")
    void somenteConcluidasContam() {
        long aluno = novoAluno();
        atividadeService.registrar(aluno, pedido("EXTENSAO", "Projeto concluído", 120, "CONCLUIDO", null));
        atividadeService.registrar(aluno, pedido("EXTENSAO", "Projeto em curso", 200, "EM_CURSO", null));
        atividadeService.registrar(aluno, pedido("EXTENSAO", "Projeto não iniciado", 80, "NAO_CONCLUIDO", null));

        var extensao = atividadeService.apurarCargaHoraria(aluno).stream()
                .filter(c -> c.categoria().equals("EXTENSAO")).findFirst().orElseThrow();

        assertThat(extensao.concluida()).isEqualTo(120);
        assertThat(extensao.exigida()).isEqualTo(400);
        assertThat(extensao.percentual()).isEqualTo(30);
    }

    @Test
    @DisplayName("a eletiva fica fora da carga horária — integra a das disciplinas, não um total próprio")
    void eletivaNaoTemMetaPropria() {
        long aluno = novoAluno();
        atividadeService.registrar(aluno, pedido("ELETIVA", "Computação em Nuvem", 40, "CONCLUIDO", 92));

        // Sem esta exclusão ela apareceria como 0 de 0, uma barra que nunca sai
        // do lugar no painel.
        assertThat(atividadeService.apurarCargaHoraria(aluno))
                .extracting("categoria")
                .containsExactly("EXTENSAO", "ESTAGIO", "COMPLEMENTAR");
    }

    @Test
    @DisplayName("a presença é guardada na eletiva e descartada nas demais categorias")
    void presencaSoNaEletiva() {
        long aluno = novoAluno();

        AtividadeDTO eletiva = atividadeService.registrar(aluno,
                pedido("ELETIVA", "Segurança de Aplicações", 40, "CONCLUIDO", 86));
        // Um estágio com presença gravaria um número que nenhuma regra consulta
        // — dado que existe mas não significa nada.
        AtividadeDTO estagio = atividadeService.registrar(aluno,
                pedido("ESTAGIO", "Estágio supervisionado", 220, "CONCLUIDO", 99));

        assertThat(eletiva.presencaPercentual()).isEqualTo(86);
        assertThat(estagio.presencaPercentual()).isNull();
    }

    @Test
    @DisplayName("um aluno não altera nem remove a atividade de outro")
    void isolamentoEntreAlunos() {
        long dono = novoAluno();
        long intruso = novoAluno();
        AtividadeDTO criada = atividadeService.registrar(dono,
                pedido("COMPLEMENTAR", "Certificação", 30, "CONCLUIDO", null));

        assertThatThrownBy(() -> atividadeService.remover(intruso, criada.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> atividadeService.atualizar(intruso, criada.id(),
                pedido("COMPLEMENTAR", "Sequestrada", 30, "CONCLUIDO", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        // E continua intacta para o dono.
        assertThat(atividadeService.listarPorAluno(dono))
                .extracting("nome").containsExactly("Certificação");
    }

    @Test
    @DisplayName("categoria desconhecida é recurso não encontrado, não erro de servidor")
    void categoriaInvalida() {
        assertThatThrownBy(() -> atividadeService.listarPorCategoria(novoAluno(), "XPTO"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("XPTO");
    }

    @Test
    @DisplayName("o ciclo completo de criação, atualização e remoção")
    void cicloCompleto() {
        long aluno = novoAluno();
        AtividadeDTO criada = atividadeService.registrar(aluno,
                pedido("COMPLEMENTAR", "Monitoria", 30, "EM_CURSO", null));
        assertThat(criada.statusDescricao()).isEqualTo("Em curso");

        AtividadeDTO atualizada = atividadeService.atualizar(aluno, criada.id(),
                pedido("COMPLEMENTAR", "Monitoria de Estruturas de Dados", 40, "CONCLUIDO", null));
        assertThat(atualizada.nome()).isEqualTo("Monitoria de Estruturas de Dados");
        assertThat(atualizada.cargaHoraria()).isEqualTo(40);

        atividadeService.remover(aluno, criada.id());
        assertThat(atividadeService.listarPorAluno(aluno)).isEmpty();
    }
}
