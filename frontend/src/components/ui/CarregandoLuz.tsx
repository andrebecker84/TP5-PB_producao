"use client";

import { useEffect, useRef, type ReactNode } from "react";
import styles from "./CarregandoLuz.module.css";

/**
 * Tela de espera: uma luz correndo por um fio, jorrando partículas atrás de si
 * como o rastro de um foguete.
 *
 * Aparece nas duas esperas que a pessoa não controla. Na ida ao provedor de
 * identidade, `embutido`, dentro do cartão de entrada, no lugar do botão. Na
 * volta, em tela cheia, enquanto o hub confirma a sessão e busca o perfil. Em
 * tela cheia o fundo é sempre escuro, nos dois temas — o efeito é de luz, e
 * luz precisa de escuro.
 *
 * Tudo em canvas: uma cauda esfumaçada que afina para trás, e poucas
 * partículas que nascem na luz, saem para trás com velocidade própria, se
 * espalham, esfriam de cor e somem — o jorro sutil de um rastro de foguete.
 */

type Tom = "azul" | "verde";

const CORES: Record<Tom, { nucleo: string; quente: [number, number, number]; frio: [number, number, number]; aura: string }> = {
  azul:  { nucleo: "#ffffff", quente: [165, 243, 252], frio: [129, 140, 248], aura: "129,140,248" },
  verde: { nucleo: "#ffffff", quente: [187, 247, 208], frio: [16, 185, 129],  aura: "52,211,153" },
};

const PERIODO = 2600;        // ms para atravessar o fio
const POR_QUADRO = 2;        // partículas que nascem a cada quadro — poucas, para ficar sutil

interface Particula { x: number; y: number; vx: number; vy: number; vida: number; total: number; r: number }

export default function CarregandoLuz({ texto = "Carregando…", embutido = false, tom = "azul", icone }: {
  texto?: string; embutido?: boolean;
  /** ícone opcional antes do texto */
  icone?: ReactNode;
  /** acompanha a digital: verde depois do "acesso liberado" */
  tom?: Tom;
}) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    const cor = CORES[tom];
    const parado = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    let L = 0, A = 0, quadro = 0, inicio = 0, anterior = -1;
    const particulas: Particula[] = [];

    const ajustar = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      L = canvas.clientWidth; A = canvas.clientHeight;
      canvas.width = Math.round(L * dpr); canvas.height = Math.round(A * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    // desacelera nas pontas e corre no meio
    const suave = (t: number) => t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

    // o rastro esfumaçado: uma cauda desfocada que afina para trás e cresce com a velocidade
    const desenharCauda = (x: number, y: number, brilho: number, velocidade: number) => {
      const escala = embutido ? 0.65 : 1;
      const comprimento = (70 + Math.abs(velocidade) * 14) * escala;
      const altura = 9 * escala;
      const [qr, qg, qb] = cor.quente;
      ctx.save();
      ctx.filter = `blur(${5 * escala}px)`;
      ctx.translate(x - comprimento * 0.5, y);
      ctx.scale(comprimento * 0.5 / altura, 1);
      const g = ctx.createRadialGradient(altura * 0.7, 0, 0, altura * 0.7, 0, altura * 1.3);
      g.addColorStop(0, `rgba(${qr},${qg},${qb},${0.55 * brilho})`);
      g.addColorStop(.5, `rgba(${cor.aura},${0.22 * brilho})`);
      g.addColorStop(1, `rgba(${cor.aura},0)`);
      ctx.fillStyle = g;
      ctx.beginPath(); ctx.ellipse(0, 0, altura * 1.3, altura, 0, 0, Math.PI * 2); ctx.fill();
      ctx.restore();
    };

    // a cauda do cometa: uma faixa que afina até virar fio, acesa junto à
    // cabeça e esmaecendo para trás, com um filete mais claro por dentro
    const desenharCometa = (x: number, y: number, brilho: number, velocidade: number) => {
      const escala = embutido ? 0.65 : 1;
      const comprimento = (110 + Math.abs(velocidade) * 10) * escala;
      const [qr, qg, qb] = cor.quente;
      const faixa = (largura: number, alfa: number, tom: string, desfoque: number) => {
        const g = ctx.createLinearGradient(x, 0, x - comprimento, 0);
        g.addColorStop(0, `rgba(${tom},${alfa * brilho})`);
        g.addColorStop(.35, `rgba(${tom},${alfa * 0.45 * brilho})`);
        g.addColorStop(1, `rgba(${tom},0)`);
        ctx.save();
        ctx.filter = `blur(${desfoque}px)`;
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.moveTo(x, y - largura);
        ctx.quadraticCurveTo(x - comprimento * 0.35, y - largura * 0.35, x - comprimento, y);
        ctx.quadraticCurveTo(x - comprimento * 0.35, y + largura * 0.35, x, y + largura);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      };
      faixa(5 * escala, 0.55, `${qr},${qg},${qb}`, 1.5 * escala);
      faixa(1.6 * escala, 0.9, "255,255,255", 0.6);
    };

    const desenharLuz = (x: number, y: number, brilho: number) => {
      const escala = embutido ? 0.65 : 1;
      // aura larga
      let g = ctx.createRadialGradient(x, y, 0, x, y, 70 * escala);
      g.addColorStop(0, `rgba(${cor.aura},${0.35 * brilho})`);
      g.addColorStop(1, `rgba(${cor.aura},0)`);
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, 70 * escala, 0, Math.PI * 2); ctx.fill();
      // cabeça: halo forte e uma bolinha branca sólida no centro
      const [qr, qg, qb] = cor.quente;
      g = ctx.createRadialGradient(x, y, 0, x, y, 15 * escala);
      g.addColorStop(0, `rgba(255,255,255,${brilho})`);
      g.addColorStop(.25, `rgba(${qr},${qg},${qb},${0.9 * brilho})`);
      g.addColorStop(.6, `rgba(${qr},${qg},${qb},${0.25 * brilho})`);
      g.addColorStop(1, `rgba(${qr},${qg},${qb},0)`);
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(x, y, 15 * escala, 0, Math.PI * 2); ctx.fill();
      ctx.shadowColor = `rgba(${qr},${qg},${qb},${brilho})`;
      ctx.shadowBlur = 10 * escala;
      ctx.fillStyle = `rgba(255,255,255,${brilho})`;
      ctx.beginPath(); ctx.arc(x, y, 2.8 * escala, 0, Math.PI * 2); ctx.fill();
      ctx.shadowBlur = 0;
    };

    const passo = (ms: number) => {
      if (!inicio) inicio = ms;
      const t = ((ms - inicio) % PERIODO) / PERIODO;
      const p = suave(t);
      const x = L * (0.06 + 0.88 * p);
      const y = A / 2;
      const brilho = Math.min(1, t / 0.12, (1 - t) / 0.12);
      const velocidade = anterior < 0 ? 0 : x - anterior;
      anterior = t < 0.02 ? -1 : x;

      // nascem na luz e saem para trás, com espalhamento e velocidades variadas
      if (brilho > 0.05) {
        for (let i = 0; i < POR_QUADRO; i++) {
          const total = 20 + Math.random() * 30;
          particulas.push({
            x: x - Math.random() * 4, y: y + (Math.random() - 0.5) * 3,
            vx: -(0.6 + Math.random() * 2.2) - Math.max(0, velocidade) * 0.25,
            vy: (Math.random() - 0.5) * (embutido ? 0.55 : 0.9),
            vida: total, total, r: 0.5 + Math.random() * (embutido ? 0.9 : 1.3),
          });
        }
      }

      ctx.clearRect(0, 0, L, A);
      ctx.globalCompositeOperation = "lighter";

      const [qr, qg, qb] = cor.quente, [fr, fg, fb] = cor.frio;
      for (let i = particulas.length - 1; i >= 0; i--) {
        const q = particulas[i];
        q.x += q.vx; q.y += q.vy;
        q.vx *= 0.985; q.vy *= 0.99;          // perdem fôlego
        q.vida -= 1;
        if (q.vida <= 0) { particulas.splice(i, 1); continue; }
        const f = q.vida / q.total;            // 1 ao nascer, 0 ao sumir
        // esfriam: do tom quente ao frio, e apagam
        const r = Math.round(fr + (qr - fr) * f), gg = Math.round(fg + (qg - fg) * f), b = Math.round(fb + (qb - fb) * f);
        ctx.fillStyle = `rgba(${r},${gg},${b},${0.45 * f * f * brilho})`;
        ctx.beginPath(); ctx.arc(q.x, q.y, q.r * (0.4 + 0.6 * f), 0, Math.PI * 2); ctx.fill();
      }

      desenharCauda(x, y, brilho, velocidade);
      desenharCometa(x, y, brilho, velocidade);
      desenharLuz(x, y, brilho);
      ctx.globalCompositeOperation = "source-over";
      quadro = requestAnimationFrame(passo);
    };

    ajustar();
    window.addEventListener("resize", ajustar);
    if (parado) { ctx.globalCompositeOperation = "lighter"; desenharLuz(L / 2, A / 2, 1); }
    else quadro = requestAnimationFrame(passo);

    return () => { cancelAnimationFrame(quadro); window.removeEventListener("resize", ajustar); };
  }, [tom, embutido]);

  return (
    <div className={embutido ? styles.embutido : styles.tela} data-tom={tom} role="status" aria-live="polite">
      <div className={styles.trilho} aria-hidden>
        <span className={styles.linha} />
        <canvas ref={ref} className={styles.faisca} />
      </div>
      <p className={styles.texto}>{icone}{texto}</p>
    </div>
  );
}
