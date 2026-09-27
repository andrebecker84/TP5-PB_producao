package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.dto.CatalogoBlocoDTO;
import com.andre.infnethub.boletim.dto.ConceitoDTO;

import java.util.List;

/** A estrutura do curso — blocos, disciplinas e competências previstas. */
public interface CatalogoService {

    List<CatalogoBlocoDTO> listarBlocos();

    CatalogoBlocoDTO buscarBloco(Integer numero);

    /** A escala de conceitos e o que cada degrau significa. */
    List<ConceitoDTO> listarConceitos();
}
