package com.andre.infnethub.contratos.feed;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * Alguém curtiu uma publicação.
 *
 * <p>Traz os nomes e o resumo do post, e não só os ids. Quem consome — hoje, o
 * serviço de notificações — precisa escrever "Fulano curtiu sua publicação
 * 'Tal'"; com só os ids, teria de perguntar ao core quem é Fulano e o que é
 * 'Tal', e a chamada síncrona que o evento elimina voltaria pela porta dos
 * fundos.
 *
 * <p>Descurtir não gera evento: nenhum consumidor faz nada com isso, e evento sem
 * consumidor é contrato a manter sem benefício. Se um dia houver quem precise,
 * nasce um {@code PostDescurtidoV1} — sem mexer neste.
 *
 * @param resumoDoPost o título, ou o começo do texto quando o post não tem título.
 */
public record PostCurtidoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long postId,
        String resumoDoPost,
        Long autorDoPostId,
        Long curtidoPorId,
        String curtidoPorNome
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(postId);
    }
}
