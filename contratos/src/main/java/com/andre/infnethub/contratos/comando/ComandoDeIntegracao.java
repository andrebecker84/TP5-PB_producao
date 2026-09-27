package com.andre.infnethub.contratos.comando;

import com.andre.infnethub.contratos.MensagemDeIntegracao;

/**
 * Um pedido endereçado a um serviço: "faça isto".
 *
 * <h2>O contrário do evento</h2>
 * <table>
 *   <caption>As duas formas de mensagem, lado a lado</caption>
 *   <tr><th></th><th>Evento</th><th>Comando</th></tr>
 *   <tr><td>diz</td><td>aconteceu isto</td><td>faça isto</td></tr>
 *   <tr><td>nome</td><td>no passado ({@code UsuarioCadastrado})</td>
 *       <td>no imperativo ({@code EnviarAviso})</td></tr>
 *   <tr><td>destinatário</td><td>nenhum, ou muitos</td><td>exatamente um</td></tr>
 *   <tr><td>exchange</td><td>{@code topic} — cada interessado se liga</td>
 *       <td>{@code direct} — uma chave, uma fila de trabalho</td></tr>
 *   <tr><td>recusa</td><td>impossível: fato é fato</td><td>possível</td></tr>
 * </table>
 *
 * <h2>Por que ainda é assíncrono</h2>
 * <p>Um comando tem destinatário único, o que o aproxima de uma chamada HTTP —
 * e a pergunta natural é por que não chamar o serviço direto. A resposta é o
 * que o TP4 inteiro persegue: a secretaria registra o aviso e recebe o
 * {@code 202} sem depender de o serviço de notificação estar de pé naquele
 * instante. Se estiver fora, a fila guarda; quando voltar, executa. Uma chamada
 * HTTP teria falhado e devolvido o problema para quem pediu.
 *
 * <p>Comando também é o único formato em que faz sentido <em>agendar</em> a
 * entrega: "faça isto daqui a duas horas" é um pedido; um fato não se adia.
 */
public interface ComandoDeIntegracao extends MensagemDeIntegracao {
}
