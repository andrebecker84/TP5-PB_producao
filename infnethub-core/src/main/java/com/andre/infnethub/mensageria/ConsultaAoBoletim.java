package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.consulta.SituacaoAcademicaPerguntaV1;
import com.andre.infnethub.contratos.consulta.SituacaoAcademicaRespostaV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * O lado que pergunta — e que sabe desistir.
 *
 * <h2>O único ponto síncrono que sobrou, de propósito</h2>
 * <p>Todo o resto do TP4 caminha na direção oposta: publicar e seguir em
 * frente. Aqui o core para e espera. A justificativa está no contrato
 * ({@link SituacaoAcademicaPerguntaV1}), e o que este componente faz é conter o
 * dano:
 * <ul>
 *   <li><strong>espera curta e explícita</strong> — três segundos, não o padrão
 *       de cinco; a pessoa está olhando uma tela;</li>
 *   <li><strong>a mensagem também tem validade</strong> — se a pergunta ficar
 *       parada na fila além do prazo, o broker a descarta em vez de fazer o
 *       boletim responder a alguém que já foi embora;</li>
 *   <li><strong>a falha tem nome</strong> — não respondeu vira
 *       "não consultado", e não "nada consta". Confundir os dois é o que faria
 *       a secretaria excluir uma pessoa com matrícula ativa.</li>
 * </ul>
 *
 * <h2>Por que o template é construído aqui</h2>
 * <p>E não declarado como bean: um segundo {@code RabbitTemplate} no contexto
 * faz a autoconfiguração do Spring Boot recuar, e o {@link RelayDoOutbox} —
 * que depende de confirmações de publicação configuradas por ela — passaria a
 * receber este, com outro comportamento. Um template local resolve, e deixa
 * claro que este tempo de espera vale só para as perguntas.
 */
@Component
public class ConsultaAoBoletim {

    private static final Logger log = LoggerFactory.getLogger(ConsultaAoBoletim.class);

    private final RabbitTemplate perguntas;

    public ConsultaAoBoletim(ConnectionFactory conexao,
                             MessageConverter conversor,
                             @Value("${app.consulta.espera-ms:3000}") long esperaMs) {
        this.perguntas = new RabbitTemplate(conexao);
        this.perguntas.setMessageConverter(conversor);
        this.perguntas.setReplyTimeout(esperaMs);
        // Sem isto, o tempo de espera de quem pergunta e a validade da mensagem
        // na fila seriam independentes — e a pergunta poderia ser respondida
        // depois de o core ter desistido dela.
        this.perguntas.setBeforePublishPostProcessors(mensagem -> {
            mensagem.getMessageProperties().setExpiration(String.valueOf(Canais.VALIDADE_DA_PERGUNTA_MS));
            mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
            return mensagem;
        });
    }

    /**
     * Pergunta ao boletim o que ele sabe sobre o aluno.
     *
     * <p>Nunca lança: a indisponibilidade do boletim é uma resposta possível
     * deste método, não um erro da chamada. Quem chama decide o que fazer com
     * ela — e, no caso da exclusão, decide não excluir.
     */
    public SituacaoAcademicaRespostaV1 situacaoDe(Long alunoId) {
        try {
            Object resposta = perguntas.convertSendAndReceive(
                    Canais.EXCHANGE_CONSULTAS,
                    Canais.ROTA_SITUACAO_ACADEMICA,
                    new SituacaoAcademicaPerguntaV1(alunoId));

            if (resposta instanceof SituacaoAcademicaRespostaV1 situacao) {
                return situacao;
            }
            // Nulo é como o RabbitTemplate relata o tempo esgotado.
            log.warn("boletim não respondeu sobre o aluno {} dentro do prazo", alunoId);
            return SituacaoAcademicaRespostaV1.semResposta(alunoId);

        } catch (AmqpException e) {
            log.warn("não foi possível consultar o boletim sobre o aluno {}: {}", alunoId, e.getMessage());
            return SituacaoAcademicaRespostaV1.semResposta(alunoId);
        }
    }
}
