"use client";

import { useState, useEffect } from "react";
import styles from "./SaudacaoBanner.module.css";

type Periodo = "manha" | "tarde" | "noite";

const CONFIG: Record<Periodo, { saud: string; sub: string }> = {
  manha: { saud: "Bom dia",   sub: "Um ótimo dia de estudos pela frente." },
  tarde: { saud: "Boa tarde", sub: "Continue firme nas suas entregas." },
  noite: { saud: "Boa noite", sub: "Hora de revisar o que você aprendeu hoje." },
};

/* ── Paisagens em SVG (preenchem o banner como plano de fundo) ── */

function CenaManha() {
  // arco-íris: cor + raio de cada faixa (centro abaixo do banner → arco no céu)
  const arco: [string, number][] = [
    ["#ff8a8a", 98], ["#ffc078", 92], ["#ffe680", 86],
    ["#8ce99a", 80], ["#74c0fc", 74], ["#b197fc", 68],
  ];
  const AX = 205, AY = 140; // centro do arco-íris — à ESQUERDA, longe do sol
  return (
    <svg className={styles.cena} viewBox="0 0 720 120" preserveAspectRatio="xMidYMid slice" aria-hidden>
      <defs>
        <linearGradient id="ceuManha" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%"  stopColor="#4a92e0" />
          <stop offset="55%" stopColor="#9ecbf3" />
          <stop offset="100%" stopColor="#ffe2ad" />
        </linearGradient>
        <radialGradient id="solManha" cx="50%" cy="50%" r="50%">
          <stop offset="0%"  stopColor="#fff7dc" />
          <stop offset="55%" stopColor="#ffd873" />
          <stop offset="100%" stopColor="#ffb84d" />
        </radialGradient>
        {/* risco (condensação) do avião: opaco perto do avião, some ao longe.
            userSpaceOnUse é ESSENCIAL — a linha é horizontal (bbox de altura 0)
            e com objectBoundingBox o gradiente não renderizava (risco sumia). */}
        <linearGradient id="contrailManha" x1="-120" y1="0" x2="-4" y2="0" gradientUnits="userSpaceOnUse">
          <stop offset="0"   stopColor="#ffffff" stopOpacity="0" />
          <stop offset="1"   stopColor="#ffffff" stopOpacity=".92" />
        </linearGradient>
      </defs>
      <rect width="720" height="120" fill="url(#ceuManha)" />

      {/* arco-íris ao fundo */}
      <g className={styles.arcoIris} fill="none" strokeWidth="2.6" strokeLinecap="round">
        {arco.map(([c, r], i) => (
          <path key={i} d={`M ${AX - r} ${AY} A ${r} ${r} 0 0 1 ${AX + r} ${AY}`} stroke={c} />
        ))}
      </g>

      <circle className={styles.astro} cx="545" cy="86" r="30" fill="url(#solManha)" />

      {/* avião no fundo voando na DIAGONAL, com um risco que esmaece atrás
          dele (some a uma certa distância) — o grupo interno é rotacionado no
          ângulo do voo; o externo translada ao longo da mesma diagonal */}
      <g className={styles.aviao}>
        <g className={styles.aviaoInner}>
          {/* condensação: um traço largo e suave (a nuvem) + um fino e nítido */}
          <line x1="-120" y1="0" x2="-4" y2="0" stroke="url(#contrailManha)" strokeWidth="3.4" strokeLinecap="round" opacity=".55" />
          <line x1="-118" y1="0" x2="-4" y2="0" stroke="url(#contrailManha)" strokeWidth="1.4" strokeLinecap="round" />
          {/* avião: silhueta limpa de jato (corpo + asas enflechadas + cauda) */}
          <path fill="#33404f" d="M11 0 L2 -1.4 L-3 -1.4 L-9 -6 L-5 -1.4 L-9 -1.3 L-11 -3 L-9 0 L-11 3 L-9 1.3 L-5 1.4 L-9 6 L-3 1.4 L2 1.4 Z" />
        </g>
      </g>

      {/* passarinhos voando */}
      <g className={styles.passaros} stroke="#37536f" strokeWidth="1.5" fill="none" strokeLinecap="round">
        <path d="M0 0 q3.5 -4 7 0 q3.5 -4 7 0" />
        <path d="M18 7 q3 -3.5 6 0 q3 -3.5 6 0" />
        <path d="M34 -3 q3 -3.5 6 0 q3 -3.5 6 0" />
      </g>

      <g className={styles.nuvem} opacity=".85">
        <ellipse cx="180" cy="34" rx="30" ry="11" fill="#fff" opacity=".9" />
        <ellipse cx="205" cy="30" rx="20" ry="9"  fill="#fff" opacity=".9" />
        <ellipse cx="155" cy="30" rx="18" ry="8"  fill="#fff" opacity=".85" />
      </g>

      <path d="M0 96 Q140 62 300 90 T720 82 V120 H0 Z" fill="#7bbf8a" opacity=".55" />
      <path d="M0 110 Q180 80 380 104 T720 100 V120 H0 Z" fill="#3f8f63" />

      {/* árvore no canto direito (primeiro plano) */}
      <g>
        <path d="M686 120 L683 76 Q689 72 695 76 L692 120 Z" fill="#6f4c2c" />
        <g className={styles.copa}>
          <ellipse cx="688" cy="54" rx="42" ry="31" fill="#2f7d4f" />
          <ellipse cx="664" cy="62" rx="26" ry="20" fill="#379157" />
          <ellipse cx="710" cy="60" rx="24" ry="19" fill="#256841" />
          <ellipse cx="690" cy="44" rx="31" ry="23" fill="#45a86a" />
          <circle cx="676" cy="40" r="4.5" fill="#63c885" opacity=".55" />
          <circle cx="701" cy="50" r="3.5" fill="#63c885" opacity=".4" />
        </g>
      </g>

      {/* criança no morro */}
      <g transform="translate(360,0)">
        <rect x="-2.4" y="98" width="1.9" height="6.5" rx=".9" fill="#2f5aa8" />
        <rect x="0.6"  y="98" width="1.9" height="6.5" rx=".9" fill="#2f5aa8" />
        <path d="M-3.4 91 Q0 88 3.4 91 L2.6 99 L-2.6 99 Z" fill="#ff7a59" />
        <line x1="-3" y1="92.5" x2="-5.6" y2="95.5" stroke="#ff7a59" strokeWidth="1.8" strokeLinecap="round" />
        <line className={styles.bracinho} x1="3" y1="92.5" x2="6.4" y2="88.6" stroke="#ff7a59" strokeWidth="1.8" strokeLinecap="round" />
        <circle cx="0" cy="85.4" r="3.4" fill="#f4c9a0" />
        <path d="M-3.4 84.6 Q0 79.4 3.4 84.6 Q1.8 82.6 0 82.6 Q-1.8 82.6 -3.4 84.6 Z" fill="#5a3b22" />
      </g>

      {/* cachorro correndo no morro, mais à frente */}
      <g className={styles.cachorro}>
        <ellipse cx="0" cy="102" rx="6.6" ry="3.2" fill="#c07a3e" />
        <path className={styles.rabo} d="M-6.2 100.6 Q-10.4 99 -10 102.4" fill="none" stroke="#c07a3e" strokeWidth="1.9" strokeLinecap="round" />
        <circle cx="6.6" cy="99.8" r="3" fill="#c07a3e" />
        <path d="M4.7 97.6 Q4 93.8 6.8 96.2 Z" fill="#9c5c2a" />
        <ellipse cx="9.2" cy="101" rx="1.8" ry="1.2" fill="#a9662f" />
        <circle cx="7.6" cy="99.2" r="0.6" fill="#2a1a0d" />
        <g className={styles.pataA} stroke="#a9662f" strokeWidth="1.7" strokeLinecap="round">
          <line x1="-3.2" y1="104" x2="-3.2" y2="108.4" />
          <line x1="4.2"  y1="104" x2="4.2"  y2="108.4" />
        </g>
        <g className={styles.pataB} stroke="#b06f34" strokeWidth="1.7" strokeLinecap="round">
          <line x1="-1"  y1="104" x2="-1"  y2="108.4" />
          <line x1="2.2" y1="104" x2="2.2" y2="108.4" />
        </g>
      </g>

      {/* borboletas perto da árvore */}
      <g transform="translate(636,72)">
        <g className={styles.borboleta}>
          <g className={styles.asas}>
            <path d="M0 0 Q-8 -7 -5 2 Q-7 5 0 1 Z" fill="#ff9f43" />
            <path d="M0 0 Q8 -7 5 2 Q7 5 0 1 Z" fill="#ffb96b" />
          </g>
          <line x1="0" y1="-3" x2="0" y2="3" stroke="#4a3520" strokeWidth="1" strokeLinecap="round" />
        </g>
      </g>
      <g transform="translate(610,86)">
        <g className={styles.borboleta} style={{ animationDelay: "1.3s" }}>
          <g className={styles.asas} style={{ animationDelay: ".18s" }}>
            <path d="M0 0 Q-6 -5 -4 1.5 Q-5 4 0 1 Z" fill="#748ffc" />
            <path d="M0 0 Q6 -5 4 1.5 Q5 4 0 1 Z" fill="#91a7ff" />
          </g>
          <line x1="0" y1="-2.4" x2="0" y2="2.4" stroke="#2a3550" strokeWidth=".9" strokeLinecap="round" />
        </g>
      </g>
    </svg>
  );
}

function CenaTarde() {
  return (
    <svg className={styles.cena} viewBox="0 0 720 120" preserveAspectRatio="xMidYMid slice" aria-hidden>
      <defs>
        <linearGradient id="ceuTarde" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%"  stopColor="#3a3d80" />
          <stop offset="45%" stopColor="#a3559d" />
          <stop offset="100%" stopColor="#ffab5e" />
        </linearGradient>
        <radialGradient id="solTarde" cx="50%" cy="50%" r="50%">
          <stop offset="0%"  stopColor="#fff1cf" />
          <stop offset="50%" stopColor="#ffb765" />
          <stop offset="100%" stopColor="#f77e4d" />
        </radialGradient>
      </defs>
      <rect width="720" height="120" fill="url(#ceuTarde)" />
      <circle className={styles.astro} cx="540" cy="98" r="34" fill="url(#solTarde)" />
      {/* gaivotas: silhueta de asas (dois arcos), não triângulos */}
      <g className={styles.gaivotas} fill="none" stroke="#241d47" strokeWidth="1.5"
         strokeLinecap="round" strokeLinejoin="round" opacity=".5">
        <path d="M452 40 q4.5 -5.5 9 0 q4.5 -5.5 9 0" />
        <path d="M487 31 q5.5 -6.5 11 0 q5.5 -6.5 11 0" />
        <path d="M472 50 q3.5 -4.5 7 0 q3.5 -4.5 7 0" />
      </g>
      <path d="M0 92 Q160 66 340 88 T720 84 V120 H0 Z" fill="#5b3f72" opacity=".7" />
      <path d="M0 108 Q200 84 420 104 T720 102 V120 H0 Z" fill="#2e2145" />
    </svg>
  );
}

function CenaNoite() {
  const estrelas = [
    [90, 26], [150, 44], [220, 22], [285, 50], [360, 30],
    [110, 60], [430, 26], [190, 36], [330, 60], [255, 34],
  ];
  return (
    <svg className={styles.cena} viewBox="0 0 720 120" preserveAspectRatio="xMidYMid slice" aria-hidden>
      <defs>
        <linearGradient id="ceuNoite" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%"  stopColor="#070f26" />
          <stop offset="60%" stopColor="#122150" />
          <stop offset="100%" stopColor="#25396f" />
        </linearGradient>
        <radialGradient id="luaGlow" cx="30%" cy="66%" r="72%">
          <stop offset="0%"  stopColor="#fffefb" />
          <stop offset="55%" stopColor="#eef2ff" />
          <stop offset="100%" stopColor="#cdd8f5" />
        </radialGradient>
        <radialGradient id="luaHaloG" cx="50%" cy="50%" r="50%">
          <stop offset="0%"   stopColor="#dfe8ff" stopOpacity=".5" />
          <stop offset="55%"  stopColor="#9fb4ef" stopOpacity=".18" />
          <stop offset="100%" stopColor="#9fb4ef" stopOpacity="0" />
        </radialGradient>
        {/* crescente FINA a 45°: recorte deslocado na diagonal (cima-direita),
            deixando a fatia iluminada embaixo-esquerda */}
        <mask id="crescente">
          <circle cx="560" cy="46" r="22" fill="#fff" />
          <circle cx="563.4" cy="42.6" r="22.4" fill="#000" />
        </mask>
        {/* disco voador */}
        <linearGradient id="discoG" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#c9d2e0" /><stop offset="50%" stopColor="#8b95a8" /><stop offset="100%" stopColor="#586074" />
        </linearGradient>
        <radialGradient id="domoG" cx="42%" cy="35%" r="70%">
          <stop offset="0%" stopColor="#dff6ff" /><stop offset="60%" stopColor="#8fd3ef" /><stop offset="100%" stopColor="#4b9fc4" />
        </radialGradient>
        <linearGradient id="feixeG" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#a9f0d4" stopOpacity=".55" /><stop offset="100%" stopColor="#a9f0d4" stopOpacity="0" />
        </linearGradient>
        <linearGradient id="trail" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%"   stopColor="#fff" stopOpacity="0" />
          <stop offset="100%" stopColor="#dbe6ff" stopOpacity=".95" />
        </linearGradient>
        <linearGradient id="cometaTail" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%"   stopColor="#8fd3ff" stopOpacity="0" />
          <stop offset="100%" stopColor="#cdefff" stopOpacity=".9" />
        </linearGradient>
      </defs>
      <rect width="720" height="120" fill="url(#ceuNoite)" />
      {estrelas.map(([cx, cy], i) => (
        <circle key={i} className={styles.estrela} cx={cx} cy={cy} r={i % 3 === 0 ? 1.6 : 1}
          fill="#fff" style={{ animationDelay: `${(i % 5) * 0.6}s` }} />
      ))}

      {/* cometa: cruza o céu e some no horizonte (desenhado antes das montanhas) */}
      <g className={styles.cometa}>
        <path d="M2 2 L-42 0.4 L-42 3.6 Z" fill="url(#cometaTail)" />
        <circle cx="0" cy="2" r="2.4" fill="#eaf7ff" />
        <circle cx="0" cy="2" r="4.5" fill="#bfe8ff" opacity=".35" />
      </g>

      {/* estrela cadente: rota até perto do disco — passa POR TRÁS dele (é antes no z) */}
      <g className={styles.cadente}>
        <line x1="0" y1="0" x2="24" y2="-7" stroke="url(#trail)" strokeWidth="1.6" strokeLinecap="round" />
        <circle cx="0" cy="0" r="1.5" fill="#fff" />
      </g>

      {/* lua crescente fina a 45° com halo */}
      <circle className={styles.luaHalo} cx="560" cy="46" r="42" fill="url(#luaHaloG)" />
      <circle cx="560" cy="46" r="22" fill="url(#luaGlow)" mask="url(#crescente)" />

      {/* 🛸 disco: sobe do horizonte, abduz a vaquinha do chão e some num flash */}
      <g className={styles.ovni}>
        <path className={styles.feixe} d="M388 44 L412 44 L428 96 L372 96 Z" fill="url(#feixeG)" />
        <g className={styles.vaca}>
          <ellipse cx="400" cy="91" rx="5" ry="3" fill="#f4f4f2" />
          <ellipse cx="404.5" cy="89.5" rx="2" ry="1.6" fill="#f4f4f2" />
          <circle cx="398.5" cy="90" r="0.9" fill="#3a3a3a" />
          <circle cx="401.5" cy="92" r="0.7" fill="#3a3a3a" />
          <rect x="397.5" y="93" width="1.1" height="2.2" fill="#e2e2df" />
          <rect x="401.4" y="93" width="1.1" height="2.2" fill="#e2e2df" />
        </g>
        <circle className={styles.flashOvni} cx="400" cy="40" r="3" fill="#eafcff" />
        <ellipse cx="400" cy="40" rx="20" ry="6.5" fill="url(#discoG)" />
        <ellipse cx="400" cy="36" rx="10" ry="7" fill="url(#domoG)" />
        <circle className={styles.luzOvni} cx="388" cy="41" r="1.5" fill="#7ce0ff" style={{ animationDelay: "0s" }} />
        <circle className={styles.luzOvni} cx="400" cy="43" r="1.5" fill="#b39cff" style={{ animationDelay: ".5s" }} />
        <circle className={styles.luzOvni} cx="412" cy="41" r="1.5" fill="#7ce0ff" style={{ animationDelay: "1s" }} />
      </g>

      {/* montanhas (cobrem o horizonte onde cometa e estrela somem) */}
      <path d="M0 94 Q170 70 350 90 T720 86 V120 H0 Z" fill="#0d1a3c" />
      <path d="M0 110 Q210 88 430 106 T720 104 V120 H0 Z" fill="#060f28" />
    </svg>
  );
}

const CENAS: Record<Periodo, () => React.ReactElement> = {
  manha: CenaManha,
  tarde: CenaTarde,
  noite: CenaNoite,
};

export default function SaudacaoBanner({ nome }: { nome: string }) {
  const h = new Date().getHours();
  // pré-visualização opcional: ?cena=manha|tarde|noite força a paisagem
  // (sem o parâmetro, segue o horário real)
  const [preview, setPreview] = useState<Periodo | null>(null);
  useEffect(() => {
    const c = new URLSearchParams(window.location.search).get("cena");
    if (c === "manha" || c === "tarde" || c === "noite") setPreview(c);
  }, []);
  const periodo: Periodo = preview ?? (h >= 5 && h < 12 ? "manha" : h >= 12 && h < 18 ? "tarde" : "noite");
  const { saud, sub } = CONFIG[periodo];
  const Cena = CENAS[periodo];
  const primeiro = nome.replace(/^Prof\.?\s+/i, "").split(" ")[0];

  return (
    <div className={`${styles.banner} ${styles[periodo]}`}>
      <Cena />
      <div className={styles.scrim} aria-hidden />
      <div className={styles.texto}>
        <span className={styles.saud}>{saud}, {primeiro}! <span className={styles.mao}>👋</span></span>
        <span className={styles.sub}>{sub}</span>
      </div>
    </div>
  );
}
