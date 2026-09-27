package com.andre.infnethub.dto.historico;

import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.model.Vaga;

/**
 * Estado de uma {@link Vaga} em uma revisão.
 *
 * <p>Inclui {@code ativo}: como o encerramento de uma vaga é lógico, é a
 * transição desse campo que o histórico precisa mostrar para explicar por que a
 * vaga saiu da listagem, e quem a encerrou.
 */
public record VagaSnapshot(
        Long id,
        String titulo,
        String empresa,
        String descricao,
        String localizacao,
        String tipo,
        String tipoDescricao,
        String categoria,
        boolean ativo,
        Long criadorId,
        String criadorNome
) {
    public static VagaSnapshot de(Vaga v) {
        Usuario criador = v.getCriador();
        return new VagaSnapshot(
                v.getId(), v.getTitulo(), v.getEmpresa(),
                v.getDescricao(), v.getLocalizacao(),
                v.getTipo() != null ? v.getTipo().name() : null,
                v.getTipo() != null ? v.getTipo().getDescricao() : null,
                v.getCategoria(), v.isAtivo(),
                criador != null ? criador.getId() : null,
                criador != null ? criador.getNome() : null
        );
    }
}
