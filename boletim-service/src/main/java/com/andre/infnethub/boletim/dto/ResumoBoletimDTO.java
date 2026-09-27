package com.andre.infnethub.boletim.dto;

/** Os números do topo do boletim, apurados sobre todo o percurso do aluno. */
public record ResumoBoletimDTO(
        int competenciasAvaliadas,
        int emAvaliacao,
        int dml,
        int dl,
        int d,
        int nd,
        int disciplinasAprovadas,
        int disciplinasCursando,
        int disciplinasReprovadas,
        int presencaMedia,
        int cargaHorariaAprovada,
        // A meta de carga das disciplinas viaja junto com o quanto já foi
        // cumprido. Deixá-la fixa no cliente faria uma regra do curso morar na
        // tela, que é exatamente o que este serviço veio desfazer — e uma
        // mudança na matriz curricular exigiria alterar o front-end.
        int cargaHorariaExigida,
        int presencaMinimaExigida
) {
}
