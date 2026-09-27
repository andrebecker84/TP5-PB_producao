import { Usuario } from "@/types";
import { ok, api } from "./api";


export const usuarioService = {
  listarTodos: (): Promise<Usuario[]> =>
    api(`/usuarios`).then(r => ok<Usuario[]>(r)),

  buscarPorId: (id: number): Promise<Usuario> =>
    api(`/usuarios/${id}`).then(r => ok<Usuario>(r)),
};
