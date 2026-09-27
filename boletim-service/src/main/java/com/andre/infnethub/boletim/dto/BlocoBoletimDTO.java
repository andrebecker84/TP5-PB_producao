package com.andre.infnethub.boletim.dto;

import java.util.List;

/**
 * Um bloco no boletim do aluno.
 *
 * <p>O {@code periodo} vem das matrículas, não do catálogo: o bloco 1 foi 25E1
 * para este aluno e pode ter sido outro trimestre para outro.
 */
public record BlocoBoletimDTO(
        Integer numero,
        String titulo,
        String periodo,
        String status,
        String statusDescricao,
        int disciplinasAprovadas,
        int totalDisciplinas,
        List<DisciplinaBoletimDTO> disciplinas
) {
}
