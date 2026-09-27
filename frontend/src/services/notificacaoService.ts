import { Notificacao } from "@/types/notificacao";
import { ok, headersEscrita, api, BASE } from "./api";

/**
 * Cliente do notificacao-service, pelo gateway.
 *
 * Nenhuma chamada informa de quem são as notificações: o serviço responde
 * sempre "as do usuário do token". Não existe URL para ler as de outra pessoa.
 */

export const notificacaoService = {
  minhas: (limite = 30): Promise<Notificacao[]> =>
    api(`/notificacoes?limite=${limite}`, { headers: headersEscrita(false) })
      .then(r => ok<Notificacao[]>(r)),

  marcarTodasComoLidas: (): Promise<{ atualizadas: number }> =>
    api(`/notificacoes/lidas/todas`, { method: "POST", headers: headersEscrita(false) })
      .then(r => ok(r)),

  marcarComoLidas: (ids: number[]): Promise<{ atualizadas: number }> =>
    api(`/notificacoes/lidas`, {
      method: "POST", headers: headersEscrita(), body: JSON.stringify({ ids }),
    }).then(r => ok(r)),

  marcarComoNaoLidas: (ids: number[]): Promise<{ atualizadas: number }> =>
    api(`/notificacoes/nao-lidas`, {
      method: "POST", headers: headersEscrita(), body: JSON.stringify({ ids }),
    }).then(r => ok(r)),

  excluir: (ids: number[]): Promise<{ excluidas: number }> =>
    api(`/notificacoes/excluir`, {
      method: "POST", headers: headersEscrita(), body: JSON.stringify({ ids }),
    }).then(r => ok(r)),

  /**
   * Endereço da conexão ao vivo (Server-Sent Events).
   *
   * URL absoluta porque o `EventSource` não passa pelo helper `api` — e é por
   * isso que ele é aberto com `withCredentials`: o cookie de sessão é a única
   * credencial que o navegador envia numa conexão dessas.
   */
  urlAoVivo: (): string => `${BASE}/notificacoes/ao-vivo`,
};
