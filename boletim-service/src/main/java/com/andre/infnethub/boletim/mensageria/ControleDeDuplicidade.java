package com.andre.infnethub.boletim.mensageria;

import com.andre.infnethub.contratos.MensagemDeIntegracao;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decide se uma mensagem deve produzir efeito ou ser descartada como repetição.
 *
 * <p>É a metade da garantia que o broker não dá. O outbox do core publica
 * <em>pelo menos uma vez</em>, e o RabbitMQ entrega de novo toda mensagem cujo
 * ack não chegou — inclusive a que foi processada e gravada, mas cujo serviço
 * caiu antes de confirmar. Para o broker, ela nunca foi consumida.
 *
 * <p>Por isso quem garante o efeito único é o consumidor, e a garantia precisa
 * estar onde o efeito é gravado: na mesma transação. {@link Propagation#MANDATORY}
 * faz disso uma regra, e não uma recomendação — chamado fora de transação, o
 * método falha em vez de dar uma falsa proteção.
 */
@Component
@RequiredArgsConstructor
public class ControleDeDuplicidade {

    private static final Logger log = LoggerFactory.getLogger(ControleDeDuplicidade.class);

    private final MensagensProcessadasRepository repositorio;

    /**
     * @return {@code true} se é a primeira vez e o efeito deve acontecer;
     *         {@code false} se é repetição e deve ser descartada.
     *
     * <p>Duas cópias processadas ao mesmo tempo passariam as duas pela consulta.
     * Quem resolve é a chave primária: o segundo commit viola a restrição, a
     * transação inteira volta atrás — efeito incluso —, a mensagem é reentregue
     * e, na volta, a consulta já enxerga o registro. O código escolhe o caminho
     * barato; o banco garante o caro.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean primeiraVez(MensagemDeIntegracao mensagem) {
        if (repositorio.existsById(mensagem.mensagemId())) {
            log.info("{} {} já processado; descartado", mensagem.tipoDaMensagem(), mensagem.mensagemId());
            return false;
        }
        repositorio.save(new MensagemProcessada(mensagem.mensagemId(), mensagem.tipoDaMensagem()));
        return true;
    }
}
