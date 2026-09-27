import type { Papel } from "@/types";

export const CORES = ["#3b8ef5", "#06b6d4", "#f59e0b", "#10b981", "#f43f5e"];

export const PAPEL_BG: Record<Papel, string> = {
  ALUNO:       "rgba(59,142,245,.25)",
  PROFESSOR:   "rgba(34,211,238,.22)",
  SECRETARIA:  "rgba(251,191,36,.2)",
  COORDENADOR: "rgba(52,211,153,.2)",
  SUPORTE_TI:  "rgba(167,139,250,.22)",
};

export const PAPEL_TXT: Record<Papel, string> = {
  ALUNO:       "var(--primary)",
  PROFESSOR:   "var(--accent)",
  SECRETARIA:  "var(--warning)",
  COORDENADOR: "var(--success)",
  SUPORTE_TI:  "#a78bfa",
};

export const TIPO_COLOR: Record<string, string> = {
  ESTAGIO:  "rgba(34,211,238,.18)",
  CLT:      "rgba(52,211,153,.18)",
  PJ:       "rgba(251,191,36,.18)",
  TRAINEE:  "rgba(59,142,245,.18)",
  AUTONOMO: "rgba(248,113,113,.18)",
  EXTERIOR: "rgba(45,212,191,.18)",
};

export const TIPO_TXT: Record<string, string> = {
  ESTAGIO:  "var(--accent)",
  CLT:      "var(--success)",
  PJ:       "var(--warning)",
  TRAINEE:  "var(--primary)",
  AUTONOMO: "var(--danger)",
  EXTERIOR: "#2dd4bf",
};
