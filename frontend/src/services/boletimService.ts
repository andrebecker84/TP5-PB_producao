import {
  Atividade, AtividadeRequest, Boletim, CargaCategoria,
  CatalogoBloco, CompetenciaAvaliada, Desempenho, EscalaConceito,
} from "@/types/boletim";
import { ok, headersEscrita, api } from "./api";

/**
 * Cliente do boletim-service.
 *
 * A URL base é a mesma dos demais serviços — e é esse o ponto. O cliente fala
 * com o API Gateway, que decide se a requisição vai para a aplicação central ou
 * para o microsserviço. Do lado do navegador não há diferença perceptível entre
 * pedir um post e pedir um boletim, embora venham de processos e bancos
 * distintos.
 */

export const boletimService = {
  buscarBoletim: (alunoId: number): Promise<Boletim> =>
    api(`/boletim/${alunoId}`).then(r => ok<Boletim>(r)),

  buscarDesempenho: (alunoId: number): Promise<Desempenho> =>
    api(`/desempenho/${alunoId}`).then(r => ok<Desempenho>(r)),

  listarAtividades: (alunoId: number): Promise<Atividade[]> =>
    api(`/alunos/${alunoId}/atividades`).then(r => ok<Atividade[]>(r)),

  cargaHoraria: (alunoId: number): Promise<CargaCategoria[]> =>
    api(`/alunos/${alunoId}/atividades/carga-horaria`).then(r => ok<CargaCategoria[]>(r)),

  criarAtividade: (alunoId: number, data: AtividadeRequest): Promise<Atividade> =>
    api(`/alunos/${alunoId}/atividades`, {
      method: "POST", headers: headersEscrita(), body: JSON.stringify(data),
    }).then(r => ok<Atividade>(r)),

  atualizarAtividade: (alunoId: number, id: number, data: AtividadeRequest): Promise<Atividade> =>
    api(`/alunos/${alunoId}/atividades/${id}`, {
      method: "PUT", headers: headersEscrita(), body: JSON.stringify(data),
    }).then(r => ok<Atividade>(r)),

  removerAtividade: (alunoId: number, id: number): Promise<void> =>
    api(`/alunos/${alunoId}/atividades/${id}`, {
      method: "DELETE", headers: headersEscrita(false),
    }).then(r => ok<void>(r)),

  /**
   * Lança ou corrige o conceito de uma competência.
   *
   * `conceito: null` devolve a competência ao estado "em avaliação" — é como se
   * desfaz um lançamento equivocado.
   */
  registrarConceito: (
    matriculaId: number, competenciaId: number, conceito: string | null,
  ): Promise<CompetenciaAvaliada> =>
    api(`/boletim/matriculas/${matriculaId}/competencias/${competenciaId}`, {
      method: "PUT", headers: headersEscrita(), body: JSON.stringify({ conceito }),
    }).then(r => ok<CompetenciaAvaliada>(r)),

  listarCatalogo: (): Promise<CatalogoBloco[]> =>
    api(`/catalogo/blocos`).then(r => ok<CatalogoBloco[]>(r)),

  /** Escala de conceitos, do pior para o melhor — alimenta a legenda. */
  listarEscalaConceitos: (): Promise<EscalaConceito[]> =>
    api(`/catalogo/conceitos`).then(r => ok<EscalaConceito[]>(r)),
};
