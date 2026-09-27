package com.andre.infnethub.boletim.aluno;

import com.andre.infnethub.boletim.mensageria.MensagemNoOutbox;
import com.andre.infnethub.boletim.mensageria.OutboxRepository;
import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Conceito;
import com.andre.infnethub.boletim.CenarioAcademico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A decisão do boletim na saga de expurgo: anonimizar ou recusar.
 *
 * <p>É a regra que decide o destino de um pedido de remoção, e ela não é
 * técnica — é acadêmica: não se desvincula a identidade de um registro escolar
 * que ainda está em curso. Por isso o teste monta os dois cenários acadêmicos
 * de verdade, com matrícula e conceitos, em vez de simular a contagem.
 */
@SpringBootTest(properties = "app.outbox.relay.habilitado=false")
@ActiveProfiles("test")
@DisplayName("Participação do boletim na saga de expurgo")
class ParticipacaoNoExpurgoTest {

    private static final AtomicInteger PROXIMO_BLOCO = new AtomicInteger(900);
    private static final AtomicInteger PROXIMO_ALUNO = new AtomicInteger(7000);
    /** O processo do core a que as respostas devem se referir. */
    private static final long PROCESSO = 4242L;

    @Autowired private ParticipacaoNoExpurgo participacao;
    @Autowired private AlunoReplicaRepository repositorio;
    @Autowired private OutboxRepository outbox;
    @Autowired private CenarioAcademico cenario;

    private List<String> respostasSobre(long alunoId) {
        return outbox.findByChaveOrderByIdAsc(String.valueOf(alunoId))
                .stream().map(MensagemNoOutbox::getTipo).toList();
    }

    /** Um aluno com a réplica sincronizada e um bloco só dele. */
    private long alunoCom(Conceito... conceitos) {
        long id = PROXIMO_ALUNO.incrementAndGet();
        cenario.alunoSincronizado(id, "Aluno " + id);
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco do expurgo " + id);
        var matricula = cenario.matricula(id, cenario.disciplina(bloco, "Disciplina", 60, false),
                "26E2", 90, 4, 0, 0);
        cenario.avaliar(matricula, conceitos);
        return id;
    }

    @Test
    @DisplayName("aluno com tudo lançado é anonimizado, e o histórico acadêmico fica")
    void anonimizaQuemTerminou() {
        long id = alunoCom(Conceito.DL, Conceito.DML);
        assertThat(repositorio.existsById(id)).isTrue();

        participacao.decidirSobre(PROCESSO, id);

        assertThat(repositorio.existsById(id))
                .as("a identidade sai da réplica — é o único dado pessoal deste serviço")
                .isFalse();
        assertThat(respostasSobre(id)).containsExactly("AlunoAnonimizadoV1");
    }

    @Test
    @DisplayName("a confirmação diz quantos registros ficaram guardados sem identificação")
    void confirmacaoInformaOQueFicou() {
        long id = alunoCom(Conceito.DL);

        participacao.decidirSobre(PROCESSO, id);

        MensagemNoOutbox resposta = outbox.findByChaveOrderByIdAsc(String.valueOf(id)).getFirst();
        assertThat(resposta.getPayload())
                // O processo volta como veio: é por ele que o core casa a
                // resposta com o pedido, e não pela pessoa.
                .contains("\"processoId\":" + PROCESSO)
                .contains("\"usuarioId\":" + id)
                .contains("\"registrosMantidos\":1");
    }

    @Test
    @DisplayName("aluno cursando é recusado, com motivo — e a réplica NÃO é tocada")
    void recusaQuemEstaCursando() {
        // Conceito nulo: a competência ainda está em avaliação.
        long id = alunoCom(Conceito.DL, null);

        participacao.decidirSobre(PROCESSO, id);

        assertThat(respostasSobre(id)).containsExactly("AnonimizacaoRecusadaV1");
        assertThat(outbox.findByChaveOrderByIdAsc(String.valueOf(id)).getFirst().getPayload())
                .contains("competência");

        // O ponto da recusa: nada foi feito. Se a réplica tivesse sido apagada
        // antes de o core desfazer o bloqueio, a compensação devolveria uma
        // pessoa sem boletim.
        assertThat(repositorio.existsById(id))
                .as("recusar é não mexer")
                .isTrue();
    }

    @Test
    @DisplayName("a resposta nasce na caixa de saída, e não direto no broker")
    void respostaVaiPelaCaixaDeSaida() {
        long id = alunoCom(Conceito.DML);

        participacao.decidirSobre(PROCESSO, id);

        // É o que garante que o boletim nunca anonimize sem o core ficar
        // sabendo — o estado que travaria a saga para sempre.
        MensagemNoOutbox resposta = outbox.findByChaveOrderByIdAsc(String.valueOf(id)).getFirst();
        assertThat(resposta.getTipo()).isEqualTo("AlunoAnonimizadoV1");
        assertThat(resposta.getRota()).isEqualTo("aluno.anonimizado");
        assertThat(resposta.isPublicada()).isFalse();
    }
}
