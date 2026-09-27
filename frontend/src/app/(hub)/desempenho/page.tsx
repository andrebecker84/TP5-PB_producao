"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity, TrendingUp, TrendingDown, Award, Clock, CalendarCheck,
  Sparkles, Target, Gauge, LineChart, Boxes, CheckCircle2, AlertTriangle,
  Briefcase, HandHeart, Info, Users, Flag,
} from "lucide-react";
import { useUsuarioLogado } from "@/hooks/useCurrentUser";
import { boletimService } from "@/services/boletimService";
import { CategoriaAtividade, Conceito, Desempenho } from "@/types/boletim";
import styles from "./page.module.css";

/* ──────────────────────────────────────────────────────────────────────────
   Painel de desempenho. Não há notas: tudo deriva dos CONCEITOS das
   competências (DML / DL / D / ND). O "índice" é o indicador que a plataforma
   calcula a partir deles — DML 100, DL 80, D 60, ND 0 — e serve para comparar
   evolução, nunca substitui o conceito.

   O que é REAL, vindo do boletim-service pelo API Gateway: índice geral,
   conceito equivalente, perfil de conceitos, presença ponderada, entregas no
   prazo, bloco atual, evolução por bloco, presenças do bloco em curso e carga
   horária das atividades. Nada disso é recalculado aqui — a página exibe o que
   o serviço apurou.

   O que ainda é DADO DE DEMONSTRAÇÃO: a comparação com turmas e a projeção de
   desempenho, agrupadas em DEMONSTRACAO logo abaixo. São funcionalidades
   previstas, e a interface delas já está construída; o que falta é o serviço
   modelar turma e coorte para alimentá-las. Ficam marcadas na tela com um
   aviso, para que ninguém as confunda com dado apurado enquanto isso.
   ────────────────────────────────────────────────────────────────────────── */

/* Total de blocos da graduação: estrutura do curso, não percurso do aluno. O
   serviço só conhece os blocos em que houve matrícula. */
const BLOCOS_CURSO = 12;

/* ── Dados de demonstração — aguardando suporte do serviço ──────────────────
   Comparar o aluno com a turma exige que o boletim-service passe a conhecer
   turmas: hoje ele modela o percurso de um aluno, e a média de uma coorte não
   é derivável disso. A projeção exige, além da série, um modelo de tendência
   com margem declarada. Os dois estão planejados; a interface fica pronta e
   sinalizada até que os endpoints existam. */
const DEMONSTRACAO = {
  meses: ["Fev", "Mar", "Abr", "Mai", "Jun", "Jul"],
  mesesFuturos: ["Ago", "Set"],
  aluno: [78, 82, 80, 86, 90, 92],
  turma: [74, 75, 77, 78, 80, 81],
  projecao: [94, 96],
  projecaoTurma: [82, 83],
  incerteza: 4,
  turmasAnteriores: [
    { turma: "24E2", indice: 79 },
    { turma: "25E1", indice: 82 },
    { turma: "25E2", indice: 80 },
    { turma: "26E2", indice: 84, eu: true },
  ] as { turma: string; indice: number; eu?: boolean }[],
  previsaoFormatura: "28E1",
};

const CONCEITO_DE = (i: number): Conceito => (i >= 95 ? "DML" : i >= 75 ? "DL" : i >= 55 ? "D" : "ND");

const ICONE_CATEGORIA: Record<CategoriaAtividade, typeof Clock> = {
  EXTENSAO: HandHeart,
  ELETIVA: CalendarCheck,
  ESTAGIO: Briefcase,
  COMPLEMENTAR: Sparkles,
};

const COR_CATEGORIA: Record<CategoriaAtividade, string> = {
  EXTENSAO: "#a78bfa",
  ELETIVA: "#f472b6",
  ESTAGIO: "#22d3ee",
  COMPLEMENTAR: "#fbbf24",
};

/* Catmull-Rom convertido em cúbicas: a linha passa exatamente pelos pontos,
   mas chega neles em curva, sem os bicos do polyline. */
function suave(pts: [number, number][], k = 0.85) {
  if (pts.length < 2) return "";
  let d = `M${pts[0][0]} ${pts[0][1]}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] ?? p2;
    const c1x = p1[0] + ((p2[0] - p0[0]) / 6) * k;
    const c1y = p1[1] + ((p2[1] - p0[1]) / 6) * k;
    const c2x = p2[0] - ((p3[0] - p1[0]) / 6) * k;
    const c2y = p2[1] - ((p3[1] - p1[1]) / 6) * k;
    d += ` C${c1x.toFixed(2)} ${c1y.toFixed(2)} ${c2x.toFixed(2)} ${c2y.toFixed(2)} ${p2[0].toFixed(2)} ${p2[1].toFixed(2)}`;
  }
  return d;
}

const area = (pts: [number, number][], base: number) =>
  `${suave(pts)} L${pts[pts.length - 1][0]} ${base} L${pts[0][0]} ${base} Z`;

/** Selo dos blocos que ainda não são alimentados pelo serviço. */
function SeloDemonstracao({ children }: { children: React.ReactNode }) {
  return <span className={styles.cardHint} title="Funcionalidade prevista — aguarda o serviço modelar turmas e coortes">{children}</span>;
}

export default function DesempenhoPage() {
  const currentUser = useUsuarioLogado();

  const [dados, setDados] = useState<Desempenho | null>(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [ponto, setPonto] = useState<number | null>(null);   // tooltip do gráfico de linhas
  const [barra, setBarra] = useState<number | null>(null);   // tooltip do comparativo
  const [pontoBloco, setPontoBloco] = useState<number | null>(null); // tooltip da evolução real

  const carregar = useCallback(() => {
    // Sem setLoading(true) aqui: o estado já nasce carregando, e chamar
    // setState no corpo de um efeito dispara uma renderização em cascata.
    boletimService.buscarDesempenho(currentUser.id)
      .then(setDados)
      .catch(e => setErro(e instanceof Error ? e.message : "Falha ao carregar o painel."))
      .finally(() => setLoading(false));
  }, [currentUser.id]);

  useEffect(carregar, [carregar]);

  /* ── geometria do gráfico de evolução por bloco (dados reais) ── */
  const W = 620, H = 190, ML = 34, MR = 16, MT = 16, MB = 30;

  const grafico = useMemo(() => {
    const serie = dados?.evolucao ?? [];
    if (serie.length === 0) return null;

    // Domínio calculado sobre os dados, não fixo: com escala 60–100 travada, um
    // aluno com índices na casa dos 40 sairia do gráfico pela base.
    const valores = serie.map(e => e.indice);
    const yMin = Math.max(0, Math.floor((Math.min(...valores) - 10) / 10) * 10);
    const yMax = 100;

    const px = (i: number) => serie.length === 1
      ? (ML + W - MR) / 2
      : ML + (i / (serie.length - 1)) * (W - ML - MR);
    const py = (v: number) => H - MB - ((v - yMin) / (yMax - yMin)) * (H - MT - MB);

    const linhas: number[] = [];
    const passo = Math.max(10, Math.round((yMax - yMin) / 4 / 10) * 10);
    for (let v = yMin; v <= yMax; v += passo) linhas.push(v);

    return { serie, px, py, linhas, pontos: serie.map((e, i): [number, number] => [px(i), py(e.indice)]) };
  }, [dados]);

  /* ── tendência: variação entre os dois últimos blocos avaliados ── */
  const tendencia = useMemo(() => {
    const s = dados?.evolucao ?? [];
    if (s.length < 2) return null;
    const delta = s[s.length - 1].indice - s[s.length - 2].indice;
    return { delta, subindo: delta >= 0, de: s[s.length - 2], para: s[s.length - 1] };
  }, [dados]);

  if (loading) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <div className={styles.headIco}><Activity size={20} /></div>
          <div>
            <h1 className={styles.h1}>Meu Desempenho</h1>
            <p className={styles.sub}>Apurando os indicadores…</p>
          </div>
        </header>
      </div>
    );
  }

  if (erro || !dados) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <div className={styles.headIco}><Activity size={20} /></div>
          <div>
            <h1 className={styles.h1}>Meu Desempenho</h1>
            <p className={styles.sub}>Não foi possível carregar o painel.</p>
          </div>
        </header>
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2 className={styles.cardTitle}><AlertTriangle size={15} /> Serviço indisponível</h2>
          </div>
          <p className={styles.chartNota}>{erro ?? "Resposta vazia do serviço de desempenho acadêmico."}</p>
        </section>
      </div>
    );
  }

  const blocoAtual = dados.blocoAtual ?? 0;
  const cursoPct = Math.round((blocoAtual / BLOCOS_CURSO) * 100);
  const angulo = tendencia ? Math.max(-90, Math.min(90, (tendencia.delta / 10) * 90)) : 0;

  const anel = (r: number, pct: number) => {
    const c = 2 * Math.PI * r;
    return { r, c, dash: `${(pct / 100) * c} ${c}` };
  };
  const aneis = [
    { ...anel(52, dados.indiceGeral),     cor: "url(#gComp)", label: "Competências",      valor: dados.indiceGeral },
    { ...anel(41, dados.presencaGeral),   cor: "url(#gPres)", label: "Presença",          valor: dados.presencaGeral },
    { ...anel(30, dados.entregasNoPrazo), cor: "url(#gEnt)",  label: "Entregas no prazo", valor: dados.entregasNoPrazo },
  ];

  /* ── séries de demonstração do gráfico do semestre ── */
  const { meses, mesesFuturos, aluno: ALUNO, turma: TURMA,
          projecao: PROJECAO, projecaoTurma: PROJECAO_TURMA,
          incerteza: INCERTEZA, turmasAnteriores: TURMAS_ANT } = DEMONSTRACAO;
  const serieSem = [...ALUNO, ...PROJECAO];
  const rotulos = [...meses, ...mesesFuturos];
  const yMinS = 60, yMaxS = 100;
  const pxS = (i: number) => ML + (i / (serieSem.length - 1)) * (W - ML - MR);
  const pyS = (v: number) => H - MB - ((v - yMinS) / (yMaxS - yMinS)) * (H - MT - MB);
  const iUlt = ALUNO.length - 1;
  const projFinal = PROJECAO[PROJECAO.length - 1];
  const acimaTurma = ALUNO[iUlt] - TURMA[iUlt];

  const alerta = !tendencia
    ? { tom: "ok" as const, icon: Award, titulo: "Primeiro bloco avaliado",
        dica: "Ainda não há dois blocos com conceitos lançados para comparar a evolução." }
    : !tendencia.subindo
      ? { tom: "warn" as const, icon: TrendingDown, titulo: "Rendimento em queda",
          dica: "Refaça os itens de rubrica das competências em D e marque uma monitoria." }
      : dados.d + dados.nd === 0
        ? { tom: "bom" as const, icon: Sparkles, titulo: "Todas as competências com louvor",
            dica: "Para manter DML, cubra todos os itens de rubrica do AT e entregue os TPs no prazo normal." }
        : { tom: "ok" as const, icon: Award, titulo: "Rendimento em alta",
            dica: `Escolha as ${dados.d + dados.nd} competência(s) em D ou ND e refaça os itens de rubrica que faltaram.` };
  const AlertaIcon = alerta.icon;

  const marcas = [
    { k: "no bloco", v: tendencia ? `${tendencia.delta >= 0 ? "+" : ""}${tendencia.delta}` : "—" },
    { k: "em D", v: `${dados.d}` },
    { k: "em ND", v: `${dados.nd}` },
  ];

  const faltamHoras = dados.cargaHoraria.reduce((s, h) => s + Math.max(0, h.exigida - h.concluida), 0);

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div className={styles.headIco}><Activity size={20} /></div>
        <div>
          <h1 className={styles.h1}>Meu Desempenho</h1>
          <p className={styles.sub}>
            Painel de acompanhamento por competências — {dados.aluno.nome ?? currentUser.nome}
          </p>
        </div>
      </header>

      {dados.aluno.indisponivel && (
        <p className={styles.chartNota}>
          <Info size={12} /> Os dados de identificação deste aluno ainda não foram sincronizados.
          Os indicadores abaixo vêm do serviço de desempenho e estão completos.
        </p>
      )}

      {/* ── Painel de topo ── */}
      <div className={styles.painel}>
        <section className={`${styles.cardPainel} ${styles.cardScore}`}>
          <Award className={styles.marca} size={130} aria-hidden />
          <h2 className={styles.painelTitulo}><Award size={15} /> Score geral no curso</h2>

          <div className={styles.scoreBody}>
            <div className={styles.scoreLado}>
              <div className={styles.ladoItem} title="Índice calculado a partir dos conceitos das competências">
                <b>{dados.indiceGeral}</b>
                <span><i className={styles.pontoComp} />índice</span>
              </div>
              <div className={styles.ladoItem} title="Presença média ponderada pela carga horária">
                <b>{dados.presencaGeral}%</b>
                <span><i className={styles.pontoPres} />presença</span>
              </div>
            </div>

            <div className={styles.aneis}>
              <svg viewBox="0 0 140 140" className={styles.aneisSvg}>
                <defs>
                  <linearGradient id="gComp" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stopColor="#a855f7" /><stop offset="1" stopColor="#22d3ee" />
                  </linearGradient>
                  <linearGradient id="gPres" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stopColor="#34d399" /><stop offset="1" stopColor="#a3e635" />
                  </linearGradient>
                  <linearGradient id="gEnt" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stopColor="#3b8ef5" /><stop offset="1" stopColor="#22d3ee" />
                  </linearGradient>
                </defs>
                <g transform="rotate(-90 70 70)">
                  {aneis.map(a => (
                    <g key={a.r}>
                      <circle cx="70" cy="70" r={a.r} className={styles.anelTrilho} />
                      <circle cx="70" cy="70" r={a.r} stroke={a.cor} strokeDasharray={a.dash}
                        className={styles.anelArco}>
                        <title>{`${a.label}: ${a.valor}%`}</title>
                      </circle>
                    </g>
                  ))}
                </g>
                {/* disco central: o conceito ganha superfície própria em vez de
                    flutuar sobre o vão dos anéis */}
                <circle cx="70" cy="70" r="23" className={styles.aneisDisco} />
                {/* O conceito equivalente vem do serviço: é ele quem sabe em que
                    degrau da escala um índice cai. */}
                <text x="70" y="78" className={styles.aneisNum}>{dados.conceitoEquivalente}</text>
              </svg>
              <span className={styles.aneisSub}>conceito médio</span>
            </div>

            <div className={styles.scoreLado}>
              <div className={styles.ladoItem} title="TPs entregues dentro do prazo normal">
                <b>{dados.entregasNoPrazo}%</b>
                <span><i className={styles.pontoEnt} />entregas</span>
              </div>
              <div className={styles.ladoItem} title="Competências avaliadas até aqui">
                <b>{dados.competenciasAvaliadas}</b>
                <span>competências</span>
              </div>
            </div>
          </div>

          <div className={styles.scorePerfil}>
            {([["DML", dados.dml], ["DL", dados.dl], ["D", dados.d], ["ND", dados.nd]] as [Conceito, number][])
              .filter(([, n]) => n > 0)
              .map(([k, n]) => (
                <span key={k} className={styles[`p${k}`]} style={{ flexGrow: n }} title={`${n} competência(s) ${k}`}>
                  {k} {n}
                </span>
              ))}
          </div>
        </section>

        {/* Conclusão do curso */}
        <section className={styles.cardPainel}>
          <Boxes className={styles.marca} size={120} aria-hidden />
          <h2 className={styles.painelTitulo}><Boxes size={15} /> Conclusão do curso</h2>
          <div className={styles.cursoTopo}>
            <div className={styles.cursoVal}>{cursoPct}<span>%</span></div>
            <span className={styles.cursoBloco}>
              <Boxes size={13} /> bloco {blocoAtual} de {BLOCOS_CURSO}
            </span>
          </div>

          {/* trilha dos blocos: o marcador aponta onde você está agora */}
          <div className={styles.trilhaBlocos}>
            <span className={styles.marcadorAqui} style={{ left: `${((blocoAtual - 0.5) / BLOCOS_CURSO) * 100}%` }}>
              aqui
            </span>
            <div className={styles.blocos} title={`Bloco ${blocoAtual} de ${BLOCOS_CURSO}`}>
              {Array.from({ length: BLOCOS_CURSO }, (_, i) => (
                <span key={i} className={
                  i < blocoAtual - 1 ? styles.blocoFeito :
                  i === blocoAtual - 1 ? styles.blocoAtual : styles.blocoVazio
                } title={`Bloco ${i + 1}${i < blocoAtual - 1 ? " · concluído" : i === blocoAtual - 1 ? " · em curso" : " · a cursar"}`} />
              ))}
            </div>
          </div>

          <div className={styles.marcas}>
            <span className={styles.marca2}><CheckCircle2 size={11} /><b>{Math.max(0, blocoAtual - 1)}</b> concluídos</span>
            <span className={styles.marca2}><Boxes size={11} /><b>{Math.max(0, BLOCOS_CURSO - blocoAtual)}</b> restantes</span>
            <span className={styles.marca2} title="Estimativa — depende do serviço projetar a conclusão">
              <Flag size={11} /><b>{DEMONSTRACAO.previsaoFormatura}</b> previsão
            </span>
          </div>
        </section>

        {/* Tendência com ponteiro */}
        <section className={styles.cardPainel}>
          <Gauge className={styles.marca} size={120} aria-hidden />
          <h2 className={styles.painelTitulo}><Gauge size={15} /> Tendência do rendimento</h2>
          <div className={styles.gauge}>
            <svg viewBox="0 0 140 92" className={styles.gaugeSvg}>
              <defs>
                <linearGradient id="gArco" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stopColor="#f87171" />
                  <stop offset=".42" stopColor="var(--surface-3)" />
                  <stop offset=".58" stopColor="var(--surface-3)" />
                  <stop offset="1" stopColor="#34d399" />
                </linearGradient>
              </defs>
              <path d="M14 70 A 56 56 0 0 1 126 70" className={styles.gaugeArco} />
              <path d="M14 70 A 56 56 0 0 1 126 70" stroke="url(#gArco)" className={styles.gaugeArcoCor} />

              {/* marcações a cada 22,5° — dão leitura de instrumento ao arco */}
              {Array.from({ length: 9 }, (_, i) => {
                const a = (-90 + i * 22.5) * (Math.PI / 180);
                const r1 = i % 4 === 0 ? 44 : 47, r2 = 51;
                return (
                  <line key={i}
                    x1={70 + Math.sin(a) * r1} y1={70 - Math.cos(a) * r1}
                    x2={70 + Math.sin(a) * r2} y2={70 - Math.cos(a) * r2}
                    className={i % 4 === 0 ? styles.tickForte : styles.tick} />
                );
              })}

              <g className={styles.ponteiro} style={{ transform: `rotate(${angulo}deg)` }}>
                <path d="M70 70 L70 26" className={styles.agulha} />
                <path d="M70 70 L70 79" className={styles.contrapeso} />
              </g>
              <circle cx="70" cy="70" r="6.5" className={styles.eixo} />
              <circle cx="70" cy="70" r="2.4" className={styles.eixoMiolo} />

              <text x="18" y="88" className={styles.gaugeLbl} textAnchor="start">caindo</text>
              <text x="122" y="88" className={styles.gaugeLbl} textAnchor="end">subindo</text>
            </svg>
          </div>

          <div className={styles.gaugeRodape}>
            <div className={`${styles.gaugeVal} ${(tendencia?.subindo ?? true) ? styles.up : styles.down}`}>
              {tendencia ? (tendencia.subindo ? <TrendingUp size={15} /> : <TrendingDown size={15} />) : <Gauge size={15} />}
              {tendencia ? `${tendencia.subindo ? "+" : "−"}${Math.abs(tendencia.delta)}` : "—"}
              <i>pts</i>
            </div>
            {tendencia && (
              <span className={styles.gaugeTag}>
                bloco {tendencia.de.numeroBloco} → {tendencia.para.numeroBloco}
              </span>
            )}
          </div>
          <p className={styles.cursoHint}>
            {tendencia
              ? `variação do índice entre os blocos ${tendencia.de.periodo} e ${tendencia.para.periodo}`
              : "é necessário mais de um bloco avaliado"}
          </p>
        </section>
      </div>

      {/* ── Alerta ── */}
      <div className={`${styles.alerta} ${alerta.tom === "bom" ? styles.alertaBom : alerta.tom === "ok" ? styles.alertaOk : styles.alertaWarn}`}>
        <span className={styles.alertaIco}><AlertaIcon size={18} /></span>
        <div className={styles.alertaCorpo}>
          <div className={styles.alertaTopo}>
            <strong className={styles.alertaTitulo}>{alerta.titulo}</strong>
            <div className={styles.marcas}>
              {marcas.map(m => (
                <span key={m.k} className={styles.marca2}>
                  <b>{m.v}</b> {m.k}
                </span>
              ))}
            </div>
          </div>
          <p className={styles.alertaTexto}>{alerta.dica}</p>
        </div>
      </div>

      {/* ── Evolução por bloco (DADOS REAIS do serviço) ──
          O eixo é o bloco, e não o mês: conceito é lançado ao fim do trimestre,
          e uma série mensal desenharia degraus que não correspondem a nenhuma
          avaliação real. */}
      <section className={styles.card}>
        <div className={styles.cardHead}>
          <h2 className={styles.cardTitle}><LineChart size={15} /> Evolução do índice por bloco</h2>
          <div className={styles.legendaLin}>
            <span><i className={styles.dotAluno} /> Índice de conceitos</span>
          </div>
        </div>

        {grafico ? (
          <div className={styles.chartWrap}>
            <svg viewBox={`0 0 ${W} ${H}`} className={styles.chart} onMouseLeave={() => setPontoBloco(null)}>
              <defs>
                <linearGradient id="fillBloco" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stopColor="rgba(59,142,245,.34)" />
                  <stop offset=".85" stopColor="rgba(59,142,245,.02)" />
                  <stop offset="1" stopColor="rgba(59,142,245,0)" />
                </linearGradient>
              </defs>

              {grafico.linhas.map(v => (
                <g key={v}>
                  <line x1={ML} y1={grafico.py(v)} x2={W - MR} y2={grafico.py(v)} className={styles.grid} />
                  <text x={ML - 8} y={grafico.py(v) + 3.5} className={styles.eixo} textAnchor="end">{v}</text>
                </g>
              ))}

              {grafico.pontos.length > 1 && (
                <>
                  <path fill="url(#fillBloco)" d={area(grafico.pontos, H - MB)} />
                  <path className={styles.lineAluno} d={suave(grafico.pontos)} />
                </>
              )}

              {grafico.serie.map((e, i) => (
                <circle key={e.numeroBloco} cx={grafico.px(i)} cy={grafico.py(e.indice)} r="3.6"
                  className={styles.ptAluno} />
              ))}

              {pontoBloco != null && (
                <line x1={grafico.px(pontoBloco)} y1={MT} x2={grafico.px(pontoBloco)} y2={H - MB}
                  className={styles.cursorLinha} />
              )}

              {grafico.serie.map((e, i) => (
                <rect key={`hit-${e.numeroBloco}`}
                  x={grafico.px(i) - (W - ML - MR) / Math.max(1, grafico.serie.length) / 2}
                  y={0}
                  width={(W - ML - MR) / Math.max(1, grafico.serie.length)}
                  height={H} fill="transparent" onMouseEnter={() => setPontoBloco(i)} />
              ))}

              {grafico.serie.map((e, i) => (
                <text key={`lbl-${e.numeroBloco}`} x={grafico.px(i)} y={H - 10} textAnchor="middle"
                  className={styles.eixo}>Bloco {e.numeroBloco}</text>
              ))}
            </svg>

            {pontoBloco != null && (
              <div className={styles.tip} style={{ left: `${(grafico.px(pontoBloco) / W) * 100}%` }}>
                <div className={styles.tipTitulo}>
                  Bloco {grafico.serie[pontoBloco].numeroBloco} · {grafico.serie[pontoBloco].periodo}
                </div>
                <div className={styles.tipLinha}>
                  <i className={styles.dotAluno} /> Índice
                  <b>{grafico.serie[pontoBloco].indice}</b>
                  <em>{grafico.serie[pontoBloco].conceitoEquivalente}</em>
                </div>
                <div className={styles.tipNota}>
                  {grafico.serie[pontoBloco].competenciasAvaliadas} competência(s) avaliada(s)
                </div>
              </div>
            )}
          </div>
        ) : (
          <p className={styles.chartNota}>Nenhum conceito lançado ainda — não há evolução a exibir.</p>
        )}

        <p className={styles.chartNota}>
          Cada ponto é a média dos conceitos lançados no bloco, apurada pelo serviço. Blocos sem
          avaliação não aparecem: um ponto em zero seria lido como queda, quando significa apenas
          que ainda não houve conceito.
        </p>
      </section>

      {/* ── Evolução no semestre + projeção (interface pronta, dados previstos) ── */}
      <section className={styles.card}>
        <div className={styles.cardHead}>
          <h2 className={styles.cardTitle}><LineChart size={15} /> Evolução no semestre e projeção</h2>
          <div className={styles.legendaLin}>
            <span><i className={styles.dotAluno} /> Você</span>
            <span><i className={styles.dotProj} /> Projeção sua</span>
            <span><i className={styles.dotTurma} /> Média da turma</span>
            <span><i className={styles.dotProjTurma} /> Projeção da turma</span>
          </div>
        </div>

        <div className={styles.chartWrap}>
          <svg viewBox={`0 0 ${W} ${H}`} className={styles.chart}
            onMouseLeave={() => setPonto(null)}>
            <defs>
              {/* cada linha tem o seu degradê descendo até a base, na própria cor */}
              <linearGradient id="fillAluno" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="rgba(59,142,245,.34)" />
                <stop offset=".85" stopColor="rgba(59,142,245,.02)" />
                <stop offset="1" stopColor="rgba(59,142,245,0)" />
              </linearGradient>
              <linearGradient id="fillProj" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="rgba(34,211,238,.3)" />
                <stop offset=".85" stopColor="rgba(34,211,238,.02)" />
                <stop offset="1" stopColor="rgba(34,211,238,0)" />
              </linearGradient>
              <linearGradient id="fillTurma" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="rgba(148,163,184,.2)" />
                <stop offset=".85" stopColor="rgba(148,163,184,.02)" />
                <stop offset="1" stopColor="rgba(148,163,184,0)" />
              </linearGradient>
              <linearGradient id="fillProjTurma" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="rgba(167,139,250,.24)" />
                <stop offset=".85" stopColor="rgba(167,139,250,.02)" />
                <stop offset="1" stopColor="rgba(167,139,250,0)" />
              </linearGradient>
            </defs>

            {[60, 70, 80, 90, 100].map(v => (
              <g key={v}>
                <line x1={ML} y1={pyS(v)} x2={W - MR} y2={pyS(v)} className={styles.grid} />
                <text x={ML - 8} y={pyS(v) + 3.5} className={styles.eixo} textAnchor="end">{v}</text>
              </g>
            ))}

            {/* áreas: turma, você e projeção — cada uma descendo até a base, e a
                da projeção emendando na sua, sem corte no meio do gráfico */}
            <path fill="url(#fillTurma)" d={area(TURMA.map((v, i): [number, number] => [pxS(i), pyS(v)]), H - MB)} />
            <path fill="url(#fillProjTurma)" d={area(
              [[pxS(iUlt), pyS(TURMA[iUlt])], ...PROJECAO_TURMA.map((v, i): [number, number] => [pxS(iUlt + 1 + i), pyS(v)])],
              H - MB)} />
            <path fill="url(#fillProj)" d={area(
              [[pxS(iUlt), pyS(ALUNO[iUlt])], ...PROJECAO.map((v, i): [number, number] => [pxS(iUlt + 1 + i), pyS(v)])],
              H - MB)} />
            <path fill="url(#fillAluno)" d={area(ALUNO.map((v, i): [number, number] => [pxS(i), pyS(v)]), H - MB)} />
            {/* fronteira entre o que já aconteceu e o que é projeção */}
            <line x1={pxS(iUlt)} y1={MT - 4} x2={pxS(iUlt)} y2={H - MB} className={styles.corte} />
            <text x={pxS(iUlt) + 5} y={MT + 4} className={styles.corteLbl}>projeção →</text>

            <path className={styles.lineTurma} d={suave(TURMA.map((v, i): [number, number] => [pxS(i), pyS(v)]))} />
            <path className={styles.lineProjTurma} d={suave(
              [[pxS(iUlt), pyS(TURMA[iUlt])], ...PROJECAO_TURMA.map((v, i): [number, number] => [pxS(iUlt + 1 + i), pyS(v)])])} />
            {PROJECAO_TURMA.map((v, i) => (
              <circle key={`pt${i}`} cx={pxS(iUlt + 1 + i)} cy={pyS(v)} r="3" className={styles.ptProjTurma} />
            ))}
            <path className={styles.lineAluno} d={suave(ALUNO.map((v, i): [number, number] => [pxS(i), pyS(v)]))} />
            <path className={styles.lineProj} d={suave(
              [[pxS(iUlt), pyS(ALUNO[iUlt])], ...PROJECAO.map((v, i): [number, number] => [pxS(iUlt + 1 + i), pyS(v)])])} />

            {serieSem.map((v, i) => (
              <circle key={i} cx={pxS(i)} cy={pyS(v)} r={i > iUlt ? 4.2 : 3.6}
                className={i > iUlt ? styles.ptProj : styles.ptAluno} />
            ))}
            {ponto != null && <line x1={pxS(ponto)} y1={MT} x2={pxS(ponto)} y2={H - MB} className={styles.cursorLinha} />}

            {/* faixas invisíveis de captura para o tooltip */}
            {serieSem.map((_, i) => (
              <rect key={i} x={pxS(i) - (W - ML - MR) / (serieSem.length - 1) / 2} y={0}
                width={(W - ML - MR) / (serieSem.length - 1)} height={H}
                fill="transparent" onMouseEnter={() => setPonto(i)} />
            ))}

            {rotulos.map((m, i) => (
              <text key={m} x={pxS(i)} y={H - 10} textAnchor="middle"
                className={i > iUlt ? styles.eixoProj : styles.eixo}>{m}</text>
            ))}
          </svg>

          {ponto != null && (
            <div className={styles.tip} style={{ left: `${(pxS(ponto) / W) * 100}%` }}>
              <div className={styles.tipTitulo}>{rotulos[ponto]}{ponto > iUlt ? " · projetado" : ""}</div>
              <div className={styles.tipLinha}>
                <i className={styles.dotAluno} /> Você
                <b>{serieSem[ponto]}</b><em>{CONCEITO_DE(serieSem[ponto])}</em>
              </div>
              <div className={styles.tipLinha}>
                <i className={ponto > iUlt ? styles.dotProjTurma : styles.dotTurma} /> Turma{ponto > iUlt ? " (proj.)" : ""}
                <b>{ponto <= iUlt ? TURMA[ponto] : PROJECAO_TURMA[ponto - iUlt - 1]}</b>
                <em>{CONCEITO_DE(ponto <= iUlt ? TURMA[ponto] : PROJECAO_TURMA[ponto - iUlt - 1])}</em>
              </div>
              {ponto > iUlt && <div className={styles.tipNota}>projeção com margem de ± {INCERTEZA} pts</div>}
            </div>
          )}
        </div>

        <p className={styles.chartNota}>
          Projeção para o fim do semestre: <strong>{projFinal}</strong> — conceito médio{" "}
          <strong>{CONCEITO_DE(projFinal)}</strong>, mantido o ritmo atual. Você está{" "}
          <strong>{acimaTurma >= 0 ? `${acimaTurma} pontos acima` : `${Math.abs(acimaTurma)} pontos abaixo`}</strong>{" "}
          da média da turma.
        </p>
        <p className={styles.chartNota}>
          <Info size={12} /> Série mensal, média da turma e projeção ainda não vêm do serviço:
          alimentá-las exige que o boletim-service modele turma, coorte e um modelo de tendência
          com margem declarada. Os valores exibidos aqui são de demonstração.
        </p>
      </section>

      {/* ── Comparação com turmas + presenças ── */}
      <div className={styles.grid2}>
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2 className={styles.cardTitle}><Users size={15} /> Comparação com turmas anteriores</h2>
            <SeloDemonstracao>dados de demonstração</SeloDemonstracao>
          </div>
          <div className={styles.comboWrap} onMouseLeave={() => setBarra(null)}>
            <svg viewBox="0 0 340 190" className={styles.combo}>
              {/* as turmas anteriores são referência: ficam neutras. Só a sua
                  barra recebe o acento — é ela que se quer ler primeiro. */}
              <defs>
                <linearGradient id="gbEu" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0" stopColor="var(--primary)" />
                  <stop offset="1" stopColor="#1f6fe0" />
                </linearGradient>
              </defs>
              {[60, 70, 80, 90].map(v => {
                const y = 160 - ((v - 55) / 40) * 130;
                return <line key={v} x1="26" y1={y} x2="330" y2={y} className={styles.grid} />;
              })}
              {TURMAS_ANT.map((t, i) => {
                const x = 44 + i * 76, y = 160 - ((t.indice - 55) / 40) * 130;
                return (
                  <g key={t.turma} onMouseEnter={() => setBarra(i)} className={styles.barGrupo}>
                    <rect x={x - 24} y={y} width="48" height={160 - y} rx="7"
                      fill={t.eu ? "url(#gbEu)" : "var(--surface-3)"}
                      className={`${styles.barra} ${barra === i ? styles.barraOn : ""}`} />
                    <text x={x} y={y - 8} textAnchor="middle" className={styles.barNum}>{t.indice}</text>
                    <text x={x} y="177" textAnchor="middle"
                      className={i === TURMAS_ANT.length - 1 ? styles.barLblEu : styles.barLbl}>
                      {i === TURMAS_ANT.length - 1 ? `Você · ${t.turma}` : t.turma}
                    </text>
                    <rect x={x - 38} y="0" width="76" height="190" fill="transparent" />
                  </g>
                );
              })}
              <path className={styles.comboLinha}
                d={suave(TURMAS_ANT.map((t, i): [number, number] => [44 + i * 76, 160 - ((t.indice - 55) / 40) * 130]))} />
              {TURMAS_ANT.map((t, i) => (
                <circle key={t.turma} cx={44 + i * 76} cy={160 - ((t.indice - 55) / 40) * 130} r="4" className={styles.comboPt} />
              ))}
            </svg>
            {barra != null && (
              <div className={styles.tipBar} style={{ left: `${((44 + barra * 76) / 340) * 100}%` }}>
                <div className={styles.tipTitulo}>{TURMAS_ANT[barra].turma}</div>
                <div className={styles.tipLinha}>
                  índice <b>{TURMAS_ANT[barra].indice}</b><em>{CONCEITO_DE(TURMAS_ANT[barra].indice)}</em>
                </div>
                <div className={styles.tipNota}>
                  {barra === TURMAS_ANT.length - 1
                    ? "sua turma"
                    : `${TURMAS_ANT[TURMAS_ANT.length - 1].indice - TURMAS_ANT[barra].indice > 0 ? "+" : ""}${TURMAS_ANT[TURMAS_ANT.length - 1].indice - TURMAS_ANT[barra].indice} pts vs. sua turma`}
                </div>
              </div>
            )}
          </div>
          <p className={styles.chartNota}>
            Índice médio de conceitos por turma no mesmo ponto do curso. Depende de o serviço
            passar a modelar turmas — hoje ele conhece o percurso de um aluno por vez.
          </p>
        </section>

        {/* Presenças — DADOS REAIS do serviço */}
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2 className={styles.cardTitle}><CalendarCheck size={15} /> Presenças por disciplina</h2>
            <span className={styles.cardHint}>bloco em curso</span>
          </div>
          {dados.presencas.length === 0 ? (
            <p className={styles.chartNota}>Nenhuma disciplina em curso no momento.</p>
          ) : (
            <ul className={styles.presencas}>
              {dados.presencas.map(p => (
                <li key={p.disciplina} className={styles.presItem}
                  title={p.isentaFrequencia
                    ? `${p.percentual}% · disciplina que não reprova por frequência`
                    : `${p.percentual}% de presença · mínimo ${dados.presencaMinimaExigida}%`}>
                  <span className={styles.presDisc}>
                    {p.disciplina}{p.isentaFrequencia && " (isenta)"}
                  </span>
                  <div className={styles.presTrilho}>
                    <span className={`${styles.presBarra} ${p.abaixoDoMinimo ? styles.presBaixa : ""}`}
                      style={{ width: `${p.percentual}%` }} />
                  </div>
                  <span className={`${styles.presPct} ${p.abaixoDoMinimo ? styles.presPctBaixa : ""}`}>
                    {p.percentual}%
                  </span>
                </li>
              ))}
            </ul>
          )}
          <p className={styles.presNota}>
            Frequência mínima para aprovação: <strong>{dados.presencaMinimaExigida}%</strong>.
          </p>
        </section>
      </div>

      {/* ── Horas obrigatórias restantes — DADOS REAIS do serviço ── */}
      <section className={styles.card}>
        <div className={styles.cardHead}>
          <h2 className={styles.cardTitle}><Clock size={15} /> Horas obrigatórias restantes</h2>
          <span className={styles.cardHint}>faltam {faltamHoras}h no total</span>
        </div>
        <div className={styles.horas}>
          {dados.cargaHoraria.map(h => {
            const rest = Math.max(0, h.exigida - h.concluida);
            const cor = COR_CATEGORIA[h.categoria];
            const HIco = ICONE_CATEGORIA[h.categoria];
            return (
              <div key={h.categoria} className={styles.horaItem}
                title={`${h.concluida}h de ${h.exigida}h concluídas`}>
                <div className={styles.horaTop}>
                  <span className={styles.horaLabel}><HIco size={13} style={{ color: cor }} /> {h.descricao}</span>
                  <span className={styles.horaRest}><strong>{rest}h</strong> restantes</span>
                </div>
                <div className={styles.horaTrilho}>
                  <span className={styles.horaBarra} style={{
                    width: `${h.percentual}%`,
                    background: `linear-gradient(90deg, ${cor}, ${cor}cc)`,
                    boxShadow: `0 0 8px ${cor}66`,
                  }} />
                </div>
                <div className={styles.horaHint}>{h.concluida}h de {h.exigida}h ({h.percentual}%)</div>
              </div>
            );
          })}
        </div>
      </section>

      <p className={styles.rodape}>
        <Target size={12} /> O índice (0–100) é um indicador da plataforma calculado a partir dos conceitos —
        DML 100 · DL 80 · D 60 · ND 0. A avaliação oficial é sempre o conceito, nunca o índice.
      </p>
    </div>
  );
}
