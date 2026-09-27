package com.andre.infnethub.dto.historico;

import org.springframework.data.history.RevisionMetadata;

/**
 * Natureza de uma revisão, na linguagem do domínio.
 *
 * <p>Traduz o {@code RevisionType} do Spring Data (INSERT/UPDATE/DELETE) para
 * termos que fazem sentido em um relatório de auditoria.
 */
public enum TipoAlteracao {

    CRIACAO("Criação"),
    ALTERACAO("Alteração"),
    EXCLUSAO("Exclusão"),
    DESCONHECIDO("Desconhecido");

    private final String descricao;

    TipoAlteracao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static TipoAlteracao de(RevisionMetadata.RevisionType tipo) {
        return switch (tipo) {
            case INSERT -> CRIACAO;
            case UPDATE -> ALTERACAO;
            case DELETE -> EXCLUSAO;
            case UNKNOWN -> DESCONHECIDO;
        };
    }
}
