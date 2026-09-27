"use client";

import { useState, useRef, useEffect } from "react";
import { TrendingUp, CheckCircle2, Clock, CircleDashed, AlertCircle } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import styles from "./ProgressoEntregas.module.css";

export interface Contagem { done: number; pending: number; nao_iniciada: number; atrasada: number; }

/* fim do trimestre/semestre do bloco 26E2 (referência para a contagem regressiva) */
const HOJE = (() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; })();
const DIA  = 86_400_000;
const PERIODOS = [
  { label: "Trimestre", ini: "2026-07-07", fim: "2026-09-30" },
  { label: "Semestre",  ini: "2026-05-04", fim: "2026-12-19" },
];

const SEGS: { key: keyof Contagem; label: string; cor: string; icon: LucideIcon }[] = [
  { key: "done",         label: "Concluídas",    cor: "#34d399", icon: CheckCircle2 },
  { key: "pending",      label: "Em andamento",  cor: "#fbbf24", icon: Clock        },
  { key: "nao_iniciada", label: "Não iniciadas", cor: "#94a3b8", icon: CircleDashed },
  { key: "atrasada",     label: "Atrasadas",     cor: "#f87171", icon: AlertCircle  },
];

const R = 26, C = 2 * Math.PI * R;

/* ── Contador estilo letreiro de aeroporto (split-flap) ──
   conta do valor anterior até o alvo em ~900ms; cada dígito é remontado
   quando muda (key = posição+valor) e a animação de flap toca de novo, dando
   o efeito da placa girando até assentar. Roda no load e a cada mudança. */
function useCountUp(target: number, duration = 950): number {
  const [val, setVal] = useState(0);
  const prev = useRef(0);
  useEffect(() => {
    const from = prev.current;
    const to = target;
    if (from === to) return;
    let raf = 0;
    const t0 = performance.now();
    const tick = (now: number) => {
      const p = Math.min(1, (now - t0) / duration);
      const eased = 1 - Math.pow(1 - p, 3); // ease-out cúbico: desacelera ao assentar
      setVal(Math.round(from + (to - from) * eased));
      if (p < 1) raf = requestAnimationFrame(tick);
      else { prev.current = to; setVal(to); }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target, duration]);
  return val;
}

function FlapPercent({ value }: { value: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  // só "arma" o count-up quando o card está de fato visível: com a aba oculta o
  // requestAnimationFrame fica pausado e o número saltaria pro alvo sem o
  // letreiro rolar. Assim o efeito toca no load e a cada reload/reveal.
  const [armed, setArmed] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const arma = () => { if (document.visibilityState === "visible") setArmed(true); };
    const io = new IntersectionObserver(([e]) => { if (e.isIntersecting) arma(); }, { threshold: 0.4 });
    io.observe(el);
    document.addEventListener("visibilitychange", arma);
    return () => { io.disconnect(); document.removeEventListener("visibilitychange", arma); };
  }, []);
  const shown = useCountUp(armed ? value : 0);
  const digits = String(shown).split("");
  return (
    <span ref={ref} className={styles.flapWrap} aria-label={`${value}% concluído`}>
      {digits.map((d, i) => (
        <span key={`${i}-${d}`} className={styles.flap}>{d}</span>
      ))}
      <span className={styles.flapPct}>%</span>
    </span>
  );
}

export default function ProgressoEntregas({ contagem }: { contagem: Contagem }) {
  const total = SEGS.reduce((s, seg) => s + contagem[seg.key], 0);
  const pctConcluido = total ? Math.round((contagem.done / total) * 100) : 0;

  // arco único de conclusão (pct do total)
  const arcoConcluido = (pctConcluido / 100) * C;

  const periodos = PERIODOS.map(p => {
    const ini = new Date(p.ini + "T00:00:00").getTime();
    const fim = new Date(p.fim + "T00:00:00").getTime();
    const dias = Math.max(0, Math.ceil((fim - HOJE.getTime()) / DIA));
    const prog = Math.min(100, Math.max(0, Math.round(((HOJE.getTime() - ini) / (fim - ini)) * 100)));
    return { ...p, dias, prog };
  });

  return (
    <div className={styles.card}>
      <h3 className={styles.title}><TrendingUp size={15} className={styles.titleIco} /> Progresso das Entregas</h3>

      {/* "X de Y concluídas" à esquerda + anel de conclusão com gradiente à direita */}
      <div className={styles.donutRow}>
        <div className={styles.statBlock}>
          <span className={styles.statNum}>{contagem.done}</span>
          <div className={styles.statMeta}>
            <span className={styles.statDe}>de {total}</span>
            <span className={styles.statSub}>concluídas</span>
          </div>
        </div>
        <div
          className={styles.donutWrap}
          aria-label={`${pctConcluido}% concluído`}
        >
          <div className={styles.donutGlow} aria-hidden />
          <svg viewBox="0 0 64 64" className={styles.donut} aria-hidden>
            <defs>
              <linearGradient id="progGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%"   stopColor="#3b8ef5" />
                <stop offset="52%"  stopColor="#7c5cff" />
                <stop offset="100%" stopColor="#22d3ee" />
              </linearGradient>
              {/* brilho do arco com região folgada: o padrão do filtro cortava o
                  brilho rente ao desenho e deixava as bordas marcadas */}
              <filter id="progBrilho" x="-25%" y="-25%" width="150%" height="150%">
                <feGaussianBlur stdDeviation="1.8" result="brilho" />
                <feComponentTransfer in="brilho" result="brilhoSuave">
                  <feFuncA type="linear" slope="0.7" />
                </feComponentTransfer>
                <feMerge>
                  <feMergeNode in="brilhoSuave" />
                  <feMergeNode in="SourceGraphic" />
                </feMerge>
              </filter>
            </defs>
            <circle cx="32" cy="32" r={R} className={styles.donutTrilho} />
            <circle
              cx="32" cy="32" r={R} fill="none"
              stroke="url(#progGrad)" strokeWidth="6" strokeLinecap="round"
              strokeDasharray={`${arcoConcluido} ${C}`}
              transform="rotate(-90 32 32)"
              filter="url(#progBrilho)"
              className={styles.donutArc}
            />
          </svg>
          <div className={styles.donutCentro}>
            <FlapPercent value={pctConcluido} />
          </div>
        </div>
      </div>

      {/* uma barra de progresso segmentada por categoria, com ícone e texto na
          cor da categoria (estilo da referência) */}
      <div className={styles.catList}>
        {SEGS.map(seg => {
          const val = contagem[seg.key];
          const pct = total ? Math.round((val / total) * 100) : 0;
          const Icon = seg.icon;
          return (
            /* a cor identifica a categoria só nas MARCAS — ícone e barra.
               Números e rótulos ficam na tipografia da plataforma, senão a
               coluna inteira vira um mostruário de cores. */
            <div key={seg.key} className={styles.catRow}>
              <span className={styles.catIco} style={{ color: seg.cor }}>
                <Icon size={14} />
              </span>
              <div className={styles.catBody}>
                <div className={styles.catHead}>
                  <span className={styles.catLabel}>
                    <b className={styles.catCount}>{val}</b> {seg.label}
                  </span>
                  <span className={styles.catPct}>{pct}%</span>
                </div>
                <div className={styles.catTrack}>
                  <span className={styles.catFill} style={{ width: `${pct}%`, background: seg.cor }} />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className={styles.periodos}>
        {periodos.map(p => (
          <div key={p.label} className={styles.periodo}>
            <div className={styles.periodoTopo}>
              <span className={styles.periodoLabel}>{p.label}</span>
              <span className={styles.periodoDias}>faltam <strong>{p.dias}</strong> dias</span>
            </div>
            <div className={styles.periodoTrilho}>
              <div className={styles.periodoBarra} style={{ width: `${p.prog}%` }} />
            </div>
          </div>
        ))}
      </div>

    </div>
  );
}
