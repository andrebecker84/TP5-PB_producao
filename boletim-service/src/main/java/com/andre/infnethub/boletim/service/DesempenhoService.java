package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.dto.DesempenhoDTO;

/** Indicadores derivados dos conceitos — alimenta o painel de desempenho. */
public interface DesempenhoService {

    DesempenhoDTO apurar(Long alunoId);
}
