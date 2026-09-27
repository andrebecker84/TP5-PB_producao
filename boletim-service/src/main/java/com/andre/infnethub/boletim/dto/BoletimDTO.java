package com.andre.infnethub.boletim.dto;

import java.util.List;

/**
 * O boletim completo de um aluno.
 *
 * <p>O campo {@code aluno} é o único deste documento cujo dono é outro serviço:
 * os dados de identificação pertencem ao infnethub-core. Chegam aqui por evento
 * e ficam numa réplica local, de modo que o boletim é montado inteiro com o
 * banco deste serviço. Se o cadastro do aluno ainda não tiver chegado, o campo
 * vem só com o id e o restante do boletim é entregue normalmente.
 */
public record BoletimDTO(
        Long alunoId,
        AlunoDTO aluno,
        List<BlocoBoletimDTO> blocos,
        ResumoBoletimDTO resumo
) {
}
