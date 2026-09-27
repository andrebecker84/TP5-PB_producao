/**
 * A escolha de cookies e de armazenamento local (LGPD, art. 7º e 8º).
 *
 * Fica num cookie, e não no localStorage, por um motivo prático: cookies são
 * do host, não da porta, então a mesma escolha vale para a plataforma
 * (localhost:21000) e para a tela de login do provedor de identidade
 * (localhost:21180), que tem o próprio aviso com o mesmo formato
 * (infra/keycloak/temas/infnethub/login/resources/js/infnethub.js).
 *
 * Categorias:
 * - essenciais: sessão, proteção contra falsificação de requisições e login.
 *   Sem eles não há como entrar; não dependem de consentimento (base legal:
 *   execução do serviço), mas são listados e explicados.
 * - preferências: tema claro/escuro e status de presença, guardados neste
 *   navegador. Só com consentimento; recusadas, valem só até recarregar.
 * - estado das janelas: menu lateral compacto ou expandido, mensagens
 *   abertas, recolhidas ou fechadas. Também só com consentimento, e com
 *   escolha própria — há quem queira o tema lembrado e as janelas não.
 * - estatística e publicidade: não usamos. O aviso diz isso explicitamente.
 */

export const COOKIE = "ih_consentimento";
export const VERSAO = "1";
const UM_ANO = 60 * 60 * 24 * 365;
export const EVENTO = "ih:consentimento";
export const ABRIR_PREFERENCIAS = "ih:cookies-abrir";

/** O que a plataforma guarda neste navegador como preferência. */
export const CHAVES_DE_PREFERENCIA = ["infnet-theme", "infnet_status"];

/** O que a plataforma guarda como estado das janelas (ver lib/janelas.ts). */
export const CHAVES_DE_JANELAS = ["infnet-janelas"];

export interface Consentimento {
  preferencias: boolean;
  janelas: boolean;
  em: string;
}

export function ler(): Consentimento | null {
  if (typeof document === "undefined") return null;
  const bruto = document.cookie.split("; ").find(c => c.startsWith(`${COOKIE}=`));
  if (!bruto) return null;
  const [versao, preferencias, em, janelas] = decodeURIComponent(bruto.split("=")[1]).split("|");
  if (versao !== VERSAO) return null; // mudou o que se pede: perguntar de novo
  // escolhas gravadas antes da categoria das janelas existir: seguem as preferências
  return { preferencias: preferencias === "1", janelas: (janelas ?? preferencias) === "1", em };
}

/** Formato: versão|preferências|data|janelas — o tema do login lê e grava o mesmo. */
export function salvar(preferencias: boolean, janelas: boolean = preferencias) {
  const valor = [VERSAO, preferencias ? "1" : "0", new Date().toISOString(), janelas ? "1" : "0"].join("|");
  document.cookie = `${COOKIE}=${encodeURIComponent(valor)}; path=/; max-age=${UM_ANO}; SameSite=Lax`;
  if (!preferencias) apagarPreferencias();
  if (!janelas) apagarJanelas();
  window.dispatchEvent(new Event(EVENTO));
}

/** Pode guardar preferências neste navegador? Sem escolha feita, ainda não. */
export function podeGuardarPreferencias(): boolean {
  return ler()?.preferencias === true;
}

/** Pode guardar o estado das janelas neste navegador? */
export function podeGuardarJanelas(): boolean {
  return ler()?.janelas === true;
}

export function apagarJanelas() {
  try { CHAVES_DE_JANELAS.forEach(k => window.localStorage.removeItem(k)); } catch { /* sem armazenamento */ }
}

export function apagarPreferencias() {
  try { CHAVES_DE_PREFERENCIA.forEach(k => window.localStorage.removeItem(k)); } catch { /* sem armazenamento */ }
}

/** Apaga o que este navegador guarda da plataforma, inclusive a própria escolha. */
export function apagarTudo() {
  apagarPreferencias();
  apagarJanelas();
  try { window.sessionStorage.clear(); } catch { /* sem armazenamento */ }
  document.cookie = `${COOKIE}=; path=/; max-age=0; SameSite=Lax`;
  window.dispatchEvent(new Event(EVENTO));
}
