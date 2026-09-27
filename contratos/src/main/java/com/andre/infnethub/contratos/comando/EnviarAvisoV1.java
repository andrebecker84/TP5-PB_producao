package com.andre.infnethub.contratos.comando;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Peça ao serviço de notificação que avise estas pessoas.
 *
 * <p>É o canal da secretaria para falar com a turma: prazo de entrega, mudança
 * de sala, resultado publicado. Nada disso é consequência de um fato do
 * sistema — ninguém curtiu nada, ninguém se cadastrou —, é alguém decidindo
 * avisar. Por isso é comando, e não evento.
 *
 * @param destinatarios  quem recebe. Lista vazia significa <em>todos os alunos
 *                       ativos</em> — o mesmo alcance que uma vaga publicada,
 *                       resolvido no serviço que sabe quem são, não em quem
 *                       pede. Uma lista com ids alcança só eles.
 * @param texto          o que aparece na notificação, já pronto para ler.
 * @param link           para onde o sino leva ao ser clicado; pode ser nulo.
 * @param solicitadoPorId quem pediu. Não é usado para autorizar — isso já foi
 *                       decidido antes de o comando entrar na fila —, mas para
 *                       responder "quem mandou este aviso?" quando alguém
 *                       perguntar.
 */
public record EnviarAvisoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        List<Long> destinatarios,
        String texto,
        String link,
        Long solicitadoPorId
) implements ComandoDeIntegracao {

    public EnviarAvisoV1 {
        destinatarios = destinatarios == null ? List.of() : List.copyOf(destinatarios);
    }

    /**
     * Um aviso não é sobre um agregado; a chave é quem o pediu.
     *
     * <p>Serve à ordenação que o relay faz por chave: dois avisos da mesma
     * pessoa saem na ordem em que foram pedidos, e um aviso preso não segura os
     * de mais ninguém.
     */
    @Override
    public String chave() {
        return "aviso:" + solicitadoPorId;
    }

    /** Sem lista de destinatários, o alvo é toda a turma. */
    public boolean paraTodosOsAlunos() {
        return destinatarios.isEmpty();
    }
}
