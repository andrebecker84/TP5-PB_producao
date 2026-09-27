"use client";

import React, { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Compass } from "lucide-react";
import HexLogo from "@/components/ui/HexLogo";
import styles from "./not-found.module.css";

/* ──────────────────────────────────────────────────────────────────────────
   404 — a curva da Terra vista da órbita.

   A fotografia já traz a luz toda: limbo azul aceso, nascer do sol à direita,
   atmosfera. O CSS só acrescenta o que a foto não tem — céu, tipografia,
   ações — e a atmosfera irradiando, que segue a curva medida do limbo.
   ────────────────────────────────────────────────────────────────────────── */

/* Corta em 3 casas. Semente fixa garante a MESMA sequência nos dois lados, mas
   isso não basta: o React serializa os números do estilo inline com 6 dígitos
   significativos no HTML do servidor e compara com o valor de precisão total no
   cliente. `54.73748448059778` virava `54.7375%` de um lado só, e a hidratação
   acusava divergência. Cortando na origem, os dois escrevem a mesma string. */
const a3 = (v: number) => Math.round(v * 1000) / 1000;

/* estrelas de semente fixa: servidor e cliente desenham o mesmo céu */
function semear(n: number) {
  let s = 20260724;
  const r = () => ((s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  return Array.from({ length: n }, () => {
    const t = r();
    return {
      x: a3(r() * 100),
      // até 90%: as estrelas descem pelas laterais do planeta (onde o PNG é
      // transparente); atrás da parte opaca da Terra elas ficam ocultas
      y: a3(r() * 90),
      tam: a3(0.8 + t * t * 2.2),
      dur: a3(3 + r() * 5),
      atraso: a3(r() * 7),
      min: a3(0.05 + r() * 0.14),
      max: a3(0.45 + r() * 0.5),
      quente: r() > 0.9,
    };
  });
}
/* O canto inferior esquerdo ficava vazio: a semeadura geral vai só até 90% da
   altura e, mais abaixo, a Terra ocupa o centro e a direita — sobra um buraco
   preto à esquerda. Este punhado extra cai exatamente lá. Semente própria, para
   não deslocar o desenho do céu principal. */
function semearCantoInferior(n: number) {
  let s = 553311;
  const r = () => ((s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  return Array.from({ length: n }, () => {
    const t = r();
    return {
      x: a3(r() * 33),
      y: a3(60 + r() * 38),
      tam: a3(0.7 + t * t * 1.9),
      dur: a3(3 + r() * 5),
      atraso: a3(r() * 7),
      min: a3(0.05 + r() * 0.13),
      max: a3(0.4 + r() * 0.45),
      quente: r() > 0.9,
    };
  });
}
const ESTRELAS = [...semear(170), ...semearCantoInferior(42)];

/* Estilhaços de meteoro e poeira cósmica ao redor do astronauta. Semente fixa
   pelo mesmo motivo das estrelas: servidor e cliente precisam gerar a mesma
   disposição, senão a hidratação acusa divergência. */
function semearDestrocos() {
  let s = 91117;
  const r = () => ((s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  /* mesmo corte de 3 casas das estrelas, e pelo mesmo motivo — aqui era ainda
     mais grave, porque `cos` e `sqrt` na conta produzem decimais longos em
     todos os grãos */
  // Com o PNG limpo — sem os cubos que vinham no render —, tanto estilhaços
  // quanto poeira voltam a envolver o astronauta por INTEIRO, sem zona vetada.
  // Os dois usam coordenadas polares em torno dele; o que os separa é o raio:
  // os estilhaços ficam de 74% para fora, longe do corpo, e a poeira começa em
  // 26% e vai até a borda, preenchendo o vácuo em volta.
  const pedras = Array.from({ length: 13 }, () => {
    const ang = r() * Math.PI * 2;
    // Teto em 114%: o astronauta fica a 5% da borda esquerda, e com raio maior
    // os fragmentos daquele lado caíam FORA da janela — perdiam o hover junto.
    const raio = 60 + Math.sqrt(r()) * 54;
    // O tamanho sai do RAIO, não de um sorteio à parte: quanto mais perto do
    // astronauta, maior o fragmento. É a pista de profundidade que faltava —
    // com tamanho aleatório, uma pedra grande lá longe achatava a cena.
    const perto = 1 - (raio - 60) / 54;      // 1 no mais próximo, 0 no mais distante
    /* Piso de −30% no eixo X. O astronauta fica a 5% da borda esquerda e sua
       caixa mede ~14vw, então um fragmento em −50% cai FORA da janela — e
       fragmento fora da janela é fragmento que o mouse nunca alcança. Resolver
       isso no cliente, medindo e puxando de volta, se mostrou frágil demais
       (transformações aninhadas, rotação, escala animada); cortar na semeadura
       garante que o problema simplesmente não exista. */
    return {
      x: a3(Math.max(-30, 50 + raio * Math.cos(ang))),
      y: a3(52 + raio * Math.sin(ang)),
      tam: a3(5.5 + perto * 8),
      dur: a3(16 + r() * 22), atraso: a3(-r() * 30),
      giro: a3((r() > 0.5 ? 1 : -1) * (180 + r() * 360)),
      dx: a3(-12 + r() * 24), dy: a3(-14 + r() * 22),
      forma: 1 + Math.floor(r() * 3),
    };
  });
  // A poeira, ao contrário dos estilhaços, envolve o astronauta por inteiro —
  // é ela que dá densidade ao vácuo e tira a sensação de recorte colado.
  // Semeada em coordenadas POLARES, e com o raio pelo sqrt: sorteando o raio
  // direto, a densidade se concentraria no centro, porque a área de cada anel
  // cresce com o raio. Com sqrt a nuvem fica homogênea de verdade.
  // O raio vem pelo sqrt: sorteado direto, a densidade se concentraria perto do
  // centro, porque a área de cada anel cresce com o raio. Com sqrt a nuvem sai
  // homogênea de verdade. E começa em 26% para os grãos não caírem no corpo.
  const poeira = Array.from({ length: 58 }, () => {
    const ang = r() * Math.PI * 2;
    const raio = 26 + Math.sqrt(r()) * 76;
    return {
      x: a3(50 + raio * Math.cos(ang)),
      y: a3(52 + raio * Math.sin(ang)),
      tam: a3(0.7 + r() * 2.1),
      dur: a3(20 + r() * 26), atraso: a3(-r() * 40),
      dx: a3(-16 + r() * 32), dy: a3(-18 + r() * 30),
      op: a3(0.34 + r() * 0.5),
      // força do halo, sorteada por grão: alguns quase secos, outros bem acesos
      glow: a3(0.35 + r() * 1.5),
    };
  });
  return { pedras, poeira };
}
const { pedras: ESTILHACOS, poeira: POEIRA_COSMICA } = semearDestrocos();

/* Texto em arco: cada caractere gira e desce conforme a distância ao centro,
   como letras pousadas no topo de um círculo — o mesmo partido do 404. */
function ArcoTexto({ texto, chClass }: { texto: string; chClass: string }) {
  const chars = [...texto];
  const centro = (chars.length - 1) / 2;
  return (
    <>
      {chars.map((ch, i) => {
        const off = i - centro;
        // arredondado pelo mesmo motivo das estrelas: off * 3.2 produz
        // -9.600000000000001, e servidor e cliente serializam isso diferente
        const rot = a3(off * 3.2);              // graus
        const dy = a3(Math.abs(off) * 0.065);   // em — extremos descem
        return (
          <span
            key={i}
            className={chClass}
            data-c={ch}
            style={{ transform: `translateY(${dy}em) rotate(${rot}deg)` }}
          >
            {ch === " " ? " " : ch}
          </span>
        );
      })}
    </>
  );
}

export default function NotFound() {
  const router = useRouter();
  const palcoRef = useRef<HTMLDivElement>(null);
  const [par, setPar] = useState({ x: 0, y: 0 });

  /* ── Chute nos estilhaços ────────────────────────────────────────────────
     Encostar o mouse manda o fragmento embora, girando, para outro ponto da
     órbita — e ele FICA lá. Não dá para fazer isso só em CSS: `:hover` volta
     ao estado original assim que o ponteiro sai, e o que se quer aqui é o
     contrário, uma mudança que persiste.
     O deslocamento vai em `transform`, que está livre: `translate` e `rotate`
     são das animações de deriva e giro, e propriedades separadas se compõem —
     o fragmento é arremessado sem parar de flutuar. */
  const [chutes, setChutes] = useState<Record<number, { x: number; y: number; g: number }>>({});
  const camadaAstro = useRef<HTMLDivElement>(null);

  /* Posição de REPOUSO do fragmento: onde ele estaria com deslocamento zero.
     É a peça que faltava. `getBoundingClientRect` devolve a posição atual —
     que, no meio da transição de 2,2s, é um ponto INTERMEDIÁRIO —, enquanto o
     valor guardado em `chutes` é o destino FINAL. Somar um ao outro mistura
     dois espaços de coordenadas, e era isso que mandava os fragmentos para
     fora da tela: a conta partia de onde ele estava passando, não de onde iria
     parar. Descontando a translação viva da matriz, sobra o repouso, e todo o
     resto da conta passa a acontecer num espaço só. */
  const repouso = (el: HTMLElement) => {
    const cs = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    /* ESCALA ACUMULADA dos ancestrais. O fragmento mora dentro do
       `.astroSequito`, que tem escala animada (0,72 a 1) — e ainda dentro de
       `.astro`, que o usuário pode ampliar ou reduzir. Um deslocamento escrito
       no `transform` do fragmento vale no espaço LOCAL já escalado, não em
       pixels de tela: pedir 100px com o pai a 0,72 anda 72px.

       Tem de sair da MATRIZ composta dos ancestrais. Tentar deduzi-la da razão
       entre a caixa medida e a largura declarada dá errado: o fragmento e o
       séquito são rotacionados, e a caixa delimitadora de algo girado é sempre
       maior que o elemento — aquilo media 1,4 com escala real 1, e o
       deslocamento saía encolhido pelo fator errado.
       `hypot(a, b)` extrai a escala e ignora a rotação, que é exatamente o que
       se quer aqui. */
    let acum = new DOMMatrix();
    for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
      const c = getComputedStyle(p);
      const t = c.transform && c.transform !== "none" ? new DOMMatrix(c.transform) : new DOMMatrix();
      const s = c.scale && c.scale !== "none" ? c.scale.split(" ").map(Number) : [1, 1];
      acum = new DOMMatrix().scaleSelf(s[0], s[1] ?? s[0]).multiplySelf(t).multiplySelf(acum);
    }
    const esc = Math.hypot(acum.a, acum.b) || 1;
    // `transform` computa como "none" enquanto o fragmento nunca foi tocado, e
    // `new DOMMatrixReadOnly("none")` LANÇA — sem esta guarda a exceção
    // derrubava justamente os intocados, que são os que precisam ser recolhidos
    const t = cs.transform;
    const m = t && t !== "none" ? new DOMMatrixReadOnly(t) : null;
    return {
      // m41/m42 estão no espaço local; multiplicados por `esc` viram tela
      x: r.x + r.width / 2 - (m?.m41 ?? 0) * esc,
      y: r.y + r.height / 2 - (m?.m42 ?? 0) * esc,
      raio: r.width / 2,
      esc,
    };
  };

  /* Prende o ponto dentro da janela REFLETINDO nas bordas — bate e volta, em
     vez de ser cortado rente à parede. O laço repete porque um arremesso forte
     na diagonal estoura os dois eixos, e uma reflexão só não resolve. */
  const rebater = (x: number, y: number, raio: number) => {
    const m = 12 + raio;
    const lx = [m, window.innerWidth - m], ly = [m, window.innerHeight - m];
    for (let volta = 0; volta < 6; volta++) {
      if (x < lx[0]) x = 2 * lx[0] - x; else if (x > lx[1]) x = 2 * lx[1] - x;
      if (y < ly[0]) y = 2 * ly[0] - y; else if (y > ly[1]) y = 2 * ly[1] - y;
      if (x >= lx[0] && x <= lx[1] && y >= ly[0] && y <= ly[1]) break;
    }
    // janela menor que o fragmento: sem canto válido, centraliza
    return { x: lx[0] > lx[1] ? window.innerWidth / 2 : x, y: ly[0] > ly[1] ? window.innerHeight / 2 : y };
  };

  /* Recolhe os que NASCEM fora da janela. A semeadura é polar em torno do
     astronauta e roda no servidor, que não conhece o tamanho da tela; como ele
     fica a 5% da borda esquerda, parte da nuvem caía para fora — e fragmento
     fora da tela é fragmento que ninguém alcança. Roda de novo a cada
     redimensionamento, porque o que cabia numa largura pode não caber na outra. */
  useEffect(() => {
    const recolher = () => {
      const alvos = camadaAstro.current?.querySelectorAll<HTMLElement>("[data-estilhaco]");
      if (!alvos?.length) return;
      setChutes(atual => {
        const novo = { ...atual };
        let mudou = false;
        alvos.forEach(el => {
          const i = Number(el.dataset.estilhaco);
          const casa = repouso(el);
          const ant = novo[i] ?? { x: 0, y: 0, g: 0 };
          // tudo em coordenadas de TELA: o offset guardado é local, então sobe
          // multiplicado por `esc` e volta dividido
          const dentro = rebater(casa.x + ant.x * casa.esc, casa.y + ant.y * casa.esc, casa.raio);
          const nx = (dentro.x - casa.x) / casa.esc, ny = (dentro.y - casa.y) / casa.esc;
          if (Math.abs(nx - ant.x) > 0.5 || Math.abs(ny - ant.y) > 0.5) {
            novo[i] = { x: nx, y: ny, g: ant.g };
            mudou = true;
          }
        });
        return mudou ? novo : atual;
      });
    };
    recolher();
    window.addEventListener("resize", recolher);
    return () => window.removeEventListener("resize", recolher);
  }, []);

  const chutar = (i: number, alvo: HTMLElement) => {
    const casa = repouso(alvo);
    setChutes(atual => {
      const ant = atual[i] ?? { x: 0, y: 0, g: 0 };
      const dir = Math.random() * Math.PI * 2;
      const forca = 70 + Math.random() * 120;
      const destino = rebater(
        casa.x + ant.x * casa.esc + Math.cos(dir) * forca,
        casa.y + ant.y * casa.esc + Math.sin(dir) * forca,
        casa.raio,
      );
      return {
        ...atual,
        [i]: {
          // de volta ao espaço local, dividindo pela escala dos ancestrais
          x: (destino.x - casa.x) / casa.esc,
          y: (destino.y - casa.y) / casa.esc,
          g: ant.g + (Math.random() > 0.5 ? 1 : -1) * (300 + Math.random() * 480),
        },
      };
    });
  };

  /* ── Arrastar o astronauta ───────────────────────────────────────────────
     Pointer Events, não mouse: o mesmo código atende toque e caneta. O
     `setPointerCapture` é o que garante que o arrasto continue mesmo se o
     ponteiro sair do corpo dele — sem isso o movimento trava na borda. */
  const [arrasto, setArrasto] = useState({ x: 0, y: 0 });
  const [arrastando, setArrastando] = useState(false);
  const inicio = useRef({ px: 0, py: 0, ox: 0, oy: 0 });

  /* Escala e rotação pela roda do mouse. Vão em `scale` e `rotate` de `.astro`,
     que são propriedades LIVRES nessa camada — as animações de deriva, giro e
     afastamento moram em `.astroDeriva`, um nível abaixo. Por serem separadas,
     compõem-se com o que já existe: o ajuste manual multiplica o movimento
     automático em vez de cancelá-lo.
     Roda pura muda o tamanho; com Shift, gira. */
  const [manejo, setManejo] = useState({ escala: 1, giro: 0 });
  const rodar = (e: React.WheelEvent<HTMLElement>) => {
    e.preventDefault();
    if (!e.shiftKey) piscarRoda(e.deltaY > 0 ? "menos" : "mais");
    setManejo(m =>
      e.shiftKey
        ? { ...m, giro: m.giro + (e.deltaY > 0 ? 6 : -6) }
        : { ...m, escala: Math.min(1.6, Math.max(0.3, m.escala * (e.deltaY > 0 ? 0.92 : 1.08))) },
    );
  };
  /* Sumiço por BRILHO, nunca por opacidade. Baixar a opacidade deixaria ele
     translúcido, e sobre o 404, o planeta, a antena — qualquer coisa atrás —
     ele viraria fantasma. Escurecendo, ele continua 100% opaco e se FUNDE ao
     preto do espaço, que é como um corpo distante realmente some.
     Funciona porque o encolhimento também o joga para trás (ver `planoAstro`):
     quando está escuro ele já está atrás do planeta, sobre o vazio, e vazio
     escuro com corpo escuro é desaparecimento de verdade. */
  const brilhoAstro = Math.min(1, Math.max(0.04, (manejo.escala - 0.3) / 0.55));

  /* PROFUNDIDADE pela escala. Os planos da cena, do fundo para a frente:
       1 estrelas · 2 o 404 · 3 a Terra · 4 a vinheta
       5 NOT FOUND, antena e satélite · 6 a nave e o texto · 7 tudo à frente
     Encolher não é só ficar menor: é estar mais LONGE, e quem está mais longe
     passa por trás. Então o z-index acompanha o tamanho, e o astronauta some
     atrás do planeta e do 404 quando recua, e volta à frente quando se
     aproxima. Sem isso ele flutuava sempre por cima, o que achatava a cena. */
  const planoAstro = manejo.escala < 0.62 ? 1 : manejo.escala < 0.95 ? 4 : 7;
  /* duplo clique devolve tudo ao lugar — sem isso, quem exagerar no zoom fica
     sem caminho de volta a não ser recarregar a página */
  const repor = () => { setManejo({ escala: 1, giro: 0 }); setArrasto({ x: 0, y: 0 }); };

  /* Teclado, para quem não quer depender da roda: setas ← → giram, ↑ ↓ mudam o
     tamanho, Esc repõe. Ficam desligadas enquanto o foco está num botão, senão
     as setas roubariam a navegação por teclado da página. */
  /* Estados que a legenda espelha: o cursor sobre um meteoro e a roda em uso.
     A roda não tem evento de "soltar", então ela apaga sozinha por tempo. */
  const [tocandoMeteoro, setTocandoMeteoro] = useState(false);
  // guarda também a DIREÇÃO, para o indicador saber se mostra + ou −
  const [rodando, setRodando] = useState<null | "mais" | "menos">(null);
  const rodaTimer = useRef<number | undefined>(undefined);
  const piscarRoda = (dir: "mais" | "menos") => {
    setRodando(dir);
    window.clearTimeout(rodaTimer.current);
    rodaTimer.current = window.setTimeout(() => setRodando(null), 420);
  };

  const [aceso, setAceso] = useState<string | null>(null);
  useEffect(() => {
    const tecla = (e: KeyboardEvent) => {
      const foco = document.activeElement;
      if (foco && foco !== document.body && foco.tagName === "BUTTON") return;
      const passo = e.shiftKey ? 15 : 5;
      if (e.key === "ArrowLeft")       setManejo(m => ({ ...m, giro: m.giro - passo }));
      else if (e.key === "ArrowRight") setManejo(m => ({ ...m, giro: m.giro + passo }));
      else if (e.key === "ArrowUp")    setManejo(m => ({ ...m, escala: Math.min(1.6, m.escala * 1.08) }));
      else if (e.key === "ArrowDown")  setManejo(m => ({ ...m, escala: Math.max(0.3, m.escala * 0.92) }));
      else if (e.key === "Escape")     repor();
      else return;
      setAceso(e.key);
      e.preventDefault();
    };
    const soltou = () => setAceso(null);
    window.addEventListener("keydown", tecla);
    window.addEventListener("keyup", soltou);
    window.addEventListener("blur", soltou);
    return () => {
      window.removeEventListener("keydown", tecla);
      window.removeEventListener("keyup", soltou);
      window.removeEventListener("blur", soltou);
    };
  }, []);

  const pegar = (e: React.PointerEvent<HTMLElement>) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    inicio.current = { px: e.clientX, py: e.clientY, ox: arrasto.x, oy: arrasto.y };
    setArrastando(true);
  };
  const mover = (e: React.PointerEvent<HTMLElement>) => {
    if (!arrastando) return;
    const i = inicio.current;
    setArrasto({ x: i.ox + (e.clientX - i.px), y: i.oy + (e.clientY - i.py) });
  };
  const soltar = (e: React.PointerEvent<HTMLElement>) => {
    if (!arrastando) return;
    e.currentTarget.releasePointerCapture(e.pointerId);
    setArrastando(false);
  };

  /* Paralaxe em três ritmos: estrelas < planeta < tipografia.
     O evento de mouse dispara muito mais que 60 vezes por segundo, e cada
     disparo aqui reavalia a árvore inteira. Guardando a última posição e
     deixando o `requestAnimationFrame` publicá-la, o React re-renderiza no
     máximo UMA vez por quadro — que é o teto do que a tela consegue mostrar. */
  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let pendente = 0;
    let ultimo = { x: 0, y: 0 };
    const publicar = () => { pendente = 0; setPar(ultimo); };
    const mover = (e: MouseEvent) => {
      const r = palcoRef.current?.getBoundingClientRect();
      if (!r) return;
      ultimo = {
        x: (e.clientX - (r.left + r.width / 2)) / r.width,
        y: (e.clientY - (r.top + r.height / 2)) / r.height,
      };
      if (!pendente) pendente = requestAnimationFrame(publicar);
    };
    window.addEventListener("mousemove", mover, { passive: true });
    return () => {
      window.removeEventListener("mousemove", mover);
      if (pendente) cancelAnimationFrame(pendente);
    };
  }, []);

  return (
    <div className={styles.page} ref={palcoRef}>
      {/* ── Céu profundo ── */}
      <div className={styles.espaco} aria-hidden />

      {/* ── Fonte de luz no topo, com raios ─────────────────────────────────
          O sol fica fora do quadro, acima: só o clarão e o leque de raios
          descendo entram na cena. Em `screen`, para somar luz sem cinzentar
          o preto do espaço. */}
      <div
        className={styles.luzTopo}
        style={{ transform: `translateX(-50%) translate(${par.x * -7}px, ${par.y * -4}px)` }}
        aria-hidden
      />
      <div
        className={styles.raios}
        style={{ transform: `translateX(-50%) translate(${par.x * -10}px, ${par.y * -5}px)` }}
        aria-hidden
      />
      {/* faixa de poeira estelar, como a Via Láctea ao fundo — elíptica e
          desfocada, sem borda reta */}
      <div className={styles.nebula} aria-hidden />
      {/* poeira cósmica espalhada pelo universo inteiro, atrás de tudo */}
      <div className={styles.poeiraFundo} aria-hidden />

      <div
        className={styles.estrelas}
        style={{ transform: `translate(${par.x * -5}px, ${par.y * -3}px)` }}
        aria-hidden
      >
        {ESTRELAS.map((e, i) => (
          <span
            key={i}
            className={`${styles.estrela} ${e.quente ? styles.estrelaQuente : ""}`}
            style={{
              left: `${e.x}%`, top: `${e.y}%`,
              width: `${e.tam}px`, height: `${e.tam}px`,
              ["--dur" as string]: `${e.dur}s`,
              ["--atraso" as string]: `${e.atraso}s`,
              ["--min" as string]: e.min,
              ["--max" as string]: e.max,
            }}
          />
        ))}
      </div>

      {/* ── Logotipo, no canto superior esquerdo ── */}
      <header className={styles.marca}>
        <HexLogo size={40} id="hexGrad404" />
        <span className={styles.marcaTxt}>Infnet<b>Hub</b></span>
      </header>

      {/* ── O planeta ── */}
      <div
        className={styles.terra}
        style={{ transform: `translateX(-50%) translate(${par.x * -12}px, ${par.y * -6}px)` }}
        aria-hidden
      >
        {/* atmosfera: anéis alinhados ao limbo, atrás da foto — só aparecem
            onde o PNG é transparente, irradiando para o espaço */}
        <span className={`${styles.limbo} ${styles.limboLargo}`} />
        <span className={`${styles.limbo} ${styles.limboQuente}`} />
        <span className={`${styles.limbo} ${styles.limboFino}`} />

        <picture>
          <source media="(max-width: 620px)" srcSet="/images/404/terra-sm-v8.webp" />
          <img
            src="/images/404/terra-v8.webp"
            alt=""
            width={7680}
            height={1440}
            fetchPriority="high"
            decoding="async"
            className={styles.terraImg}
          />
        </picture>
        {/* luz do anel POR CIMA da foto, em `screen`: a borda do recorte tem
            pixels escuros com alfa parcial que, sobre o brilho de trás,
            escureciam e apareciam como um risco na linha do horizonte. Em
            `screen` a camada só clareia — o risco deixa de existir. */}
        <span className={`${styles.limbo} ${styles.limboLuz}`} />
      </div>

      {/* escurecimento da base, para o texto assentar sobre o planeta */}
      <div className={styles.vinheta} aria-hidden />

      {/* ── Astronauta à deriva ──────────────────────────────────────────────
          Flutua em órbita, girando devagar. A luz vem do topo, como o resto da
          cena: o halo frio nasce acima dele e a sombra cai para baixo. */}
      <div
        className={styles.astro}
        ref={camadaAstro}
        style={{
          // o arrasto soma ao paralaxe no MESMO translate; enquanto o dedo
          // está pressionado a transição é desligada, senão o corpo persegue o
          // ponteiro com 0,8s de atraso e o arrasto parece emborrachado
          transform: `translate(${par.x * -8 + arrasto.x}px, ${par.y * -5 + arrasto.y}px)`,
          scale: manejo.escala,
          filter: brilhoAstro < 1 ? `brightness(${brilhoAstro})` : undefined,
          zIndex: planoAstro,
          transition: arrastando ? "none" : undefined,
        }}
        aria-hidden
      >
        <span className={styles.astroLuz} />

        {/* detritos presos à órbita dele: o séquito repete as animações de
            órbita e afastamento do astronauta, então a nuvem viaja junto em vez
            de ficar parada enquanto ele recua */}
        <div className={styles.astroSequito}>
        {/* poeira cósmica: partículas finas boiando junto, mais numerosas e
            muito mais lentas que os estilhaços — é o que dá densidade ao vácuo */}
        {POEIRA_COSMICA.map((p, i) => (
          <span
            key={"p" + i}
            className={styles.poeiraCosmica}
            style={{
              left: `${p.x}%`, top: `${p.y}%`,
              width: `${p.tam}px`, height: `${p.tam}px`,
              opacity: p.op,
              ["--glow" as string]: p.glow,
              ["--dur" as string]: `${p.dur}s`,
              ["--atraso" as string]: `${p.atraso}s`,
              ["--dx" as string]: `${p.dx}%`,
              ["--dy" as string]: `${p.dy}%`,
            }}
          />
        ))}

        {/* estilhaços de meteoro à deriva junto dele — cada um com tamanho,
            trajeto e rotação próprios, para não lerem como um padrão */}
        {ESTILHACOS.map((e, i) => {
          const chute = chutes[i];
          return (
            <span
              key={i}
              className={styles.estilhacoOrbe}
              data-estilhaco={i}
              onPointerOver={ev => { chutar(i, ev.currentTarget); setTocandoMeteoro(true); }}
              onPointerOut={() => setTocandoMeteoro(false)}
              style={{
                left: `${e.x}%`, top: `${e.y}%`,
                width: `${e.tam}px`, height: `${e.tam}px`,
                ...(chute && { transform: `translate(${chute.x}px, ${chute.y}px)` }),
              }}
            >
              <span
                className={`${styles.estilhaco} ${styles["forma" + e.forma]}`}
                style={{
                  ["--dur" as string]: `${e.dur}s`,
                  ["--atraso" as string]: `${e.atraso}s`,
                  ["--giro" as string]: `${e.giro}deg`,
                  ["--dx" as string]: `${e.dx}%`,
                  ["--dy" as string]: `${e.dy}%`,
                }}
              />
            </span>
          );
        })}
        </div>

        <div className={styles.astroDeriva}>
          {/* versão sem os cubos do render: com o PNG limpo, os estilhaços e a
              poeira podem envolver o astronauta em todas as direções sem
              disputar espaço com o que já vinha na imagem.
              É o único elemento arrastável da cena. */}
          {/* eslint-disable-next-line @next/next/no-img-element -- arrastável, com transformações próprias */}
          <img src="/images/404/astronauta-b.webp" alt=""
            className={`${styles.astroImg} ${arrastando ? styles.astroPegado : ""}`}
            /* A rotação manual vive AQUI, e não na camada de fora. A caixa da
               camada é bem maior que a figura e o corpo dentro dela é deslocado
               o tempo todo pelas animações de órbita e afastamento — girar por
               ali usava um eixo que não é o dele, e ele parecia pendurado. A
               caixa da imagem é exatamente o astronauta, então 50%/50% aqui é o
               eixo dele de verdade. */
            style={{ rotate: `${manejo.giro}deg` }}
            width={900} height={1076} decoding="async" draggable={false}
            onPointerDown={pegar} onPointerMove={mover}
            onPointerUp={soltar} onPointerCancel={soltar}
            onWheel={rodar} onDoubleClick={repor} />
        </div>
      </div>

      {/* ── Disco voador ─────────────────────────────────────────────────────
          Sobe pela esquerda e some no espaço. A imagem é o render original,
          sem recorte por chroma — o céu dela foi esmagado para preto e ela
          entra em `screen`, então o fundo some sozinho e o volume 3D fica
          intacto. O feixe de propulsão encolhe junto na subida. */}
      <div className={styles.ufoCamada} aria-hidden>
        <div className={styles.ufo}>
          <div className={styles.ufoBalanco}>
            <span className={styles.ufoAura} />
            <span className={styles.ufoFeixe} />
            <span className={styles.ufoNucleo} />
            <span className={styles.ufoNevoa} />
            <span className={styles.ufoPoeira} />
            <span className={styles.ufoBrilho} />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/images/404/ufo-v3.webp" alt="" className={styles.ufoImg}
              width={838} height={323} decoding="async" />
          </div>
        </div>
      </div>

      {/* ── Satélite fora de alcance ─────────────────────────────────────────
          Alto e à direita, na direção geral para onde a antena aponta — e
          longe demais. Ele também chama, com arcos descendo, e também não é
          respondido. É o desencontro entre as duas emissões que conta a falta
          de conexão. Parallax mínimo: é o objeto mais distante da cena. */}
      <div
        className={styles.satelite}
        style={{ transform: `translate(${par.x * -4}px, ${par.y * -3}px)` }}
        aria-hidden
      >
        <div className={styles.satSinal}>
          <span className={styles.satOnda} />
          <span className={styles.satOnda} />
        </div>
        <div className={styles.sateliteDeriva}>
          {/* coletor de detritos */}
          {/* eslint-disable-next-line @next/next/no-img-element -- camada animada por CSS */}
          <img src="/images/404/satelite-b.webp" alt="" className={styles.sateliteImg}
            width={700} height={689} decoding="async" />
          <span className={styles.sateliteFarol} />
        </div>
      </div>

      {/* ── Antena parabólica na superfície ──────────────────────────────────
          Plantada na curva do planeta e inclinada junto com ela. Varre o céu
          procurando enlace: os arcos saem da boca do prato e se dissipam sem
          resposta, e a luz de estado na base fica piscando em vermelho.
          Acompanha o mesmo parallax da Terra — está pousada nela. */}
      <div
        className={styles.antenaCamada}
        style={{ transform: `translate(${par.x * -12}px, ${par.y * -6}px)` }}
        aria-hidden
      >
        <div className={styles.antena}>
          <span className={styles.antenaBase} />
          {/* dois arcos, não três: o primeiro parte no mesmo instante que o do
              satélite (é o par que se cruza no meio), o segundo é o eco meio
              ciclo atrás. Um terceiro cairia em cima do primeiro. */}
          <div className={styles.sinalCampo}>
            <span className={styles.onda} />
            <span className={styles.onda} />
          </div>
          <span className={styles.sinalFoco} />
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/images/404/antena.webp" alt="" className={styles.antenaImg}
            width={760} height={895} decoding="async" />
          <span className={styles.antenaLed} />
        </div>
      </div>

      {/* corte do enlace: no ponto médio exato entre a antena e o satélite, no
          instante em que os dois arcos se cruzam. Estoura em vermelho e as duas
          metades do enlace se afastam pelo eixo que liga os dois */}
      <span className={styles.falhaEnlace} aria-hidden />

      {/* reflexo da luz do topo na superfície: a faixa iluminada que desce do
          limbo, somada em `screen` sobre o planeta */}
      <div
        className={styles.reflexoTerra}
        style={{ transform: `translateX(-50%) translate(${par.x * -12}px, ${par.y * -6}px)` }}
        aria-hidden
      />


      {/* bloom do 404: luz suave que ele emana sobre o limbo e o espaço ao
          redor, na linha do horizonte, atrás das letras */}
      <div
        className={styles.numeroBloom}
        style={{ transform: `translate(-50%, -50%) translate(${par.x * -6}px, ${par.y * -4}px)` }}
        aria-hidden
      />

      {/* ── 404: atrás do planeta, nascendo da curva ─────────────────────────
          z-index abaixo da Terra — a curva oculta a base das letras. Cada
          algarismo é um elemento próprio para poder arquear acompanhando a
          curvatura do horizonte. */}
      <span
        className={styles.numero}
        style={{ transform: `translate(${par.x * -6}px, ${par.y * -4}px)` }}
        aria-hidden
      >
        <span className={styles.digito} data-c="4">4</span>
        <span className={styles.digito} data-c="0">0</span>
        <span className={styles.digito} data-c="4">4</span>
      </span>

      {/* ── NOT FOUND, deitado sobre a Terra, abaixo do 404 ──────────────────
          Perspectiva + rotateX dão o relevo 3D: as letras parecem pousadas na
          superfície e vistas de cima. Fica à frente do planeta (z 5). */}
      <div
        className={styles.titulo}
        /* mesmo deslocamento da Terra: o NOT FOUND está pousado nela, então
           acompanha o planeta no parallax em vez de derivar por conta própria */
        style={{ transform: `translate(${par.x * -12}px, ${par.y * -6}px)` }}
        aria-hidden
      >
        <span className={styles.sobre}>
          <ArcoTexto texto="NOT FOUND" chClass={styles.sobreCh} />
        </span>
      </div>

      {/* ── Controles ────────────────────────────────────────────────────────
          Nada disso é descobrível sozinho: ninguém adivinha que a roda do mouse
          redimensiona o astronauta. O painel fica apagado no canto e só acende
          ao passar o mouse, e a tecla em uso ACENDE enquanto está pressionada —
          o retorno que transforma a legenda em algo vivo em vez de decorativo. */}
      {/* `ajudaViva` acende o painel inteiro no instante em que QUALQUER controle
          é usado; as linhas paradas então recuam, e só a linha em ação fica
          legível. É o efeito de hover, mas disparado pela ação em vez do
          cursor — e restrito ao item que está acontecendo. */}
      <aside
        className={`${styles.ajuda} ${
          arrastando || rodando || tocandoMeteoro || aceso ? styles.ajudaViva : ""
        }`}
        aria-hidden
      >
        <div className={styles.ajudaLinhas}>
          <span className={`${styles.ajudaLinha} ${arrastando ? styles.linhaAtiva : ""}`}>
            <b className={`${styles.tecla} ${styles.teclaMouse} ${styles.destacaBotao} ${arrastando ? styles.teclaAtiva : ""}`}>
              <i className={`${styles.mBotao} ${styles.mEsq}`} />
              <i className={`${styles.mBotao} ${styles.mDir}`} />
              <i className={styles.mRoda} />
            </b>
            arrastar o astronauta
          </span>
          <span className={`${styles.ajudaLinha} ${rodando ? styles.linhaAtiva : ""}`}>
            <b className={`${styles.tecla} ${styles.teclaMouse} ${styles.destacaRoda} ${rodando ? styles.teclaAtiva : ""}`}>
              <i className={`${styles.mBotao} ${styles.mEsq}`} />
              <i className={`${styles.mBotao} ${styles.mDir}`} />
              <i className={styles.mRoda} />
            </b>
            {/* FORA do <b>: dentro dele o sinal herdava a rotação 3/4 do mouse
                e saía tombado. Aqui ele fica ancorado na linha, sempre de
                frente. A `key` reinicia a animação a cada giro — sem ela, rolar
                duas vezes na mesma direção só mostraria o pisco uma vez. */}
            {rodando && (
              <em key={rodando + String(rodaTimer.current)} className={styles.sinalZoom}>
                {rodando === "mais" ? "+" : "−"}
              </em>
            )}
            aproximar e afastar
          </span>
          <span className={`${styles.ajudaLinha} ${tocandoMeteoro ? styles.linhaAtiva : ""}`}>
            {/* mesmo mouse dos outros, mas DESLIZANDO: aqui não se clica, só se
                leva o cursor até o fragmento, e é o movimento do ícone que diz
                isso. E ele acende de verdade quando o cursor encosta num
                meteoro na cena — a legenda responde ao que está acontecendo. */}
            <b className={`${styles.tecla} ${styles.teclaMouse} ${styles.teclaMovendo} ${tocandoMeteoro ? styles.teclaAtiva : ""}`}>
              <i className={`${styles.mBotao} ${styles.mEsq}`} />
              <i className={`${styles.mBotao} ${styles.mDir}`} />
              <i className={styles.mRoda} />
              <i className={`${styles.mArco} ${styles.mArcoE}`} />
              <i className={`${styles.mArco} ${styles.mArcoD}`} />
            </b>
            encostar nos meteoros
          </span>

          {/* Esc antes das setas: é uma tecla avulsa e curta, e o bloco em T
              ocupa duas alturas — pondo o T por último, o grupo do teclado
              termina no elemento mais pesado em vez de deixá-lo no meio. */}
          <span className={`${styles.ajudaLinha} ${aceso === "Escape" ? styles.linhaAtiva : ""}`}>
            <b className={`${styles.tecla} ${styles.teclaLarga} ${aceso === "Escape" ? styles.teclaAcesa : ""}`}>Esc</b>
            voltar ao início
          </span>

          {/* As quatro setas no T INVERTIDO do teclado, e não em duas fileiras
              soltas: a disposição física é o que faz alguém reconhecer o
              controle sem ler. Por isso as duas funções passam a dividir um
              bloco só, com a legenda ao lado. */}
          <span className={`${styles.ajudaLinha} ${styles.linhaSetas} ${aceso && aceso.startsWith("Arrow") ? styles.linhaAtiva : ""}`}>
            <span className={styles.setas}>
              <b className={`${styles.tecla} ${styles.setaCima} ${aceso === "ArrowUp" ? styles.teclaAcesa : ""}`}>↑</b>
              <b className={`${styles.tecla} ${styles.setaEsq} ${aceso === "ArrowLeft" ? styles.teclaAcesa : ""}`}>←</b>
              <b className={`${styles.tecla} ${styles.setaBaixo} ${aceso === "ArrowDown" ? styles.teclaAcesa : ""}`}>↓</b>
              <b className={`${styles.tecla} ${styles.setaDir} ${aceso === "ArrowRight" ? styles.teclaAcesa : ""}`}>→</b>
            </span>
            <span className={styles.ajudaDupla}>
              <span>← → girar</span>
              <span>↑ ↓ tamanho</span>
            </span>
          </span>
        </div>
      </aside>

      <main className={styles.bloco}>
        <span className={styles.badge}>
          <span className={styles.badgePonto} aria-hidden /> ERROR 404: NOT FOUND
        </span>
        <h1 className={styles.tituloTxt}>Alunos, temos um problema!</h1>
        <p className={styles.sub}>
          Esta página se perdeu no espaço — nenhum sinal por aqui.
          Volte à órbita e retome a rota pelo hub.
        </p>
        <div className={styles.acoes}>
          <button className={styles.btn} onClick={() => router.push("/feed")}>
            <span className={styles.btnBrilho} aria-hidden />
            <Compass size={16} /> Ir para o feed
          </button>
          <button className={styles.btnGhost} onClick={() => router.back()}>
            <ArrowLeft size={15} /> Voltar
          </button>
        </div>
      </main>
    </div>
  );
}
