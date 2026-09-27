package com.andre.infnethub.contratos.usuario;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * Um usuário passou a existir no Infnet Hub.
 *
 * <p>Carrega o estado inteiro que interessa aos outros serviços, e não só o id
 * (<em>event-carried state transfer</em>). A alternativa — mandar o id e deixar
 * cada consumidor perguntar o resto ao core — recriaria exatamente a chamada
 * síncrona que este evento existe para eliminar: o consumidor voltaria a
 * depender de o core estar no ar no momento em que a mensagem chega.
 *
 * <p><strong>O e-mail fica de fora de propósito.</strong> Nenhum consumidor
 * atual precisa dele, e dado pessoal que não viaja é dado pessoal que não fica
 * retido em fila, em réplica nem em fila de mensagens mortas. Se um serviço
 * futuro precisar, o caminho é uma versão nova do contrato, com a decisão
 * registrada — não um campo acrescentado por conveniência.
 *
 * @param versao valor do bloqueio otimista do {@code Usuario} depois da
 *               gravação. Cresce a cada alteração, e é o que permite ao
 *               consumidor descartar um fato mais antigo que chegue depois de
 *               um mais novo.
 */
public record UsuarioCadastradoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long usuarioId,
        long versao,
        String nome,
        String escola,
        String ultimoBloco,
        String classe,
        String papel,
        String papelDescricao
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(usuarioId);
    }
}
