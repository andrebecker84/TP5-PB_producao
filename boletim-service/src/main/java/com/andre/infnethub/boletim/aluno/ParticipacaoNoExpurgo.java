package com.andre.infnethub.boletim.aluno;

import com.andre.infnethub.boletim.mensageria.CaixaDeSaida;
import com.andre.infnethub.boletim.repository.AvaliacaoRepository;
import com.andre.infnethub.boletim.repository.MatriculaRepository;
import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.expurgo.AlunoAnonimizadoV1;
import com.andre.infnethub.contratos.expurgo.AnonimizacaoRecusadaV1;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * O que o boletim faz quando o core pede a remoção de uma pessoa.
 *
 * <h2>Duas respostas possíveis, e nenhuma delas é "falhei"</h2>
 * <p>Este serviço é participante de uma saga, e participante de saga responde.
 * A regra que decide é acadêmica, não técnica:
 *
 * <ul>
 *   <li><strong>Anonimiza</strong> quando não há competência em avaliação. O
 *       histórico — matrículas, conceitos, carga horária — continua no banco,
 *       agora sem dizer de quem é. É o que a instituição precisa guardar, e é
 *       dado anonimizado, fora do alcance da LGPD (art. 12).</li>
 *   <li><strong>Recusa</strong> quando o aluno está cursando. Desvincular a
 *       identidade de um registro escolar em andamento tornaria impossível
 *       lançar o conceito que falta, emitir declaração ou apurar a aprovação —
 *       e a instituição tem obrigação legal de manter esse registro
 *       identificado até o fim do curso.</li>
 * </ul>
 *
 * <p>A recusa é uma <em>decisão</em>, e por isso viaja como evento e não como
 * exceção. Lançar uma exceção aqui mandaria a mensagem para a fila de mensagens
 * mortas depois de cinco tentativas inúteis, e o pedido de remoção ficaria
 * parado sem que ninguém soubesse por quê. Do jeito que está, o core recebe a
 * resposta, desfaz o bloqueio e avisa quem pediu.
 *
 * <h2>A resposta sai pela caixa de saída</h2>
 * <p>Gravada na mesma transação da anonimização. Ou o boletim anonimizou
 * <em>e</em> a resposta está a caminho, ou nenhuma das duas coisas aconteceu —
 * nunca o estado intermediário em que ele cumpriu a sua parte e o core nunca
 * ficou sabendo, que travaria a saga para sempre.
 */
@Service
@RequiredArgsConstructor
public class ParticipacaoNoExpurgo {

    private static final Logger log = LoggerFactory.getLogger(ParticipacaoNoExpurgo.class);

    private final AlunoReplicaRepository replica;
    private final MatriculaRepository matriculas;
    private final AvaliacaoRepository avaliacoes;
    private final CaixaDeSaida caixa;

    @Transactional
    public void decidirSobre(Long processoId, Long alunoId) {
        long emAberto = avaliacoes.contarCompetenciasEmAberto(alunoId);

        if (emAberto > 0) {
            recusar(processoId, alunoId, "o aluno tem %d competência(s) em avaliação; o registro escolar precisa"
                    .formatted(emAberto) + " permanecer identificado até a conclusão");
            return;
        }

        anonimizar(processoId, alunoId);
    }

    /**
     * Apaga a identidade e mantém o histórico.
     *
     * <p>A réplica é o único lugar deste serviço onde há dado pessoal: as
     * matrículas guardam o id do aluno por valor, sem nome nem e-mail. Remover a
     * linha da réplica é, portanto, remover a identificação — e o que sobra são
     * números ligados a um identificador que não leva a ninguém.
     */
    private void anonimizar(Long processoId, Long alunoId) {
        boolean tinhaReplica = replica.existsById(alunoId);
        if (tinhaReplica) {
            replica.deleteById(alunoId);
        }

        int registrosMantidos = (int) matriculas.countByAlunoId(alunoId);

        caixa.depositar(new AlunoAnonimizadoV1(
                UUID.randomUUID(), Instant.now(), processoId, alunoId, registrosMantidos),
                Canais.ROTA_ALUNO_ANONIMIZADO);

        log.info("aluno {} anonimizado no boletim ({} matrícula(s) mantida(s) sem identificação)",
                alunoId, registrosMantidos);
    }

    private void recusar(Long processoId, Long alunoId, String motivo) {
        caixa.depositar(new AnonimizacaoRecusadaV1(
                UUID.randomUUID(), Instant.now(), processoId, alunoId, motivo),
                Canais.ROTA_ANONIMIZACAO_RECUSADA);

        log.warn("anonimização do aluno {} recusada: {}", alunoId, motivo);
    }
}
