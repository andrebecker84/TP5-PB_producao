package com.andre.infnethub.contratos.expurgo;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * O boletim não pode cumprir a sua parte, e diz por quê.
 *
 * <h2>Recusa não é falha</h2>
 * <p>É a distinção que decide o que o sistema faz a seguir, e por isso são
 * caminhos separados:
 * <ul>
 *   <li><strong>Falha</strong> — o banco caiu, a mensagem veio corrompida. Não
 *       há resposta a dar: a mensagem é reentregue, e depois de algumas
 *       tentativas vai para a fila de mensagens mortas. Tentar de novo pode
 *       resolver.</li>
 *   <li><strong>Recusa</strong> — o participante entendeu o pedido e responde
 *       que <em>não deve</em> ser executado. Tentar de novo nunca vai resolver,
 *       porque não é um problema técnico. Aqui a regra é acadêmica: não se
 *       desvincula a identidade de um registro escolar que ainda está em
 *       curso.</li>
 * </ul>
 *
 * <p>Tratar recusa como falha mandaria para a fila de mensagens mortas uma
 * decisão de negócio perfeitamente válida, e o processo ficaria parado sem que
 * ninguém soubesse por quê. Daí a recusa ser um evento de primeira classe: ela
 * volta ao core, que <strong>desfaz</strong> o bloqueio e avisa quem pediu.
 *
 * @param processoId o processo a que esta resposta pertence — copiado do
 *                   pedido, e é por ele, e não pela pessoa, que o core a casa.
 * @param motivo texto para quem pediu a remoção ler. Não é código de erro: é o
 *               que a secretaria precisa saber para decidir o que fazer.
 */
public record AnonimizacaoRecusadaV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long processoId,
        Long usuarioId,
        String motivo
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(usuarioId);
    }
}
