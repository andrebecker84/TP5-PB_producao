"use client";

import { useSyncExternalStore } from "react";
import { podeGuardarPreferencias } from "@/lib/consentimento";

/* ── Status de presença do usuário ──────────────────────────────────────
   Guardado fora do React (localStorage + evento) para que TODOS os pontos
   que mostram o status — a barra de mensagens, a bolinha do avatar, o menu
   do perfil — reajam à mesma troca, inclusive entre abas abertas.        */

export type StatusId = "online" | "ausente" | "ocupado" | "dormindo" | "offline";

export const STATUS: { id: StatusId; label: string; cor: string; hint: string }[] = [
  { id: "online",   label: "Online",   cor: "#22c55e", hint: "disponível agora" },
  { id: "ausente",  label: "Ausente",  cor: "#f59e0b", hint: "volto em instantes" },
  { id: "ocupado",  label: "Ocupado",  cor: "#ef4444", hint: "não perturbe" },
  { id: "dormindo", label: "Dormindo", cor: "#a78bfa", hint: "zzz… até amanhã" },
  { id: "offline",  label: "Offline",  cor: "#94a3b8", hint: "invisível para todos" },
];

export const infoStatus = (id: StatusId) => STATUS.find(s => s.id === id) ?? STATUS[0];

const CHAVE = "infnet_status";
const EVENTO = "infnet:status";

let cache: StatusId | null = null;

function ler(): StatusId {
  if (cache) return cache;
  if (typeof window === "undefined") return "online";
  const v = window.localStorage.getItem(CHAVE) as StatusId | null;
  cache = v && STATUS.some(s => s.id === v) ? v : "online";
  return cache;
}

export function definirStatus(id: StatusId) {
  cache = id;
  // guardado neste navegador só com consentimento para preferências
  if (podeGuardarPreferencias()) window.localStorage.setItem(CHAVE, id);
  window.dispatchEvent(new Event(EVENTO));
}

function assinar(aviso: () => void) {
  const h = () => { cache = null; aviso(); };
  window.addEventListener(EVENTO, h);
  window.addEventListener("storage", h);
  return () => {
    window.removeEventListener(EVENTO, h);
    window.removeEventListener("storage", h);
  };
}

export function useStatus(): StatusId {
  return useSyncExternalStore(assinar, ler, () => "online" as StatusId);
}
