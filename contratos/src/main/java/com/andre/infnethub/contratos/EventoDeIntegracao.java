package com.andre.infnethub.contratos;

/**
 * Um fato que um serviço anuncia para os outros.
 *
 * <p>É diferente do evento de domínio que circula dentro de um processo: este
 * atravessa a rede, fica guardado numa fila e pode ser lido por um serviço que
 * ainda nem existe. Por isso tudo nele é contrato público — renomear um campo
 * quebra quem consome, e a saída para mudar é publicar uma versão nova
 * ({@code V2}) ao lado da antiga, nunca editar a que está em uso.
 *
 * <h2>Evento não tem destinatário</h2>
 * <p>É a diferença que separa esta interface de
 * {@link com.andre.infnethub.contratos.comando.ComandoDeIntegracao}. Quem
 * publica {@code UsuarioCadastradoV1} está dizendo "aconteceu isto" ao mundo, e
 * não sabe — nem precisa saber — que o boletim mantém uma réplica e que o
 * serviço de notificação manda boas-vindas. Acrescentar um terceiro interessado
 * amanhã não toca numa linha de quem publica.
 *
 * <p>O nome está no passado, e no particípio, justamente porque já aconteceu:
 * ninguém pode recusar um fato. Um pedido, que pode ser recusado, é comando.
 */
public interface EventoDeIntegracao extends MensagemDeIntegracao {
}
