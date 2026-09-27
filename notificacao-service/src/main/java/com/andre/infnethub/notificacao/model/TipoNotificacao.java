package com.andre.infnethub.notificacao.model;

/** De que fato a notificação veio — é o que a interface usa para escolher o ícone. */
public enum TipoNotificacao {
    BOAS_VINDAS,
    CURTIDA,
    COMENTARIO,
    VAGA,

    /**
     * Recado da secretaria. É o único que não vem de um fato do sistema: os
     * outros quatro são consequência de algo que aconteceu, este é alguém
     * decidindo avisar.
     */
    AVISO
}
