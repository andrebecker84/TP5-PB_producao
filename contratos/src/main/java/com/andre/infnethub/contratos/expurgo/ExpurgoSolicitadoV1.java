package com.andre.infnethub.contratos.expurgo;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * A instituição decidiu remover esta pessoa do sistema. Primeiro passo da saga
 * de expurgo (LGPD).
 *
 * <h2>Por que uma saga, e não um evento só</h2>
 * <p>Remover uma pessoa envolve três bancos que nenhuma transação alcança ao
 * mesmo tempo. Publicar {@code UsuarioRemovido} e torcer deixaria o sistema num
 * estado que ninguém consegue descrever: removido aqui, presente ali, e sem
 * registro de onde a operação parou.
 *
 * <p>A saga troca isso por um processo com estados observáveis. Este evento
 * <strong>abre</strong> o processo; o core já bloqueou a pessoa (ela não
 * aparece, não entra e não age), mas ainda não apagou nada. Os participantes
 * respondem com {@link AlunoAnonimizadoV1} ou {@link AnonimizacaoRecusadaV1}, e
 * só então o core conclui ou desfaz.
 *
 * <p>Repare no nome: <em>solicitado</em>, não <em>removido</em>. É a diferença
 * entre "isto vai acontecer" e "isto aconteceu", e ela importa — quem consome o
 * fato consumado ({@code UsuarioRemovidoV1}) não pode agir sobre o pedido, ou
 * apagaria dados de alguém cuja remoção ainda pode ser revertida.
 *
 * @param processoId o processo de expurgo a que esta mensagem pertence. A
 *                   pessoa é a mesma entre dois pedidos; o processo, não. Sem
 *                   ele, uma resposta atrasada de um pedido já encerrado por
 *                   prazo seria lida como a resposta do pedido seguinte.
 * @param usuarioId quem. Só o id: é tudo o que o participante precisa para
 *                  encontrar o que tem sobre a pessoa, e mandar o nome junto
 *                  seria espalhar pelo broker o dado que se quer eliminar.
 */
public record ExpurgoSolicitadoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long processoId,
        Long usuarioId
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(usuarioId);
    }
}
