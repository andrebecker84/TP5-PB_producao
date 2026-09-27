"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  GraduationCap, Award, CheckCircle2, Clock, Printer, ChevronRight,
  HandHeart, BookMarked, Layers, CircleDashed, CircleDot, Briefcase,
  Sparkles, Info, CalendarCheck, FileCheck2, XCircle, Route, AlertTriangle,
} from "lucide-react";
import { useUsuarioLogado } from "@/hooks/useCurrentUser";
import { boletimService } from "@/services/boletimService";
import {
  Atividade, BlocoBoletim, Boletim, CargaCategoria, CategoriaAtividade,
  Conceito, ConceitoOuPendente, DisciplinaBoletim, EscalaConceito, StatusProgresso,
} from "@/types/boletim";
import styles from "./page.module.css";

/* ──────────────────────────────────────────────────────────────────────────
   Boletim no modelo da Infnet: não há notas — o aluno demonstra COMPETÊNCIAS,
   avaliadas em D / DL / DML (ou ND, quando não demonstrada).

   Os dados vêm do boletim-service, um microsserviço com banco próprio, através
   do API Gateway. Até o TP2 esta página trazia tudo codificado no arquivo,
   inclusive as REGRAS de aprovação — o cálculo de situação, o pior conceito da
   disciplina, o teto imposto por TP atrasado. Essas regras agora vivem no
   serviço: a tela apenas exibe o que ele apurou.

   A diferença não é de organização. Enquanto a regra morava aqui, ela era
   conhecida apenas por esta tela, e qualquer outro consumidor da API — um
   relatório, a secretaria, um aplicativo — precisaria reimplementá-la, com
   divergência garantida na primeira mudança de critério.
   ────────────────────────────────────────────────────────────────────────── */

/* Trilhas de especialização — os blocos seguintes, ainda não iniciados. Segue
   estático: é estrutura de curso que o aluno ainda não percorreu, e o serviço
   só conhece o percurso. Modelar a matriz completa fica para quando houver
   matrícula em trilha. */
const TRILHAS = [
  { nome: "Inteligência Artificial", disc: ["Machine Learning", "Desenvolvimento Disciplinado e Gestão de Multi-Agentes IA"] },
  { nome: "Sistemas Complexos",      disc: ["Engenharia Disciplinada de Softwares", "Desenvolvimento Disciplinado e Gestão de Multi-Agentes IA"] },
  { nome: "Engenharia de Dados",     disc: ["Engenharia de Banco de Dados", "Engenharia de Dados: Big Data"] },
  { nome: "Cibersegurança",          disc: ["Segurança Defensiva com SOC e Blue Team", "Segurança Ofensiva com Red Team"] },
  { nome: "Cloud Computing",         disc: ["Cloud Computing e Conteinerização", "Arquitetura e Engenharia de Softwares na Nuvem"] },
];

const ORDEM_CATEGORIAS: CategoriaAtividade[] = ["EXTENSAO", "ELETIVA", "ESTAGIO", "COMPLEMENTAR"];

const ICONE_CATEGORIA: Record<CategoriaAtividade, typeof HandHeart> = {
  EXTENSAO: HandHeart,
  ELETIVA: BookMarked,
  ESTAGIO: Briefcase,
  COMPLEMENTAR: Sparkles,
};

const COR_CATEGORIA: Record<CategoriaAtividade, string> = {
  EXTENSAO: "#a78bfa",
  ELETIVA: "#f472b6",
  ESTAGIO: "#22d3ee",
  COMPLEMENTAR: "#fbbf24",
};

function ConceitoTag({ v, mini, escala }: { v: ConceitoOuPendente; mini?: boolean; escala: EscalaConceito[] }) {
  if (v == null) {
    return (
      <span className={`${styles.cnc} ${styles.cncAval} ${mini ? styles.cncMini : ""}`}
        title="Competência ainda em avaliação">—</span>
    );
  }
  const info = escala.find(e => e.codigo === v);
  const cls = v === "DML" ? styles.cncDML : v === "DL" ? styles.cncDL : v === "D" ? styles.cncD : styles.cncND;
  return (
    <span className={`${styles.cnc} ${cls} ${mini ? styles.cncMini : ""}`}
      title={info ? `${v} — ${info.nome}: ${info.regra}` : v}>{v}</span>
  );
}

function StatusTag({ s, texto }: { s: StatusProgresso; texto: string }) {
  const cls = s === "CONCLUIDO" ? styles.stOk : s === "EM_CURSO" ? styles.stCurso : styles.stOff;
  const Ico = s === "CONCLUIDO" ? CheckCircle2 : s === "EM_CURSO" ? CircleDot : CircleDashed;
  return <span className={`${styles.stBadge} ${cls}`}><Ico size={11} /> {texto}</span>;
}

function TabelaAtividades({ itens, comPresenca, presencaMinima }: {
  itens: Atividade[]; comPresenca?: boolean; presencaMinima: number;
}) {
  if (itens.length === 0) {
    return <p className={styles.trilhaIntro}>Nenhuma atividade registrada nesta categoria.</p>;
  }
  return (
    <div className={styles.tabelaWrap}>
      <table className={styles.tabela}>
        <thead>
          <tr>
            <th>Atividade</th>
            <th className={styles.colNum}>Carga</th>
            <th className={styles.colNum}>Trimestre</th>
            {comPresenca && <th className={styles.colNum}>Presença</th>}
            <th className={styles.colSit}>Status</th>
          </tr>
        </thead>
        <tbody>
          {itens.map(i => (
            <tr key={i.id}>
              <td className={styles.disc}>{i.nome}</td>
              <td className={styles.colNum}>{i.cargaHoraria}h</td>
              <td className={styles.colNum}>{i.periodo ?? "—"}</td>
              {comPresenca && (
                <td className={`${styles.colNum} ${i.presencaPercentual != null && i.presencaPercentual < presencaMinima ? styles.freqBaixa : ""}`}>
                  {i.presencaPercentual != null ? `${i.presencaPercentual}%` : "—"}
                </td>
              )}
              <td className={styles.colSit}><StatusTag s={i.status} texto={i.statusDescricao} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function BoletimPage() {
  const currentUser = useUsuarioLogado();

  const [boletim, setBoletim] = useState<Boletim | null>(null);
  const [atividades, setAtividades] = useState<Atividade[]>([]);
  const [carga, setCarga] = useState<CargaCategoria[]>([]);
  const [escala, setEscala] = useState<EscalaConceito[]>([]);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [abertos, setAbertos] = useState<Set<number>>(new Set());

  const carregar = useCallback(() => {
    // Sem setLoading(true) aqui: o estado já nasce carregando, e chamar
    // setState no corpo de um efeito dispara uma renderização em cascata. Quem
    // precisa reativá-lo é o botão de nova tentativa, abaixo.
    //
    // Em paralelo: são quatro recursos independentes, e encadeá-los somaria as
    // latências sem que nenhum dependa do anterior.
    Promise.all([
      boletimService.buscarBoletim(currentUser.id),
      boletimService.listarAtividades(currentUser.id),
      boletimService.cargaHoraria(currentUser.id),
      boletimService.listarEscalaConceitos(),
    ])
      .then(([b, a, c, e]) => {
        setBoletim(b);
        setAtividades(a);
        setCarga(c);
        setEscala(e);
        // Abre o bloco mais recente, que é o que o aluno quer ver primeiro.
        const ultimo = b.blocos.at(-1);
        if (ultimo) setAbertos(new Set([ultimo.numero]));
      })
      .catch(e => setErro(e instanceof Error ? e.message : "Falha ao carregar o boletim."))
      .finally(() => setLoading(false));
  }, [currentUser.id]);

  useEffect(carregar, [carregar]);

  /** Refaz a busca a pedido do usuário — aqui o estado de carregamento volta. */
  const tentarNovamente = () => {
    setLoading(true);
    setErro(null);
    carregar();
  };

  const alternar = (b: number) =>
    setAbertos(prev => {
      const s = new Set(prev);
      if (s.has(b)) s.delete(b); else s.add(b);
      return s;
    });

  const imprimir = () => {
    if (boletim) setAbertos(new Set(boletim.blocos.map(b => b.numero)));
    setTimeout(() => window.print(), 80);
  };

  const porCategoria = useMemo(() => {
    const mapa = new Map<CategoriaAtividade, Atividade[]>();
    for (const a of atividades) {
      const lista = mapa.get(a.categoria) ?? [];
      lista.push(a);
      mapa.set(a.categoria, lista);
    }
    return mapa;
  }, [atividades]);

  if (loading) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <div className={styles.headIco}><GraduationCap size={20} /></div>
          <div className={styles.headInfo}>
            <h1 className={styles.h1}>Boletim Acadêmico</h1>
            <p className={styles.sub}>Carregando o histórico por competências…</p>
          </div>
        </header>
      </div>
    );
  }

  if (erro || !boletim) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <div className={styles.headIco}><GraduationCap size={20} /></div>
          <div className={styles.headInfo}>
            <h1 className={styles.h1}>Boletim Acadêmico</h1>
            <p className={styles.sub}>Não foi possível carregar o boletim.</p>
          </div>
        </header>
        <section className={styles.regras}>
          <h2 className={styles.regrasTitulo}><AlertTriangle size={15} /> Serviço indisponível</h2>
          <p className={styles.regrasNota}>{erro ?? "Resposta vazia do serviço de desempenho acadêmico."}</p>
          <button className={styles.printBtn} onClick={tentarNovamente}>Tentar novamente</button>
        </section>
      </div>
    );
  }

  const { resumo, blocos, aluno } = boletim;
  const presencaMinima = resumo.presencaMinimaExigida;

  /* Carga horária total: as disciplinas somam-se às categorias de atividade.
     Todas as metas vêm do serviço — nenhuma constante de curso mora aqui. */
  const totais = [
    {
      label: "Disciplinas dos blocos", feito: resumo.cargaHorariaAprovada,
      meta: resumo.cargaHorariaExigida, cor: "#3b8ef5", icon: Layers,
    },
    ...carga.map(c => ({
      label: c.descricao, feito: c.concluida, meta: c.exigida,
      cor: COR_CATEGORIA[c.categoria], icon: ICONE_CATEGORIA[c.categoria],
    })),
  ];
  const cargaFeita = totais.reduce((s, t) => s + t.feito, 0);
  const cargaMeta = totais.reduce((s, t) => s + t.meta, 0);
  const pctCurso = cargaMeta > 0 ? Math.round((cargaFeita / cargaMeta) * 100) : 0;

  const contagem: [Conceito, number][] =
    [["DML", resumo.dml], ["DL", resumo.dl], ["D", resumo.d], ["ND", resumo.nd]];

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div className={styles.headIco}><GraduationCap size={20} /></div>
        <div className={styles.headInfo}>
          <h1 className={styles.h1}>Boletim Acadêmico</h1>
          <p className={styles.sub}>
            Histórico por competências — {aluno.nome ?? currentUser.nome}
            {aluno.escola ? ` · ${aluno.escola}` : ""}
          </p>
        </div>
        <button className={styles.printBtn} onClick={imprimir} title="Imprimir">
          <Printer size={15} /> Imprimir
        </button>
      </header>

      {/* Degradação explícita: os conceitos vêm do banco deste serviço e estão
          íntegros; o que faltou foi a identificação, que vive na aplicação
          central. Dizer isso é melhor que exibir um cabeçalho vazio. */}
      {aluno.indisponivel && (
        <p className={styles.aviso}>
          <Info size={11} /> Os dados de identificação deste aluno ainda não foram sincronizados com o boletim.
          O histórico acadêmico abaixo está completo.
        </p>
      )}

      {/* ── Resumo ── */}
      <div className={styles.resumo}>
        <div className={styles.resItem}>
          <span className={styles.resIco} data-c="primary"><Award size={16} /></span>
          <div>
            <div className={styles.resVal}>{resumo.competenciasAvaliadas}</div>
            <div className={styles.resLbl}>competências demonstradas</div>
          </div>
        </div>
        <div className={styles.resItem}>
          <span className={styles.resIco} data-c="louvor"><Sparkles size={16} /></span>
          <div>
            <div className={styles.resVal}>{resumo.dml + resumo.dl}</div>
            <div className={styles.resLbl}>com louvor (DL + DML)</div>
          </div>
        </div>
        <div className={styles.resItem}>
          <span className={styles.resIco} data-c="success"><CheckCircle2 size={16} /></span>
          <div>
            <div className={styles.resVal}>{resumo.disciplinasAprovadas}</div>
            <div className={styles.resLbl}>disciplinas aprovadas</div>
          </div>
        </div>
        <div className={styles.resItem}>
          <span className={styles.resIco} data-c="warning"><CalendarCheck size={16} /></span>
          <div>
            <div className={styles.resVal}>{resumo.presencaMedia}%</div>
            <div className={styles.resLbl}>presença média (mín. {presencaMinima}%)</div>
          </div>
        </div>
      </div>

      {/* ── Perfil de conceitos ── */}
      <section className={styles.perfil}>
        <h2 className={styles.perfilTitulo}>Perfil de conceitos</h2>
        <div className={styles.perfilBarra}>
          {contagem.filter(([, n]) => n > 0).map(([k, n]) => {
            const info = escala.find(e => e.codigo === k);
            return (
              <span key={k} className={styles[`seg${k}`]} style={{ flexGrow: n }}
                title={`${n} competência${n > 1 ? "s" : ""} ${k}${info ? ` — ${info.nome}` : ""}`}>
                {n}
              </span>
            );
          })}
        </div>
        {/* A legenda vem do serviço, na ordem que ele define (pior → melhor);
            aqui é invertida para leitura decrescente. */}
        <div className={styles.perfilLegenda}>
          {escala.slice().reverse().map(e => (
            <span key={e.codigo} className={styles.legItem} title={e.regra}>
              <i className={styles[`seg${e.codigo}`]} /> <b>{e.codigo}</b> {e.nome}
            </span>
          ))}
        </div>
      </section>

      {/* ── Blocos ── */}
      <h2 className={styles.secTitulo}><Layers size={15} /> Blocos de Graduação</h2>

      {blocos.slice().reverse().map((b: BlocoBoletim) => {
        const aberto = abertos.has(b.numero);
        return (
          <section key={b.numero} className={`${styles.bloco} ${aberto ? styles.blocoAberto : ""}`}>
            <button className={styles.blocoHead} onClick={() => alternar(b.numero)} aria-expanded={aberto}>
              <ChevronRight size={15} className={styles.chevron} />
              <span className={styles.blocoTag}>Bloco {b.numero}</span>
              <span className={styles.blocoTitulo}>{b.titulo}</span>
              <span className={styles.blocoPeriodo}>{b.periodo}</span>
              <span className={styles.blocoCount}>{b.disciplinasAprovadas}/{b.totalDisciplinas}</span>
              <StatusTag s={b.status} texto={b.statusDescricao} />
            </button>

            {aberto && (
              <div className={styles.tabelaWrap}>
                <table className={styles.tabela}>
                  <thead>
                    <tr>
                      <th>Disciplina e competências</th>
                      <th className={styles.colNum}>Carga</th>
                      <th className={styles.colNum}>Presença</th>
                      <th className={styles.colNum}>TPs</th>
                      <th className={styles.colSit}>Situação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {b.disciplinas.map((d: DisciplinaBoletim) => {
                      const freqBaixa = !d.isentaFrequencia && d.presencaPercentual < presencaMinima;
                      return (
                        <tr key={d.matriculaId}>
                          <td>
                            <span className={styles.disc}>
                              {d.tipo === "PROJETO_BLOCO" && (
                                <span className={styles.pbTag} title={d.tipoDescricao}>PB</span>
                              )}
                              {d.nome}
                              {d.conceitoFinal && <ConceitoTag v={d.conceitoFinal} escala={escala} />}
                            </span>
                            <ul className={styles.comps}>
                              {d.competencias.map(c => (
                                <li key={c.competenciaId}>
                                  <ConceitoTag v={c.conceito} mini escala={escala} />
                                  <span>{c.nome}</span>
                                </li>
                              ))}
                            </ul>
                            {d.avisoTp && <p className={styles.aviso}><Info size={11} /> {d.avisoTp}</p>}
                          </td>
                          <td className={styles.colNum}>{d.cargaHoraria}h</td>
                          <td className={`${styles.colNum} ${freqBaixa ? styles.freqBaixa : ""}`}
                            title={d.isentaFrequencia ? "Disciplina que não reprova por frequência" : `Mínimo de ${presencaMinima}%`}>
                            {d.presencaPercentual}%{d.isentaFrequencia && <i className={styles.isenta}>isenta</i>}
                          </td>
                          <td className={styles.colNum}
                            title={`${d.tps.total} TPs · ${d.tps.atraso} fora do prazo · ${d.tps.pendentes} pendentes`}>
                            <span className={d.tps.pendentes > 0 ? styles.tpAlerta : d.tps.atraso > 0 ? styles.tpAtraso : styles.tpOk}>
                              {d.tps.entregues}/{d.tps.total}
                            </span>
                          </td>
                          <td className={styles.colSit}>
                            <span className={`${styles.sitBadge} ${
                              d.situacao === "CURSANDO" ? styles.sitCursando :
                              d.situacao === "REPROVADO" ? styles.sitReprovado : styles.sitAprovado
                            }`} title={d.motivoSituacao}>
                              {d.situacao === "REPROVADO" ? <XCircle size={11} />
                                : d.situacao === "CURSANDO" ? <CircleDot size={11} />
                                : <CheckCircle2 size={11} />}
                              {d.situacaoDescricao}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        );
      })}

      {/* ── Mapa do curso ── */}
      <h2 className={styles.secTitulo}><Route size={15} /> Mapa do Curso</h2>
      <section className={styles.mapa}>
        <p className={styles.trilhaIntro}>
          Cada roda é um bloco: a metade de cima e a de baixo são as duas disciplinas,
          e o Projeto de Bloco fecha o conjunto. O núcleo é comum a todos; depois dele
          o curso segue por uma das trilhas.
        </p>

        <div className={styles.nucleo}>
          {blocos.map((b, i) => {
            const regs = b.disciplinas.filter(d => d.tipo === "REGULAR");
            return (
              <div key={b.numero} className={styles.passo}>
                <div className={`${styles.rodaG} ${
                  b.status === "CONCLUIDO" ? styles.rodaFeita : b.status === "EM_CURSO" ? styles.rodaAtual : ""
                }`} title={`Bloco ${b.numero} · ${b.titulo} · ${b.statusDescricao}`}>
                  <span className={styles.rodaMeta}>{regs[0]?.nome ?? b.titulo}</span>
                  <span className={styles.rodaLinha} />
                  <span className={styles.rodaMeta}>{regs[1]?.nome ?? "Projeto de Bloco"}</span>
                </div>
                <span className={styles.rodaNome}>{b.titulo}</span>
                <span className={styles.rodaPer}>{b.periodo}</span>
                {i < blocos.length - 1 && <span className={styles.seta} aria-hidden>→</span>}
              </div>
            );
          })}
        </div>

        <div className={styles.ramoTitulo}>
          <span />
          <b>Escolha uma trilha ao concluir o núcleo</b>
          <span />
        </div>

        <div className={styles.ramos}>
          {TRILHAS.map(t => (
            <div key={t.nome} className={styles.ramo} title={`${t.nome} · não iniciada`}>
              <div className={styles.rodaG}>
                <span className={styles.rodaMeta}>{t.disc[0]}</span>
                <span className={styles.rodaLinha} />
                <span className={styles.rodaMeta}>{t.disc[1]}</span>
              </div>
              <span className={styles.rodaNome}>{t.nome}</span>
            </div>
          ))}
        </div>
      </section>

      {/* ── Atividades por categoria ── */}
      {ORDEM_CATEGORIAS.map(cat => {
        const itens = porCategoria.get(cat) ?? [];
        const meta = carga.find(c => c.categoria === cat);
        const Ico = ICONE_CATEGORIA[cat];
        const titulo = itens[0]?.categoriaDescricao ?? meta?.descricao ?? cat;
        return (
          <div key={cat}>
            <h2 className={styles.secTitulo}><Ico size={15} /> {titulo}</h2>
            <section className={styles.bloco}>
              <p className={styles.trilhaIntro}>
                {meta
                  ? <>Carga horária necessária: <strong>{meta.exigida}h</strong> · cumprida: <strong>{meta.concluida}h</strong>.</>
                  : <>Exigem no mínimo <strong>{presencaMinima}% de presença</strong>, como as disciplinas regulares.</>}
              </p>
              <TabelaAtividades itens={itens} comPresenca={cat === "ELETIVA"} presencaMinima={presencaMinima} />
            </section>
          </div>
        );
      })}

      {/* ── Carga horária total ── */}
      <h2 className={styles.secTitulo}><Clock size={15} /> Carga Horária Total</h2>
      <section className={styles.totais}>
        <div className={styles.totaisTopo}>
          <div>
            <div className={styles.totaisVal}>
              {cargaFeita.toLocaleString("pt-BR")}
              <span className={styles.totaisMeta}> / {cargaMeta.toLocaleString("pt-BR")}h</span>
            </div>
            <div className={styles.totaisLbl}>integralização do curso</div>
          </div>
          <div className={styles.totaisPct}>{pctCurso}%</div>
        </div>
        <div className={styles.totaisTrilho}><span style={{ width: `${pctCurso}%` }} /></div>

        <ul className={styles.totaisLista}>
          {totais.map(t => {
            const pct = t.meta > 0 ? Math.min(100, Math.round((t.feito / t.meta) * 100)) : 0;
            const TIco = t.icon;
            return (
              <li key={t.label} className={styles.totalItem}>
                <div className={styles.totalTop}>
                  <span className={styles.totalLabel}><TIco size={13} style={{ color: t.cor }} /> {t.label}</span>
                  <span className={styles.totalNum} style={{ color: t.cor }}>{t.feito}h <i>/ {t.meta}h</i></span>
                </div>
                <div className={styles.totalTrilho}>
                  <span style={{ width: `${pct}%`, background: `linear-gradient(90deg, ${t.cor}, ${t.cor}bb)`, boxShadow: `0 0 8px ${t.cor}55` }} />
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      {/* ── Regras de aprovação ── */}
      <section className={styles.regras}>
        <h2 className={styles.regrasTitulo}><FileCheck2 size={15} /> Como funciona a aprovação</h2>
        <ol className={styles.regrasLista}>
          <li>Demonstrar <strong>todas</strong> as competências previstas para a disciplina.</li>
          <li>Ter <strong>{presencaMinima}% de presença</strong> nas aulas, na modalidade presencial.</li>
          <li>Entregar os <strong>Testes de Performance (TPs)</strong> até o prazo limite.</li>
          <li>Ser aprovado na disciplina de <strong>Projeto de Bloco</strong>, em blocos iniciados a partir de 2025.</li>
        </ol>
        <p className={styles.regrasNota}>
          Um TP entregue fora do prazo normal limita os conceitos do AT a <b>DL</b>; dois ou mais limitam a <b>D</b>;
          e um TP não entregue até o prazo limite torna as competências <b>ND</b>. Planejamento de Curso e Carreira
          e Fluência em IA não reprovam por frequência.
        </p>
      </section>

      <p className={styles.rodape}>
        Documento gerado pela plataforma Infnet Hub · dados do serviço de Desempenho Acadêmico.
      </p>
    </div>
  );
}
