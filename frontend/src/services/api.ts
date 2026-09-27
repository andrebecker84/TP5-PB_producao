export async function ok<T>(r: Response): Promise<T> {
  if (!r.ok) throw new Error((await r.json().catch(() => ({}))).message || r.statusText);
  if (r.status === 204) return undefined as T;
  return r.json();
}

/** Endereço do gateway — o único endereço que o navegador conhece. */
export const GATEWAY = (process.env.NEXT_PUBLIC_API_URL || "http://localhost:21080/api/v1").replace(/\/api\/v1\/?$/, "");
export const BASE = `${GATEWAY}/api/v1`;

/**
 * Lê um cookie pelo nome. O token anti-CSRF é o único cookie que o servidor
 * deixa o JavaScript ler; o de sessão é `HttpOnly` e não aparece aqui.
 */
function cookie(nome: string): string | null {
  if (typeof document === "undefined") return null;
  const achado = document.cookie.split("; ").find(c => c.startsWith(`${nome}=`));
  return achado ? decodeURIComponent(achado.split("=").slice(1).join("=")) : null;
}

/**
 * Toda chamada à API passa por aqui.
 *
 * Duas coisas que mudaram no TP4, e que antes eram responsabilidade de cada
 * chamada:
 *
 * 1. **`credentials: "include"`** — a identidade agora é o cookie de sessão do
 *    gateway. Sem isto o navegador não o envia, porque o front (porta 21000) e
 *    o gateway (21080) são origens diferentes, e a resposta seria 401.
 *
 * 2. **Token anti-CSRF** nas operações de escrita. O cookie vai sozinho em
 *    qualquer requisição, inclusive numa disparada por outro site; o cabeçalho
 *    não, porque só quem consegue ler o cookie consegue montá-lo. É o que
 *    separa uma requisição da aplicação de uma requisição forjada.
 *
 * O que sumiu: o cabeçalho `X-Usuario-Id`. Quem diz quem é a pessoa agora é o
 * token que o gateway guarda — não mais o cliente.
 */
export async function api(caminho: string, init: RequestInit = {}): Promise<Response> {
  const metodo = (init.method || "GET").toUpperCase();
  const cabecalhos = new Headers(init.headers);

  if (!["GET", "HEAD", "OPTIONS"].includes(metodo)) {
    const csrf = cookie("XSRF-TOKEN");
    if (csrf) cabecalhos.set("X-XSRF-TOKEN", csrf);
  }

  return fetch(caminho.startsWith("http") ? caminho : `${BASE}${caminho}`, {
    ...init,
    headers: cabecalhos,
    credentials: "include",
  });
}

/** Cabeçalhos das operações que enviam corpo em JSON. */
export function headersEscrita(incluirJson = true): HeadersInit {
  return incluirJson ? { "Content-Type": "application/json" } : {};
}
