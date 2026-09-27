"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Check } from "lucide-react";
import { STATUS, StatusId, useStatus, definirStatus, infoStatus } from "@/hooks/useStatus";
import s from "./StatusPicker.module.css";

/* ── O bonequinho do status ───────────────────────────────────────────────
   Um personagem por estado, todos do mesmo desenho: online pula alegre com
   brilhos em volta; ausente sai voando num foguete; ocupado corre feito
   maluco, suando; dormindo ronca com os "z" subindo; offline espia por trás
   de uma nuvem. O avatar continua só com a bolinha de cor.                */
function Rosto({ cx, cy, olhos = "abertos", boca = "sorriso" }: {
  cx: number; cy: number;
  olhos?: "abertos" | "fechados" | "focados";
  boca?: "sorriso" | "aberta" | "o" | "reta";
}) {
  return (
    <>
      {olhos === "fechados" ? (
        <>
          <path d={`M${cx - 2.9} ${cy - .6} q1.35 1.5 2.7 0`} className={s.olho} />
          <path d={`M${cx + .2} ${cy - .6} q1.35 1.5 2.7 0`} className={s.olho} />
        </>
      ) : (
        <>
          <circle cx={cx - 2} cy={cy - .6} r=".85" className={s.pupila} />
          <circle cx={cx + 2} cy={cy - .6} r=".85" className={s.pupila} />
          {olhos === "focados" && (
            <>
              <path d={`M${cx - 3.2} ${cy - 2.6} l2 .8`} className={s.olho} />
              <path d={`M${cx + 3.2} ${cy - 2.6} l-2 .8`} className={s.olho} />
            </>
          )}
        </>
      )}
      <circle cx={cx - 3.6} cy={cy + 1.8} r="1.05" className={s.bochecha} />
      <circle cx={cx + 3.6} cy={cy + 1.8} r="1.05" className={s.bochecha} />
      {boca === "sorriso" && <path d={`M${cx - 1.7} ${cy + 1.5} q1.7 1.9 3.4 0`} className={s.boca} />}
      {boca === "aberta" && <ellipse cx={cx + .2} cy={cy + 2.2} rx="1" ry=".85" className={s.bocaAberta} />}
      {boca === "o" && <circle cx={cx} cy={cy + 2.1} r=".6" className={s.bocaAberta} />}
      {boca === "reta" && <path d={`M${cx - .9} ${cy + 2.1} h1.8`} className={s.boca} />}
    </>
  );
}

function Luz({ id }: { id: StatusId }) {
  if (id === "dormindo") {
    return (
      <svg className={s.boneco} viewBox="0 0 26 22" width="30" height="25" aria-hidden>
        <ellipse cx="8" cy="19.4" rx="7.6" ry="2.1" className={s.travesseiro} />
        <g className={s.corpo}>
          <circle cx="8" cy="12.6" r="5.9" className={s.cabeca} />
          <Rosto cx={8} cy={12.6} olhos="fechados" boca="reta" />
        </g>
        <g className={s.zs}>
          <path className={s.z1} d="M15.6 10.6 h2.7 l-2.7 3.1 h2.7" />
          <path className={s.z2} d="M19.8 5.6 h2.1 l-2.1 2.4 h2.1" />
        </g>
      </svg>
    );
  }
  if (id === "online") {
    return (
      <svg className={s.boneco} viewBox="0 0 26 22" width="30" height="25" aria-hidden>
        <ellipse cx="12" cy="20.4" rx="4.6" ry="1.1" className={s.sombra} />
        <g className={s.pula}>
          <circle cx="12" cy="12" r="5.9" className={s.cabeca} />
          <Rosto cx={12} cy={12} />
        </g>
        <path className={`${s.brilho} ${s.brilho1}`} d="M21 3.4v3.2M19.4 5h3.2" />
        <path className={`${s.brilho} ${s.brilho2}`} d="M3.6 5.6v2.4M2.4 6.8h2.4" />
      </svg>
    );
  }
  if (id === "ausente") {
    return (
      <svg className={s.boneco} viewBox="0 0 26 22" width="30" height="25" aria-hidden>
        <g className={s.voo}>
          <path className={s.chama} d="M6 13.8 q-5.4 .6 -6 2.2 q.6 1.6 6 2.2 z" />
          <path className={s.fogueteAsa} d="M8.4 13.2 l-2.2 -2.6 h2.8 l2.4 2.6 z M8.4 18.8 l-2.2 2.4 h2.8 l2.4 -2.4 z" />
          <path className={s.foguete} d="M6.2 13.2 h11 q4.4 0 6.4 2.8 q-2 2.8 -6.4 2.8 h-11 z" />
          <path className={s.fogueteBico} d="M19.4 13.3 q2.9 .5 4.2 2.7 q-1.3 2.2 -4.2 2.7 z" />
          <circle cx="16.4" cy="16" r="1.25" className={s.janela} />
          <circle cx="12.4" cy="8.4" r="4.6" className={s.cabeca} />
          <Rosto cx={12.4} cy={8.4} boca="o" />
        </g>
      </svg>
    );
  }
  if (id === "ocupado") {
    return (
      <svg className={s.boneco} viewBox="0 0 26 22" width="30" height="25" aria-hidden>
        <g className={s.velocidade}>
          <path d="M1 7.8 h5" /><path d="M0 11.4 h6.4" /><path d="M1.6 15 h4.6" /><path d="M.4 18.6 h5.4" />
        </g>
        <g className={s.corre}>
          {/* calça jeans e tênis branco */}
          <g className={`${s.perna} ${s.perna1}`}>
            <path className={s.jeans} d="M15 15.6 v4.2" />
            <ellipse className={s.tenis} cx="15.6" cy="20.2" rx="1.3" ry=".75" />
          </g>
          <g className={`${s.perna} ${s.perna2}`}>
            <path className={s.jeans} d="M15 15.6 v4.2" />
            <ellipse className={s.tenis} cx="15.6" cy="20.2" rx="1.3" ry=".75" />
          </g>
          {/* os bracinhos, cor de pele: vão para frente e para trás, ao contrário
              das pernas; o de trás fica atrás da cabeça */}
          <g className={`${s.braco} ${s.braco1}`}>
            <path className={s.pele} d="M11.2 14.4 v3" /><circle className={s.mao} cx="11.2" cy="17.6" r=".8" />
          </g>
          <circle cx="15" cy="10.4" r="5.6" className={s.cabeca} />
          <Rosto cx={15.4} cy={10.6} olhos="focados" boca="aberta" />
          {/* o da frente vem por cima do rosto: é ele que passa na testa */}
          <g className={`${s.braco} ${s.braco2}`}>
            <path className={s.pele} d="M18.8 14.4 v3" /><circle className={s.mao} cx="18.8" cy="17.6" r=".8" />
          </g>
          {/* o suor: a gota escorre pela lateral da cabeça e respinga para fora */}
          <path className={s.suor} d="M20 4 q1.8 2.5 0 3.6 q-1.8 -1.1 0 -3.6 z" />
          <circle className={`${s.respingo} ${s.respingo1}`} cx="23" cy="8.6" r=".85" />
          <circle className={`${s.respingo} ${s.respingo2}`} cx="23.6" cy="11" r=".65" />
        </g>
      </svg>
    );
  }
  // offline: espiando por trás da nuvem
  return (
    <svg className={s.boneco} viewBox="0 0 26 22" width="30" height="25" aria-hidden>
      <g className={s.espia}>
        <circle cx="13" cy="9" r="5.4" className={s.cabeca} />
        <Rosto cx={13} cy={8.6} boca="reta" />
      </g>
      <path className={s.nuvem}
        d="M4.6 19.6 a3.2 3.2 0 0 1 .6 -6.3 a4.6 4.6 0 0 1 8.7 -1.6 a3.8 3.8 0 0 1 7.1 2.2 a2.9 2.9 0 0 1 -.4 5.7 z" />
    </svg>
  );
}

const LARGURA_MENU = 232;
const ALTURA_MENU = 248;

export default function StatusPicker({ compacto }: { compacto?: boolean }) {
  const atual = useStatus();
  const [aberto, setAberto] = useState(false);
  // o menu vai para o <body> por portal: assim não fica preso (nem recortado)
  // dentro do dock de mensagens ou do menu da conta, que são popups menores
  const [pos, setPos] = useState<{ left: number; top?: number; bottom?: number } | null>(null);
  const btnRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const info = infoStatus(atual);

  const alternar = () => {
    if (aberto) { setAberto(false); return; }
    const r = btnRef.current?.getBoundingClientRect();
    if (!r) return;
    const cabeAbaixo = r.bottom + 8 + ALTURA_MENU <= window.innerHeight;
    setPos({
      left: Math.max(8, Math.min(r.left - 6, window.innerWidth - LARGURA_MENU - 8)),
      ...(cabeAbaixo
        ? { top: r.bottom + 8 }
        : { bottom: window.innerHeight - r.top + 8 }),
    });
    setAberto(true);
  };

  useEffect(() => {
    if (!aberto) return;
    const fora = (e: MouseEvent) => {
      const alvo = e.target as Node;
      if (!btnRef.current?.contains(alvo) && !menuRef.current?.contains(alvo)) setAberto(false);
    };
    const esc = (e: KeyboardEvent) => { if (e.key === "Escape") setAberto(false); };
    const fecha = () => setAberto(false);
    document.addEventListener("mousedown", fora);
    document.addEventListener("keydown", esc);
    window.addEventListener("resize", fecha);
    window.addEventListener("scroll", fecha, true);
    return () => {
      document.removeEventListener("mousedown", fora);
      document.removeEventListener("keydown", esc);
      window.removeEventListener("resize", fecha);
      window.removeEventListener("scroll", fecha, true);
    };
  }, [aberto]);

  return (
    <div className={s.wrap}>
      <button
        ref={btnRef}
        className={`${s.badge} ${s[atual]} ${compacto ? s.compacto : ""}`}
        onClick={e => { e.stopPropagation(); alternar(); }}
        title="Alterar meu status"
        aria-haspopup="menu"
        aria-expanded={aberto}
      >
        <Luz id={atual} />
        {!compacto && info.label}
      </button>

      {aberto && pos && createPortal(
        <div
          ref={menuRef}
          className={`${s.menu} ${pos.bottom != null ? s.menuCima : ""}`}
          style={{ left: pos.left, top: pos.top, bottom: pos.bottom }}
          role="menu"
          /* impede que o popup hospedeiro se feche antes do clique completar */
          onMouseDown={e => e.stopPropagation()}
        >
          <div className={s.menuTitulo}>Meu status</div>
          {STATUS.map(o => (
            <button
              key={o.id}
              role="menuitemradio"
              aria-checked={o.id === atual}
              className={`${s.opt} ${s[o.id]} ${o.id === atual ? s.optOn : ""}`}
              onClick={e => { e.stopPropagation(); definirStatus(o.id); setAberto(false); }}
            >
              <span className={s.optLuz}><Luz id={o.id} /></span>
              <span className={s.optTxt}>
                <b>{o.label}</b>
                <i>{o.hint}</i>
              </span>
              {o.id === atual && <Check size={14} className={s.optCheck} />}
            </button>
          ))}
        </div>,
        document.body
      )}
    </div>
  );
}
