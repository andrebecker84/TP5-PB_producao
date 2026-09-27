package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.MensagemDeIntegracao;
import com.andre.infnethub.observabilidade.Rastreamento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Onde um serviço deposita a mensagem que quer entregar — o fato que anuncia
 * ou o pedido que faz.
 *
 * <h2>O problema que isto resolve: a gravação dupla</h2>
 * <p>O jeito ingênuo de publicar é salvar o usuário e, em seguida, chamar o
 * {@code RabbitTemplate}. São duas escritas em dois sistemas, sem transação que
 * as una, e cada ordem tem sua falha:
 * <ul>
 *   <li>publica e depois o commit falha — os outros serviços ficam sabendo de um
 *       usuário que não existe;</li>
 *   <li>commita e depois o broker está fora — o usuário existe e ninguém fica
 *       sabendo, para sempre.</li>
 * </ul>
 *
 * <p>Aqui o evento não vai para o broker: vai para uma tabela do mesmo banco,
 * dentro da mesma transação do usuário. Uma escrita só, num sistema só — ou as
 * duas linhas existem, ou nenhuma. A entrega ao broker vira problema do
 * {@link RelayDoOutbox}, que pode tentar quantas vezes precisar.
 *
 * <p>{@link Propagation#MANDATORY}: chamar isto fora de uma transação é erro de
 * programação, porque o evento seria gravado sozinho e a garantia inteira
 * deixaria de valer. O método falha alto em vez de fingir que protege.
 */
@Component
@RequiredArgsConstructor
public class CaixaDeSaida {

    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final Rastreamento rastreamento;

    @Transactional(propagation = Propagation.MANDATORY)
    public void depositar(MensagemDeIntegracao mensagem, String rota) {
        agendar(mensagem, rota, Duration.ZERO);
    }

    /**
     * Deposita a mensagem para ser entregue só depois do prazo.
     *
     * <p>O atraso fica gravado com ela, e quem espera é o broker — ver
     * {@link com.andre.infnethub.contratos.SalaDeEspera}.
     * A alternativa seria um agendador neste serviço, que teria de sobreviver a
     * reinícios, não duplicar o disparo com várias instâncias de pé e ainda
     * lidar com o relógio de cada máquina. Nada disso é problema de quem pede o
     * aviso.
     *
     * @param atraso quanto esperar; {@link Duration#ZERO} entrega imediatamente.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void agendar(MensagemDeIntegracao mensagem, String rota, Duration atraso) {
        outbox.save(new MensagemNoOutbox(
                mensagem.mensagemId(),
                mensagem.tipoDaMensagem(),
                rota,
                mensagem.chave(),
                json.writeValueAsString(mensagem),
                Math.max(0, atraso.toMillis()),
                rastreamento.atual()));
    }
}
