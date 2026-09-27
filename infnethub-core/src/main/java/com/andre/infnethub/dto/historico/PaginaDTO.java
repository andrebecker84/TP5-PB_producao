package com.andre.infnethub.dto.historico;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope de paginação da API.
 *
 * <p>Existe porque serializar {@code PageImpl} diretamente entrega um JSON
 * derivado da estrutura interna do Spring Data — instável entre versões e
 * explicitamente desaconselhado pelo projeto. Este record fixa o contrato.
 */
public record PaginaDTO<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas,
        boolean primeira,
        boolean ultima
) {
    public static <E, T> PaginaDTO<T> de(Page<E> page, Function<E, T> mapeador) {
        return new PaginaDTO<>(
                page.getContent().stream().map(mapeador).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
