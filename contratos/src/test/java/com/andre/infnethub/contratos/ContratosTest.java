package com.andre.infnethub.contratos;

import com.andre.infnethub.contratos.comando.ComandoDeIntegracao;
import com.andre.infnethub.contratos.comando.EnviarAvisoV1;
import com.andre.infnethub.contratos.usuario.UsuarioCadastradoV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O catálogo de contratos, verificado pelo compilador e por reflexão.
 *
 * <p>Existe por causa de um defeito real: {@code VagaPublicadaV1} tem um campo
 * {@code tipo} (o tipo da vaga), e enquanto o método da interface se chamava
 * {@code tipo()}, o acessor do record o sobrescreveu em silêncio. O evento foi
 * gravado na caixa de saída anunciando-se como "ESTAGIO", e o relay não achou
 * contrato com esse nome — o evento ficou retido, tentativa após tentativa.
 *
 * <p>Nada disso falha na compilação: sobrescrever um método padrão é legítimo.
 * Só um teste apanha.
 */
@DisplayName("Contratos de mensagem")
class ContratosTest {

    @Test
    @DisplayName("nenhum contrato sobrescreve o nome do próprio tipo")
    void nenhumContratoSobrescreveOTipo() {
        for (Class<? extends MensagemDeIntegracao> contrato : Contratos.todos()) {
            assertThat(Arrays.stream(contrato.getDeclaredMethods()).map(Method::getName))
                    .as("o contrato %s declara um membro chamado tipoDaMensagem, que esconderia o nome do tipo",
                            contrato.getSimpleName())
                    .doesNotContain("tipoDaMensagem");
        }
    }

    @Test
    @DisplayName("o catálogo indexa cada contrato pelo nome da própria classe")
    void catalogoIndexadoPeloNomeDaClasse() {
        for (Class<? extends MensagemDeIntegracao> contrato : Contratos.todos()) {
            assertThat(Contratos.classeDe(contrato.getSimpleName())).contains(contrato);
        }
    }

    @Test
    @DisplayName("tipo desconhecido não vira classe — o conteúdo da tabela não escolhe o que carregar")
    void tipoDesconhecido() {
        assertThat(Contratos.classeDe("ESTAGIO")).isEmpty();
        assertThat(Contratos.classeDe("java.lang.Runtime")).isEmpty();
    }

    @Test
    @DisplayName("todo contrato é um record, imutável, e implementa o contrato base")
    void contratosSaoRecords() {
        assertThat(Contratos.todos()).isNotEmpty().allSatisfy(contrato -> {
            assertThat(contrato.isRecord()).as("%s deveria ser record", contrato.getSimpleName()).isTrue();
            assertThat(MensagemDeIntegracao.class).isAssignableFrom(contrato);
        });
    }

    @Test
    @DisplayName("todo contrato é ou fato ou pedido, nunca os dois")
    void fatoOuPedido() {
        assertThat(Contratos.todos()).allSatisfy(contrato -> {
            boolean evento = EventoDeIntegracao.class.isAssignableFrom(contrato);
            boolean comando = ComandoDeIntegracao.class.isAssignableFrom(contrato);
            assertThat(evento ^ comando)
                    .as("%s precisa ser exatamente um dos dois (evento=%s, comando=%s)",
                            contrato.getSimpleName(), evento, comando)
                    .isTrue();
        });
    }

    @Test
    @DisplayName("a exchange sai do tipo da mensagem: fato na topic, pedido na direct")
    void exchangeVemDoTipo() {
        var evento = new UsuarioCadastradoV1(UUID.randomUUID(), Instant.now(), 1L, 1L,
                "Lucas Mendonça", "Infnet", "Bloco 4", "26E2", "ALUNO", "Aluno");
        var comando = new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), List.of(1L), "Aula cancelada", null, 3L);

        assertThat(Contratos.exchangeDe(evento)).isEqualTo(Canais.EXCHANGE_EVENTOS);
        assertThat(Contratos.exchangeDe(comando)).isEqualTo(Canais.EXCHANGE_COMANDOS);
    }

    @Test
    @DisplayName("aviso sem lista de destinatários vale para toda a turma")
    void avisoSemDestinatarios() {
        var paraTodos = new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), null, "Prazo prorrogado", null, 3L);
        var paraUm = new EnviarAvisoV1(UUID.randomUUID(), Instant.now(), List.of(7L), "Sua matrícula", null, 3L);

        assertThat(paraTodos.paraTodosOsAlunos()).isTrue();
        assertThat(paraTodos.destinatarios()).isEmpty();
        assertThat(paraUm.paraTodosOsAlunos()).isFalse();
    }
}
