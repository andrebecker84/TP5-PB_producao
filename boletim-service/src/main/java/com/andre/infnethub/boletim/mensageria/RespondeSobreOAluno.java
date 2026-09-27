package com.andre.infnethub.boletim.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.consulta.SituacaoAcademicaPerguntaV1;
import com.andre.infnethub.contratos.consulta.SituacaoAcademicaRespostaV1;
import com.andre.infnethub.boletim.repository.AvaliacaoRepository;
import com.andre.infnethub.boletim.repository.MatriculaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * O lado que responde no padrão <em>request/reply</em>.
 *
 * <h2>A resposta não precisa de código para voltar</h2>
 * <p>Este método simplesmente <strong>retorna</strong> um objeto, e ele chega a
 * quem perguntou. Quem faz isso é o Spring AMQP: a mensagem da pergunta traz
 * dois cabeçalhos do protocolo — {@code reply-to}, com o endereço da resposta,
 * e {@code correlation-id}, que a liga à pergunta certa —, e o retorno do
 * método é publicado naquele endereço com aquele identificador. É o que a
 * rúbrica chama de "abstração do Spring Boot": o padrão inteiro cabe num
 * {@code @RabbitListener} que devolve um valor.
 *
 * <p>O {@code correlation-id} não é detalhe. Quem pergunta pode ter dez
 * perguntas em voo pela mesma conexão; sem ele, não haveria como saber qual
 * resposta é de qual — e a alternativa, uma fila de resposta por pergunta,
 * custaria uma fila criada e destruída a cada clique.
 *
 * <h2>Leitura, e só</h2>
 * <p>{@code readOnly}: responder não muda nada. A pergunta pode ser repetida à
 * vontade, e por isso não há controle de duplicidade aqui — a idempotência sai
 * de graça quando a operação não tem efeito.
 */
@Component
@RequiredArgsConstructor
class RespondeSobreOAluno {

    private static final Logger log = LoggerFactory.getLogger(RespondeSobreOAluno.class);

    private final MatriculaRepository matriculas;
    private final AvaliacaoRepository avaliacoes;

    @RabbitListener(queues = Canais.FILA_BOLETIM_CONSULTAS)
    @Transactional(readOnly = true)
    SituacaoAcademicaRespostaV1 situacao(SituacaoAcademicaPerguntaV1 pergunta) {
        long quantasMatriculas = matriculas.countByAlunoId(pergunta.alunoId());
        int quantosConceitos = avaliacoes.findConceitosLancados(pergunta.alunoId()).size();

        log.info("situação do aluno {}: {} matrícula(s), {} conceito(s)",
                pergunta.alunoId(), quantasMatriculas, quantosConceitos);

        return SituacaoAcademicaRespostaV1.de(pergunta.alunoId(), (int) quantasMatriculas, quantosConceitos);
    }
}
