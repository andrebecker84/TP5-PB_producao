import { apagarJanelas, podeGuardarJanelas } from "@/lib/consentimento";

/**
 * O estado das janelas da plataforma, lembrado entre visitas — só com
 * consentimento (categoria "estado das janelas" do aviso de cookies).
 *
 * Sem nada guardado, cada janela usa o próprio padrão: o menu lateral segue a
 * largura da tela, e as mensagens começam fechadas.
 */

export type EstadoDaSidebar = "expandida" | "compacta";
export type EstadoDasMensagens = "fechada" | "aberta" | "recolhida";

export interface Janelas {
  sidebar?: EstadoDaSidebar;
  mensagens?: EstadoDasMensagens;
}

const CHAVE = "infnet-janelas";
/** Disparado por "redefinir padrão": cada janela volta ao estado inicial. */
export const REDEFINIR_JANELAS = "ih:janelas-redefinir";

export function lerJanelas(): Janelas {
  try {
    return JSON.parse(window.localStorage.getItem(CHAVE) ?? "{}") as Janelas;
  } catch {
    return {};
  }
}

export function gravarJanela(parte: Janelas) {
  if (!podeGuardarJanelas()) return;
  try {
    window.localStorage.setItem(CHAVE, JSON.stringify({ ...lerJanelas(), ...parte }));
  } catch { /* sem armazenamento */ }
}

export function redefinirJanelas() {
  apagarJanelas();
  window.dispatchEvent(new Event(REDEFINIR_JANELAS));
}
