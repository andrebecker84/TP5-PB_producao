"use client";

import { useState, useMemo, useEffect } from "react";
import { createPortal } from "react-dom";
import {
  ChevronLeft, ChevronRight, ChevronDown, CheckCircle2, Clock, Circle, AlertCircle,
  CalendarDays, ListChecks, TriangleAlert, Check,
} from "lucide-react";
import DragScroll from "@/components/ui/DragScroll";
import MarqueeText from "@/components/ui/MarqueeText";
import ProgressoEntregas, { Contagem } from "./ProgressoEntregas";
import styles from "./LeftPanel.module.css";

const WEEK_DAYS = ["S","T","Q","Q","S","S","D"];
const MONTHS = [
  "Janeiro","Fevereiro","Março","Abril","Maio","Junho",
  "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro",
];

type Status = "done" | "pending" | "nao_iniciada" | "atrasada";
type Faixa  = "mes" | "trimestre" | "semestre";

interface Tarefa { titulo: string; data: string; vencimento: string; grupo: string; status: Status; }

const TAREFAS_INICIAIS: Tarefa[] = [
  { titulo: "Kickoff Bloco DR5",    data: "10 Mar", vencimento: "2026-03-10", grupo: "Eng. de Softwares Escaláveis", status: "done"        },
  { titulo: "TP1-PB Monolito",      data: "05 Mai", vencimento: "2026-05-05", grupo: "Eng. de Softwares Escaláveis", status: "done"        },
  { titulo: "Design Patterns Quiz", data: "20 Jun", vencimento: "2026-06-20", grupo: "Design Patterns e DDD",        status: "done"        },
  { titulo: "Assessment — Aula 06", data: "09 Jul", vencimento: "2026-07-09", grupo: "Design Patterns e DDD",        status: "nao_iniciada"},
  { titulo: "TP2-PB Persistência",  data: "14 Jul", vencimento: "2026-07-14", grupo: "Eng. de Softwares Escaláveis", status: "pending"     },
  { titulo: "React Final Project",  data: "24 Jul", vencimento: "2026-07-24", grupo: "Dev. Web com React",           status: "nao_iniciada"},
  { titulo: "Mobile Assessment",    data: "12 Ago", vencimento: "2026-08-12", grupo: "Dev. Mobile React Native",     status: "pending"     },
  { titulo: "TP3-PB Microserviços", data: "28 Set", vencimento: "2026-09-28", grupo: "Eng. de Softwares Escaláveis", status: "nao_iniciada"},
  { titulo: "Projeto Final DR5",    data: "30 Nov", vencimento: "2026-11-30", grupo: "Eng. de Softwares Escaláveis", status: "nao_iniciada"},
];

const STATUS_LABEL: Record<Status, string> = {
  done:          "Concluída",
  pending:       "Em andamento",
  nao_iniciada:  "Não iniciada",
  atrasada:      "Atrasada",
};

const STATUS_ORDER: Status[] = ["done", "pending", "nao_iniciada", "atrasada"];

const FAIXAS: { id: Faixa; label: string; dias: number }[] = [
  { id: "mes",       label: "1 mês",     dias: 31  },
  { id: "trimestre", label: "Trimestre", dias: 92  },
  { id: "semestre",  label: "Semestre",  dias: 184 },
];

const HOJE = (() => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; })();

/** Status efetivo: uma tarefa vencida e "não iniciada" vira atrasada
 *  automaticamente; se estiver "em andamento" e vencida, mantém o status mas
 *  sinaliza o aviso de atraso. */
function efetivo(t: Tarefa): { status: Status; atraso: boolean } {
  const venc = new Date(t.vencimento + "T00:00:00");
  const vencida = venc < HOJE;
  if (vencida && t.status === "nao_iniciada") return { status: "atrasada", atraso: true };
  if (vencida && t.status === "pending")      return { status: "pending",  atraso: true };
  if (t.status === "atrasada")                return { status: "atrasada", atraso: true };
  return { status: t.status, atraso: false };
}

function StatusIcon({ status, size = 15 }: { status: Status; size?: number }) {
  if (status === "done")         return <CheckCircle2 size={size} className={styles.iconDone} />;
  if (status === "pending")      return <Clock        size={size} className={styles.iconPending} />;
  if (status === "nao_iniciada") return <Circle       size={size} className={styles.iconNaoIniciada} />;
  return <AlertCircle size={size} className={styles.iconAtrasada} />;
}

interface Cell { d: number; outside: boolean; }
function buildCalendar(year: number, month: number): Cell[] {
  const firstDay  = new Date(year, month, 1).getDay();
  const offset    = (firstDay + 6) % 7; // segunda=0, ..., domingo=6
  const total     = new Date(year, month + 1, 0).getDate();
  const prevTotal = new Date(year, month, 0).getDate();
  const cells: Cell[] = [];
  for (let i = offset - 1; i >= 0; i--) cells.push({ d: prevTotal - i, outside: true });
  for (let d = 1; d <= total; d++)      cells.push({ d, outside: false });
  const resto = cells.length % 7;
  if (resto) for (let d = 1; d <= 7 - resto; d++) cells.push({ d, outside: true });
  return cells;
}

interface TooltipPos { task: Tarefa; x: number; y: number; }
interface MenuPos     { idx: number; x: number; y: number; }

export default function LeftPanel() {
  const today = new Date();
  const [calYear,  setCalYear]  = useState(today.getFullYear());
  const [calMonth, setCalMonth] = useState(today.getMonth());
  const [tarefas,  setTarefas]  = useState<Tarefa[]>(TAREFAS_INICIAIS);
  const [faixa,    setFaixa]    = useState<Faixa>("trimestre");
  const [tip,  setTip]  = useState<TooltipPos | null>(null);
  const [menu, setMenu] = useState<MenuPos | null>(null);
  // seções do kanban recolhidas (por status) — começam TODAS recolhidas
  const [colapsadas, setColapsadas] = useState<Set<Status>>(() => new Set(STATUS_ORDER));
  const toggleCol = (s: Status) => setColapsadas(prev => {
    const n = new Set(prev); if (n.has(s)) n.delete(s); else n.add(s); return n;
  });

  // tarefas dentro da faixa escolhida (janela em torno de hoje)
  const tarefasVisiveis = useMemo(() => {
    const dias = FAIXAS.find(f => f.id === faixa)!.dias;
    const min = new Date(HOJE); min.setDate(min.getDate() - dias);
    const max = new Date(HOJE); max.setDate(max.getDate() + dias);
    return tarefas.filter(t => {
      const v = new Date(t.vencimento + "T00:00:00");
      return v >= min && v <= max;
    });
  }, [tarefas, faixa]);

  // agrupa as tarefas visíveis por status efetivo (colunas do kanban vertical)
  const grupos = useMemo(() => {
    const g: Record<Status, { t: Tarefa; idx: number }[]> =
      { done: [], pending: [], nao_iniciada: [], atrasada: [] };
    tarefasVisiveis.forEach(t => g[efetivo(t).status].push({ t, idx: tarefas.indexOf(t) }));
    return g;
  }, [tarefasVisiveis, tarefas]);

  // distribuição de status sobre TODAS as tarefas (para o gráfico de progresso)
  const contagem = useMemo<Contagem>(() => {
    const c: Contagem = { done: 0, pending: 0, nao_iniciada: 0, atrasada: 0 };
    tarefas.forEach(t => { c[efetivo(t).status] += 1; });
    return c;
  }, [tarefas]);

  // dots do calendário: só no mês/ano de vencimento, com o status efetivo
  const taskMap = useMemo(() => {
    const m = new Map<number, { task: Tarefa; status: Status }>();
    tarefas.forEach(t => {
      const v = new Date(t.vencimento + "T00:00:00");
      if (v.getFullYear() === calYear && v.getMonth() === calMonth) {
        m.set(v.getDate(), { task: t, status: efetivo(t).status });
      }
    });
    return m;
  }, [tarefas, calYear, calMonth]);

  useEffect(() => {
    if (!menu) return;
    const close = () => setMenu(null);
    window.addEventListener("click", close);
    window.addEventListener("scroll", close, true);
    return () => { window.removeEventListener("click", close); window.removeEventListener("scroll", close, true); };
  }, [menu]);

  const showTip = (e: React.MouseEvent, task: Tarefa) => {
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setTip({ task, x: r.left + r.width / 2, y: r.top });
  };

  const openMenu = (e: React.MouseEvent, idx: number) => {
    e.stopPropagation();
    const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setTip(null);
    setMenu({ idx, x: r.left, y: r.bottom + 6 });
  };

  const setStatus = (idx: number, status: Status) => {
    setTarefas(ts => ts.map((t, i) => (i === idx ? { ...t, status } : t)));
    setMenu(null);
  };

  const cells  = buildCalendar(calYear, calMonth);
  const todayD = today.getFullYear() === calYear && today.getMonth() === calMonth
    ? today.getDate() : -1;

  const prevMonth = () => {
    if (calMonth === 0) { setCalYear(y => y - 1); setCalMonth(11); }
    else setCalMonth(m => m - 1);
  };
  const nextMonth = () => {
    if (calMonth === 11) { setCalYear(y => y + 1); setCalMonth(0); }
    else setCalMonth(m => m + 1);
  };

  return (
    <aside className={styles.panel}>

      {/* ── Calendário ── */}
      <div className={styles.card}>
        <div className={styles.calHeader}>
          <button className={styles.calNav} onClick={prevMonth} aria-label="Mês anterior"><ChevronLeft size={15} /></button>
          <span className={styles.calTitle}>
            <CalendarDays size={14} className={styles.calTitleIco} />
            {MONTHS[calMonth]} {calYear}
          </span>
          <button className={styles.calNav} onClick={nextMonth} aria-label="Próximo mês"><ChevronRight size={15} /></button>
        </div>
        <div className={styles.calGrid}>
          {WEEK_DAYS.map((d, i) => (
            <div key={i} className={styles.calDay}>{d}</div>
          ))}
          {cells.map((cell, i) => {
            const task = cell.outside ? undefined : taskMap.get(cell.d);
            const hoje = !cell.outside && cell.d === todayD;
            return (
              <div
                key={i}
                className={`${styles.calCell} ${cell.outside ? styles.calOutside : ""} ${hoje ? styles.calToday : ""} ${task ? styles.calTask : ""}`}
                onMouseEnter={task ? (e) => showTip(e, task.task) : undefined}
                onMouseLeave={task ? () => setTip(null) : undefined}
              >
                <span className={styles.calNum}>{cell.d}</span>
                {task && <span className={`${styles.taskDot} ${styles[`dot_${task.status}`]}`} />}
              </div>
            );
          })}
        </div>
      </div>

      {/* ── Tarefas & Entregas — kanban por status, em bloco de notas espiral ── */}
      <div className={`${styles.card} ${styles.cardNotas}`}>
        {/* espiral metálica (gunmetal) do caderno, straddling a borda superior,
            com furos perfurados na chapa por onde o fio passa */}
        <div className={styles.espiral} aria-hidden>
          <svg viewBox="0 0 240 26" preserveAspectRatio="none">
            <defs>
              {/* aço equilibrado: reflexo alto → especular → meia-sombra → reflexo
                  baixo → base (nem cromado claro, nem preto) */}
              <linearGradient id="coilChrome" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0"   stopColor="#9aa6b8" />
                <stop offset=".22" stopColor="#dde6f2" />
                <stop offset=".46" stopColor="#4e596b" />
                <stop offset=".7"  stopColor="#8b97aa" />
                <stop offset="1"   stopColor="#39424f" />
              </linearGradient>
            </defs>
            {Array.from({ length: 11 }).map((_, i) => {
              const cx = 13 + i * 21.4;
              const w = 2.5;   // meia-largura da argola
              // loop reto (∩) com duas pernas descendo até a chapa
              const loop = `M${cx - w} 16 L${cx - w} 7 Q${cx - w} 3 ${cx} 3 Q${cx + w} 3 ${cx + w} 7 L${cx + w} 16`;
              return (
                <g key={i}>
                  {/* sombra do espiral projetada no card (deslocada) */}
                  <path d={loop} fill="none" stroke="rgba(0,0,0,.4)" strokeWidth="2.7"
                    strokeLinecap="round" transform="translate(1.3 2.2)" />
                  {/* fio principal de metal (loop) */}
                  <path d={loop} fill="none" stroke="url(#coilChrome)" strokeWidth="2.4" strokeLinecap="round" />
                  {/* brilho especular no fio (reflexo) */}
                  <path d={loop} fill="none" stroke="rgba(246,250,255,.75)" strokeWidth=".8"
                    strokeLinecap="round" transform="translate(-.6 0)" strokeDasharray="0 5 6 40" />
                  {/* FURAÇÃO no card — MAIOR que o fio (é um buraco por onde ele
                      passa por dentro); desenhada por cima das pernas */}
                  <rect x={cx - 4.4} y="12.5" width="8.8" height="8" rx="4" fill="#04070d" />
                  {/* sombra interna no topo do furo (profundidade) */}
                  <path d={`M${cx - 3.4} 13.6 Q${cx} 11.9 ${cx + 3.4} 13.6`} fill="none"
                    stroke="rgba(0,0,0,.75)" strokeWidth="1.1" strokeLinecap="round" />
                  {/* LUZ dentro do furo (rim iluminado no fundo → furação visível) */}
                  <path d={`M${cx - 3.4} 19 Q${cx} 21.2 ${cx + 3.4} 19`} fill="none"
                    stroke="rgba(150,178,224,.6)" strokeWidth="1.1" strokeLinecap="round" />
                </g>
              );
            })}
          </svg>
        </div>
        <h3 className={styles.title}><ListChecks size={15} className={styles.titleIco} /> Tarefas &amp; Entregas</h3>

        <div className={styles.faixaRow}>
          {FAIXAS.map(f => (
            <button
              key={f.id}
              className={`${styles.faixaBtn} ${faixa === f.id ? styles.faixaAtiva : ""}`}
              onClick={() => setFaixa(f.id)}
            >
              {f.label}
            </button>
          ))}
        </div>

        {tarefasVisiveis.length === 0 ? (
          <p className={styles.tarefaVazio}>Nenhuma entrega nesta faixa.</p>
        ) : (
          <DragScroll className={styles.tarefaScroll}>
            {STATUS_ORDER.map(s => {
              const items = grupos[s];
              if (items.length === 0) return null;
              const aberto = !colapsadas.has(s);
              const temAtraso = items.some(({ t }) => efetivo(t).atraso);
              return (
                <section key={s} className={`${styles.kbGroup} ${styles[`kb_${s}`]}`}>
                  <button
                    className={styles.kbHead}
                    onClick={() => toggleCol(s)}
                    aria-expanded={aberto}
                  >
                    <ChevronDown size={13} className={`${styles.kbChevron} ${aberto ? "" : styles.kbChevronCol}`} />
                    <StatusIcon status={s} size={13} />
                    <span className={styles.kbLabel}>{STATUS_LABEL[s]}</span>
                    {temAtraso && (
                      <span className={styles.atrasoBadge} title="Há tarefa(s) atrasada(s) neste grupo">
                        <TriangleAlert size={12} />
                      </span>
                    )}
                    <span className={styles.kbCount}>{items.length}</span>
                  </button>
                  {aberto && (
                    <div className={styles.kbBody}>
                      {items.map(({ t, idx }) => {
                        const atrasada = efetivo(t).atraso;
                        return (
                          <div key={idx} data-marquee-host className={`${styles.tarefaItem} ${s === "done" ? styles.tarefaDone : ""} ${atrasada ? styles.tarefaAtrasada : ""}`}>
                            <button
                              className={styles.tarefaCheck}
                              onClick={(e) => openMenu(e, idx)}
                              title="Alterar status"
                              aria-label={`Status: ${STATUS_LABEL[s]} — clique para alterar`}
                            >
                              <span className={`${styles.checkBox} ${styles[`chk_${s}`]}`}>
                                {s === "done"
                                  ? <Check size={11} strokeWidth={3} />
                                  : <StatusIcon status={s} size={13} />}
                              </span>
                            </button>
                            <div className={styles.tarefaInfo}>
                              <div className={styles.tarefaTituloRow}>
                                {atrasada && (
                                  <span className={styles.atrasoBadge} title="Tarefa atrasada, verificar com urgência!" aria-label="Tarefa atrasada, verificar com urgência!">
                                    <TriangleAlert size={12} />
                                  </span>
                                )}
                                <MarqueeText className={styles.tarefaTitulo}>{t.titulo}</MarqueeText>
                              </div>
                              <span className={styles.tarefaMeta}>
                                <MarqueeText className={styles.tarefaGrupo}>{t.grupo}</MarqueeText>
                              </span>
                            </div>
                            <span className={styles.tarefaData}>
                              <CalendarDays size={12} className={styles.tarefaDataIco} />
                              {t.data}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </section>
              );
            })}
          </DragScroll>
        )}
      </div>

      {/* ── Progresso das Entregas ── */}
      <ProgressoEntregas contagem={contagem} />

      {tip && !menu && createPortal(
        <div className={styles.tooltip} style={{ left: tip.x, top: tip.y - 8 }}>
          <span className={styles.ttTitle}>{tip.task.titulo}</span>
          <span className={`${styles.ttBadge} ${styles[`ttBadge_${efetivo(tip.task).status}`]}`}>
            {STATUS_LABEL[efetivo(tip.task).status]}
          </span>
          <span className={styles.ttGrupo}>{tip.task.grupo}</span>
        </div>,
        document.body
      )}

      {menu && createPortal(
        <div className={styles.statusMenu} style={{ left: menu.x, top: menu.y }} onClick={e => e.stopPropagation()}>
          <span className={styles.statusMenuTitle}>Marcar como</span>
          {STATUS_ORDER.map(s => (
            <button
              key={s}
              className={`${styles.statusOpt} ${tarefas[menu.idx].status === s ? styles.statusOptAtivo : ""}`}
              onClick={() => setStatus(menu.idx, s)}
            >
              <StatusIcon status={s} size={14} />
              <span>{STATUS_LABEL[s]}</span>
              {tarefas[menu.idx].status === s && <CheckCircle2 size={13} className={styles.statusOptCheck} />}
            </button>
          ))}
        </div>,
        document.body
      )}
    </aside>
  );
}
