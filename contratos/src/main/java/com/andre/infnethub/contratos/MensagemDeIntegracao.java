package com.andre.infnethub.contratos;

import java.time.Instant;
import java.util.UUID;

/**
 * O que é comum a tudo que atravessa o broker, seja fato ou pedido.
 *
 * <p>Os três metadados abaixo existem em toda mensagem, e cada um responde a
 * uma pergunta que o consumidor inevitavelmente faz:
 * <ul>
 *   <li>{@link #mensagemId()} — <em>já processei isto?</em> A entrega é pelo
 *       menos uma vez, e é por este id que o consumidor descarta a repetição.</li>
 *   <li>{@link #ocorridoEm()} — <em>de quando é?</em> O momento do fato ou do
 *       pedido, não o da entrega, que pode vir horas depois se o consumidor
 *       estava fora.</li>
 *   <li>{@link #chave()} — <em>sobre o que é?</em> O agregado a que a mensagem
 *       se refere, usado para correlacionar e para ordenar.</li>
 * </ul>
 *
 * <p>A divisão em {@link EventoDeIntegracao} e
 * {@link com.andre.infnethub.contratos.comando.ComandoDeIntegracao} não é
 * decoração: ela decide por qual exchange a mensagem sai, e o tipo aqui é o que
 * permite ao relay descobrir isso sozinho, sem uma coluna a mais na caixa de
 * saída dizendo o óbvio.
 */
public interface MensagemDeIntegracao {

    UUID mensagemId();

    Instant ocorridoEm();

    String chave();

    /**
     * Nome versionado do contrato, que viaja no cabeçalho {@code type} da
     * mensagem e identifica a classe a instanciar do outro lado.
     *
     * <p>O nome é {@code tipoDaMensagem}, e não {@code tipo}, por um motivo
     * concreto: um contrato pode ter um campo chamado {@code tipo} — o tipo de
     * uma vaga, por exemplo —, e o acessor do record <em>sobrescreve</em> o
     * método padrão da interface, sem erro de compilação e sem aviso. A
     * mensagem passaria a se anunciar como "ESTAGIO", e o consumidor não
     * encontraria contrato com esse nome. Aconteceu, e o teste de contratos
     * existe para que não volte a acontecer.
     */
    default String tipoDaMensagem() {
        return getClass().getSimpleName();
    }
}
