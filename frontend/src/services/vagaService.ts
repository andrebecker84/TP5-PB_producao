import { Vaga, VagaRequest } from "@/types";
import { ok, headersEscrita, api } from "./api";


export const vagaService = {
  listarAtivas: (): Promise<Vaga[]> =>
    api(`/vagas`).then(r => ok<Vaga[]>(r)),

  buscarPorId: (id: number): Promise<Vaga> =>
    api(`/vagas/${id}`).then(r => ok<Vaga>(r)),

  criar: (data: VagaRequest): Promise<Vaga> =>
    api(`/vagas`, { method: "POST", headers: headersEscrita(), body: JSON.stringify(data) })
      .then(r => ok<Vaga>(r)),

  atualizar: (id: number, data: VagaRequest): Promise<Vaga> =>
    api(`/vagas/${id}`, { method: "PUT", headers: headersEscrita(), body: JSON.stringify(data) })
      .then(r => ok<Vaga>(r)),

  deletar: (id: number): Promise<void> =>
    api(`/vagas/${id}`, { method: "DELETE", headers: headersEscrita(false) })
      .then(r => ok<void>(r)),
};
