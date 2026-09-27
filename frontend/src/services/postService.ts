import { Post, PostRequest, Comentario, CurtidaResponse } from "@/types";
import { ok, headersEscrita, api } from "./api";


export const postService = {
  listarTodos: (): Promise<Post[]> =>
    api(`/posts`).then(r => ok<Post[]>(r)),

  criar: (data: PostRequest): Promise<Post> =>
    api(`/posts`, { method: "POST", headers: headersEscrita(), body: JSON.stringify(data) })
      .then(r => ok<Post>(r)),

  atualizar: (id: number, data: PostRequest): Promise<Post> =>
    api(`/posts/${id}`, { method: "PUT", headers: headersEscrita(), body: JSON.stringify(data) })
      .then(r => ok<Post>(r)),

  // Sem corpo, mas com o cabeçalho de autoria: a exclusão também gera revisão,
  // e é a que mais importa saber quem fez.
  deletar: (id: number): Promise<void> =>
    api(`/posts/${id}`, { method: "DELETE", headers: headersEscrita(false) })
      .then(r => ok<void>(r)),

  // Quem curte, comenta ou publica sai do token, no servidor: o cliente não
  // manda o próprio id, e não teria como falar em nome de outra pessoa.
  toggleCurtir: (postId: number): Promise<CurtidaResponse> =>
    api(`/posts/${postId}/curtidas`, {
      method: "POST", headers: headersEscrita(false),
    }).then(r => ok<CurtidaResponse>(r)),

  listarCurtidas: (postId: number): Promise<{ usuarioId: number; usuarioNome: string }[]> =>
    api(`/posts/${postId}/curtidas`).then(r => ok(r)),

  listarComentarios: (postId: number): Promise<Comentario[]> =>
    api(`/posts/${postId}/comentarios`).then(r => ok<Comentario[]>(r)),

  criarComentario: (postId: number, conteudo: string): Promise<Comentario> =>
    api(`/posts/${postId}/comentarios`, {
      method: "POST", headers: headersEscrita(),
      body: JSON.stringify({ conteudo }),
    }).then(r => ok<Comentario>(r)),

  editarComentario: (postId: number, id: number, conteudo: string): Promise<Comentario> =>
    api(`/posts/${postId}/comentarios/${id}`, {
      method: "PUT", headers: headersEscrita(),
      body: JSON.stringify({ conteudo }),
    }).then(r => ok<Comentario>(r)),

  deletarComentario: (postId: number, id: number): Promise<void> =>
    api(`/posts/${postId}/comentarios/${id}`, {
      method: "DELETE", headers: headersEscrita(false),
    }).then(r => ok<void>(r)),
};
