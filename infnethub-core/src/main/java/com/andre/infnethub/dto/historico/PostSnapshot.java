package com.andre.infnethub.dto.historico;

import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;

/**
 * Estado de um {@link Post} em uma revisão.
 *
 * <p>O autor é achatado em id e nome em vez de aninhar o snapshot inteiro do
 * usuário: o histórico do post responde "o que este post dizia", e o histórico
 * do autor tem endpoint próprio.
 */
public record PostSnapshot(
        Long id,
        String titulo,
        String conteudo,
        Long autorId,
        String autorNome
) {
    public static PostSnapshot de(Post p) {
        // Em uma revisão de exclusão a associação pode não ser resolvível,
        // mesmo com store_data_at_delete ativo.
        Usuario autor = p.getAutor();
        return new PostSnapshot(
                p.getId(), p.getTitulo(), p.getConteudo(),
                autor != null ? autor.getId() : null,
                autor != null ? autor.getNome() : null
        );
    }
}
