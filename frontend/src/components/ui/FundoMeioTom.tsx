"use client";

import { useEffect, useRef } from "react";

/**
 * Fundo em meio-tom: uma grade de pontos bem sutil sobre o fundo escuro.
 *
 * Os pontos existem na tela inteira, quase apagados. Uma meia-lua de luz no
 * topo e duas luzes que passeiam devagar os acendem por onde passam — os
 * pontos crescem e clareiam em degradê —, e uma onda diagonal atravessa a
 * grade. É desenhado em canvas porque o efeito depende de cada ponto ter o
 * próprio raio, o que um gradiente em CSS não faz. A cor vem de `--primary`,
 * para acompanhar o tema. Com movimento reduzido, desenha um quadro e para.
 * O mesmo desenho está no tema da tela de login (infnethub.js).
 */
const PASSO = 8;           // distância entre pontos, em px
const RAIO_MAXIMO = 1.15;  // raio de um ponto aceso
const OPACIDADE = 0.5;     // teto de opacidade: o fundo é textura, não assunto
const BASE = 0.12;         // o quanto um ponto aparece longe de qualquer luz
const PERIODO_ONDA = 16;   // segundos para uma onda atravessar a tela
const BASE_ESTATICA = 0.3; // o mesmo, no fundo parado (o hub)

function suave(a: number, b: number, x: number) {
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
}

export default function FundoMeioTom({ className, estatico = false }: {
  className?: string;
  /** sem movimento: só o pontilhado e a meia-lua, desenhados uma vez (o hub) */
  estatico?: boolean;
}) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;

    const parado = estatico || window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let largura = 0, altura = 0, quadro = 0, anterior = 0;
    // o mouse não acende nada: ele só puxa, devagar, as formas de luz para perto de si
    let alvoX = -1, alvoY = -1, cursorX = -1, cursorY = -1, cursorForca = 0, cursorAlvo = 0;
    const aoMover = (e: PointerEvent) => {
      const r = canvas.getBoundingClientRect();
      alvoX = e.clientX - r.left; alvoY = e.clientY - r.top; cursorAlvo = 1;
      if (cursorX < 0) { cursorX = alvoX; cursorY = alvoY; }
    };
    const aoSair = () => { cursorAlvo = 0; };

    const cor = () =>
      getComputedStyle(canvas).getPropertyValue("--primary").trim() || "#3b8ef5";
    // no tema claro os pontos são escuros sobre o claro: a mesma textura pede menos opacidade
    const teto = () =>
      canvas.closest("[data-theme]")?.getAttribute("data-theme") === "light" ? OPACIDADE * 0.6 : OPACIDADE;

    const ajustar = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      largura = canvas.clientWidth;
      altura = canvas.clientHeight;
      canvas.width = Math.round(largura * dpr);
      canvas.height = Math.round(altura * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    const desenhar = (ms: number) => {
      const t = ms / 1000;
      const diagonal = Math.hypot(largura, altura);
      // a meia-lua: o centro fica acima da tela, só a metade de baixo aparece
      const mx = largura / 2, my = -altura * 0.2;
      const mrx = Math.max(largura * 0.62, 560), mry = altura * 0.78;
      // as duas luzes que passeiam
      let l1x = largura * (0.5 + 0.34 * Math.sin(t * 0.055)), l1y = altura * (0.58 + 0.26 * Math.sin(t * 0.035 + 1));
      let l2x = largura * (0.5 + 0.36 * Math.sin(t * 0.04 + 2.4)), l2y = altura * (0.5 + 0.3 * Math.sin(t * 0.05 + 4));
      const l1r = diagonal * 0.3, l2r = diagonal * 0.24;

      // o pulso: ondas de luz que nascem no canto inferior esquerdo e se abrem pela
      // tela, uma atrás da outra, crescendo e se apagando devagar
      const px = largura / 2, py = altura * 0.5, ox = 0, oy = altura, pw = diagonal * 0.14;
      const fases = [(t / PERIODO_ONDA) % 1, (t / PERIODO_ONDA + 0.5) % 1];

      cursorX += (alvoX - cursorX) * 0.05; cursorY += (alvoY - cursorY) * 0.05;
      cursorForca += (cursorAlvo - cursorForca) * 0.03;
      // as luzes se inclinam na direção do ponteiro
      const puxa = estatico ? 0 : 0.22 * cursorForca;
      l1x += (cursorX - l1x) * puxa; l1y += (cursorY - l1y) * puxa;
      l2x += (cursorX - l2x) * puxa * 0.7; l2y += (cursorY - l2y) * puxa * 0.7;

      ctx.clearRect(0, 0, largura, altura);
      const opacidade = teto();
      // um véu de luz contínuo por baixo dos pontos: é ele que faz o claro se
      // desfazer no escuro em degradê; os pontos só acompanham, de leve
      if (!estatico) {
        const veu = (cx: number, cy: number, r0: number, r1: number, pico: number, a: number) => {
          if (a <= 0.002 || r1 <= 0) return;
          const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r1);
          g.addColorStop(Math.max(0, r0 / r1), "rgba(59,142,245,0)");
          g.addColorStop(Math.min(0.999, Math.max(0.001, pico)), `rgba(59,142,245,${a})`);
          g.addColorStop(1, "rgba(59,142,245,0)");
          ctx.fillStyle = g;
          ctx.fillRect(0, 0, largura, altura);
        };
        veu(l1x, l1y, 0, l1r * 1.3, 0.001, 0.035);
        veu(l2x, l2y, 0, l2r * 1.3, 0.001, 0.03);
        for (const fase of fases) {
          const R = diagonal * 1.25 * fase, fim = R + pw * 2;
          veu(ox, oy, Math.max(0, R - pw * 2), fim, R / fim, 0.03 * Math.sin(Math.PI * fase));
        }
      }
      ctx.fillStyle = cor();

      for (let y = PASSO / 2; y < altura; y += PASSO) {
        for (let x = PASSO / 2; x < largura; x += PASSO) {
          const lua = 1 - suave(0, 1, Math.hypot((x - mx) / mrx, (y - my) / mry));
          // queda gaussiana: a luz se desfaz aos poucos, sem borda entre claro e escuro
          const d1 = Math.hypot(x - l1x, y - l1y) / l1r, d2 = Math.hypot(x - l2x, y - l2y) / l2r;
          const luzes = Math.max(Math.exp(-2.2 * d1 * d1), Math.exp(-2.2 * d2 * d2));
          const onda = 0.5 + 0.5 * Math.sin((x * 0.7 + y) * 0.006 - t * 0.22);
          // parado, não há luzes passeando para revelar os pontos: a base sobe
          // um pouco, para o pontilhado aparecer também longe da meia-lua
          // cada onda cresce de 0 até além da tela; o seno da fase faz ela
          // surgir e sumir aos poucos, sem aparecer do nada
          let portal = 0;
          if (!estatico) {
            const dCanto = Math.hypot(x - ox, y - oy), ang = Math.atan2(oy - y, x - ox);
            for (const fase of fases) {
              // perto do ponteiro a frente da onda se adianta um pouco, como se fosse atraída
              const dPonteiro = Math.hypot(x - cursorX, y - cursorY) / (diagonal * 0.16);
              const raio = diagonal * 1.25 * fase * (1 + 0.05 * Math.sin(4 * ang + t * 0.4))
                + diagonal * 0.06 * cursorForca * Math.exp(-dPonteiro * dPonteiro);
              const dp = (dCanto - raio) / pw;
              // peristalse: faixas lentas correm por dentro da onda, contraindo e soltando
              const contracao = 0.7 + 0.3 * Math.sin(dCanto * 0.014 - t * 0.6);
              portal = Math.max(portal, Math.sin(Math.PI * fase) * contracao * Math.exp(-dp * dp));
            }
          }
          // o centro, onde fica o cartão, recebe pouca luz: o efeito vive em volta
          const dc = Math.hypot(x - px, y - py) / (diagonal * 0.22);
          const fora = 1 - 0.8 * Math.exp(-dc * dc);
          const i = Math.min(1, (estatico ? BASE_ESTATICA : BASE) + 0.85 * lua * lua
            + (estatico ? 0 : fora * (0.55 * luzes * (0.6 + 0.4 * onda) + 0.28 * portal)));
          // o ponto apagado não some de todo: o salto entre escuro e aceso fica suave
          ctx.globalAlpha = opacidade * (0.3 + 0.7 * i);
          ctx.beginPath();
          ctx.arc(x, y, RAIO_MAXIMO * (0.45 + 0.55 * i), 0, Math.PI * 2);
          ctx.fill();
        }
      }
      ctx.globalAlpha = 1;
    };

    const laco = (ms: number) => {
      // ~30 quadros por segundo bastam para um movimento tão lento
      if (ms - anterior > 33) { desenhar(ms); anterior = ms; }
      quadro = requestAnimationFrame(laco);
    };

    ajustar();
    const aoRedimensionar = () => { ajustar(); desenhar(performance.now()); };
    window.addEventListener("resize", aoRedimensionar);
    if (!estatico) {
      window.addEventListener("pointermove", aoMover);
      document.documentElement.addEventListener("mouseleave", aoSair);
    }

    // parado, redesenha só quando o tema muda (a cor dos pontos vem dele)
    const tema = new MutationObserver(() => { if (parado) desenhar(0); });
    tema.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

    if (parado) desenhar(0);
    else quadro = requestAnimationFrame(laco);

    return () => {
      cancelAnimationFrame(quadro);
      window.removeEventListener("resize", aoRedimensionar);
      window.removeEventListener("pointermove", aoMover);
      document.documentElement.removeEventListener("mouseleave", aoSair);
      tema.disconnect();
    };
  }, [estatico]);

  return <canvas ref={ref} className={className} aria-hidden />;
}
