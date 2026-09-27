"use client";

import { useState, useRef, useEffect } from "react";
import { createPortal } from "react-dom";
import { usePathname, useRouter } from "next/navigation";
import {
  Search, Bell, MessageSquare, LogOut,
  Users, Trophy, Briefcase,
  User, Settings, ImageIcon,
  FileText, X,
  CheckCheck, CircleSlash, Circle, Trash2, SquarePen, ChevronDown, ChevronUp,
  CircleDashed, ListChecks, MailOpen, Mail, Check,
  GraduationCap, Activity, ThumbsUp, MessageCircle, UserPlus, Cookie, ArrowLeft, Megaphone,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import MarqueeText from "@/components/ui/MarqueeText";
import DragScroll from "@/components/ui/DragScroll";
import { Usuario } from "@/types";
import { boletimService } from "@/services/boletimService";
import { postService } from "@/services/postService";
import { vagaService } from "@/services/vagaService";
import { signOut } from "@/hooks/useCurrentUser";
import { ABRIR_PREFERENCIAS } from "@/lib/consentimento";
import { gravarJanela, lerJanelas, REDEFINIR_JANELAS } from "@/lib/janelas";
import { initials, relativo, normalizar, casaComBusca } from "@/utils/format";
import { CORES } from "@/utils/colors";
import { MENSAGENS as MESSAGES } from "@/data/inbox";
import { useNotificacoes } from "@/hooks/useNotificacoes";
import type { TipoNotificacao } from "@/types/notificacao";
import { useStatus, infoStatus } from "@/hooks/useStatus";
import StatusPicker from "./StatusPicker";
import styles from "./Header.module.css";

interface Props {
  currentUser: Usuario;
}

const MENU_ITEMS: { label: string; icon: LucideIcon; href?: string }[] = [
  { label: "Perfil",        icon: User,       href: "/perfil" },
  { label: "Grupos",        icon: Users       },
  { label: "Trilhas",       icon: Trophy      },
  { label: "Fotos",         icon: ImageIcon   },
  { label: "Configurações", icon: Settings    },
];

/* O sino só navega para telas que existem. Um link de outra natureza (avisos
   antigos apontavam para "/usuarios/{id}", que é endereço da API) deixaria a
   navegação sem destino e derrubaria a página. */
const TELAS = ["/feed", "/vagas", "/boletim", "/desempenho", "/perfil"];
function linkDaPlataforma(link: string | null): boolean {
  if (!link) return false;
  const caminho = link.split(/[?#]/)[0];
  return TELAS.some(t => caminho === t || caminho.startsWith(t + "/"));
}

// Painel de operação (Grafana). O item só aparece para o suporte de TI — mas
// quem garante o acesso é o próprio Grafana, que pede login no Keycloak e
// recusa qualquer outro papel. Esconder o link aqui é conveniência, não
// segurança.
const PAINEL_DE_OPERACAO = process.env.NEXT_PUBLIC_GRAFANA_URL || "http://localhost:21300";

/* ── Índice de busca das páginas acadêmicas ──
   Só o que é ESTRUTURA: nomes de seção, regras do curso e trilhas. As entradas
   que descreviam o percurso do aluno — cada disciplina com sua situação, cada
   atividade com sua carga — saíram daqui e passaram a ser derivadas do
   boletim-service em tempo de busca (ver indiceDoAluno, abaixo).

   Motivo: eram uma segunda cópia dos mesmos dados. A busca dizia "Bloco 2 ·
   Aprovado · conceito DL" a partir de um texto fixo, e continuaria dizendo isso
   depois de a secretaria corrigir o conceito — divergindo do boletim sem que
   nada acusasse o erro. ── */
const INDICE_PAGINAS: { category: string; label: string; sub: string; href: string; termos: string }[] = [
  // ── Boletim: seções e regras ──
  { category: "Boletim", label: "Boletim Acadêmico",           sub: "histórico por competências e carga horária", href: "/boletim", termos: "boletim historico escolar competencias frequencia situacao blocos graduacao" },
  { category: "Boletim", label: "Perfil de conceitos",         sub: "DML · DL · D · ND — como cada conceito é atribuído", href: "/boletim", termos: "conceito conceitos dml dl d nd demonstrou louvor maximo rubrica competencia at" },
  { category: "Boletim", label: "Como funciona a aprovação",   sub: "competências · 75% de presença · TPs no prazo · PB", href: "/boletim", termos: "aprovacao regras quesitos presenca 75 tp prazo projeto bloco reprovado" },
  { category: "Boletim", label: "Mapa do Curso",               sub: "blocos do núcleo e trilhas de especialização", href: "/boletim", termos: "mapa curso nucleo trilhas blocos rodas especializacao" },
  { category: "Boletim", label: "Carga horária total",         sub: "integralização do curso por categoria", href: "/boletim", termos: "carga horaria total integralizacao horas disciplinas extensao estagio complementares" },
  // ── Boletim: as quatro categorias de atividade (as atividades em si vêm do serviço) ──
  { category: "Boletim", label: "Projetos Supervisionados de Extensão", sub: "400h necessárias · sem exigência de presença", href: "/boletim", termos: "projetos supervisionados extensao 400h carga sem presenca" },
  { category: "Boletim", label: "Disciplinas Eletivas",        sub: "exigem no mínimo 75% de presença", href: "/boletim", termos: "eletivas eletiva presenca 75 minimo" },
  { category: "Boletim", label: "Estágio Obrigatório",         sub: "400h necessárias de estágio supervisionado", href: "/boletim", termos: "estagio obrigatorio 400h supervisionado" },
  { category: "Boletim", label: "Atividades Complementares",   sub: "140h necessárias · certificações e monitorias", href: "/boletim", termos: "atividades complementares 140h certificacao monitoria" },
  // ── Boletim: trilhas de especialização (estrutura do curso, não percurso) ──
  { category: "Boletim", label: "Trilha: Inteligência Artificial",  sub: "Machine Learning · Multi-Agentes IA", href: "/boletim", termos: "trilha inteligencia artificial machine learning multi-agentes ia" },
  { category: "Boletim", label: "Trilha: Sistemas Complexos",       sub: "Engenharia Disciplinada de Softwares", href: "/boletim", termos: "trilha sistemas complexos engenharia disciplinada" },
  { category: "Boletim", label: "Trilha: Engenharia de Dados",      sub: "Banco de Dados · Big Data", href: "/boletim", termos: "trilha engenharia dados big data banco" },
  { category: "Boletim", label: "Trilha: Cibersegurança",           sub: "SOC e Blue Team · Red Team", href: "/boletim", termos: "trilha ciberseguranca seguranca soc blue red team ofensiva defensiva" },
  { category: "Boletim", label: "Trilha: Cloud Computing",          sub: "Conteinerização · Arquitetura na Nuvem", href: "/boletim", termos: "trilha cloud computing conteinerizacao nuvem arquitetura" },
  // ── Perfil ──
  { category: "Perfil", label: "Meu perfil", sub: "panorama do aluno, status e horas obrigatórias", href: "/perfil", termos: "perfil conta aluno panorama resumo status horas" },
  // ── Meu Desempenho: seções do painel ──
  { category: "Meu Desempenho", label: "Score geral no curso",     sub: "anéis de competências, presença e entregas", href: "/desempenho", termos: "score geral aneis competencias presenca entregas indice conceito medio" },
  { category: "Meu Desempenho", label: "Conclusão do curso",       sub: "progresso pelos blocos da graduação", href: "/desempenho", termos: "conclusao curso progresso blocos faltam formatura 12" },
  { category: "Meu Desempenho", label: "Tendência do rendimento",  sub: "variação do índice entre blocos", href: "/desempenho", termos: "tendencia rendimento ponteiro subindo caindo evolucao" },
  { category: "Meu Desempenho", label: "Evolução do índice por bloco", sub: "média dos conceitos lançados em cada bloco", href: "/desempenho", termos: "evolucao indice bloco grafico linha conceitos media" },
  { category: "Meu Desempenho", label: "Evolução no semestre e projeção", sub: "você × turma + previsão dos próximos meses", href: "/desempenho", termos: "evolucao semestre projecao previsao futura media turma grafico linha" },
  { category: "Meu Desempenho", label: "Comparação com turmas anteriores", sub: "índice médio de conceitos por turma", href: "/desempenho", termos: "comparacao turmas anteriores indice medio barras coorte" },
  { category: "Meu Desempenho", label: "Presenças por disciplina", sub: "frequência do bloco em curso", href: "/desempenho", termos: "presenca presencas frequencia faltas disciplinas 75" },
  { category: "Meu Desempenho", label: "Horas obrigatórias restantes", sub: "extensão, estágio e complementares", href: "/desempenho", termos: "horas obrigatorias restantes extensao estagio complementares carga" },
  { category: "Meu Desempenho", label: "Horas complementares",  sub: "quanto falta das 140h", href: "/desempenho", termos: "horas complementares atividades 140 restantes" },
  { category: "Meu Desempenho", label: "Horas de estágio",      sub: "quanto falta das 400h", href: "/desempenho", termos: "horas estagio obrigatorio 400 restantes" },
  { category: "Meu Desempenho", label: "Horas de extensão",     sub: "quanto falta das 400h", href: "/desempenho", termos: "horas extensao projetos supervisionados 400 restantes" },
];

/* Percurso do aluno indexado a partir do boletim-service.

   Buscado uma vez por sessão e guardado no módulo: a busca dispara a cada
   tecla, e refazer a chamada a cada letra sobrecarregaria o serviço para
   devolver sempre o mesmo conteúdo. A falha é silenciosa de propósito — se o
   microsserviço estiver fora, a busca continua funcionando com posts, vagas e
   as entradas estruturais acima, apenas sem as disciplinas. */
type ItemIndice = { category: string; label: string; sub: string; href: string; termos: string };

let cacheIndiceAluno: ItemIndice[] | null = null;

async function indiceDoAluno(alunoId: number): Promise<ItemIndice[]> {
  if (cacheIndiceAluno) return cacheIndiceAluno;
  try {
    const [boletim, atividades] = await Promise.all([
      boletimService.buscarBoletim(alunoId),
      boletimService.listarAtividades(alunoId),
    ]);

    const itens: ItemIndice[] = [];

    for (const bloco of boletim.blocos) {
      for (const d of bloco.disciplinas) {
        const conceito = d.conceitoFinal ? ` · conceito ${d.conceitoFinal}` : "";
        itens.push({
          category: "Boletim",
          label: d.nome,
          sub: `Bloco ${bloco.numero} · ${d.periodo} · ${d.situacaoDescricao}${conceito}`,
          href: "/boletim",
          termos: `${d.nome} bloco ${bloco.numero} ${d.periodo} ${d.situacaoDescricao} ${d.conceitoFinal ?? ""}`.toLowerCase(),
        });
      }
    }

    for (const a of atividades) {
      itens.push({
        category: "Boletim",
        label: a.nome,
        sub: `${a.categoriaDescricao} · ${a.cargaHoraria}h${a.periodo ? ` · ${a.periodo}` : ""} · ${a.statusDescricao}`,
        href: "/boletim",
        termos: `${a.nome} ${a.categoriaDescricao} ${a.periodo ?? ""} ${a.statusDescricao}`.toLowerCase(),
      });
    }

    cacheIndiceAluno = itens;
    return itens;
  } catch {
    return [];
  }
}

// notificações e mensagens vêm de @/data/inbox (fonte única compartilhada com a sidebar)

/* ── Pasta de mensagens: contorno como UM path SVG único ──
   corpo + aba desenhados numa só forma (fill + stroke contínuos), gerada a
   partir da largura/altura reais do dock — assim o contorno acompanha o
   crescimento da lista sozinho, sem "duas peças". */
const ABA_PEEK = 34; // altura da aba — título em tamanho padrão, centralizado
const PASTA_R  = 14; // raio dos cantos
function pastaPath(w: number, h: number): string {
  const H = h + ABA_PEEK;   // altura total do SVG (inclui a aba)
  const t = ABA_PEEK;       // y da borda superior do corpo
  return [
    `M0 ${t}`,                                   // sobe pela esquerda até a aba
    `Q0 0 16 0`,                                 // canto superior-esquerdo da aba
    `L132 0`,                                    // topo reto da aba (mais larga)
    `C158 0 158 ${t} 186 ${t}`,                  // ombro: curva descendo ao corpo
    `L${w - PASTA_R} ${t}`,                      // borda superior do corpo
    `Q${w} ${t} ${w} ${t + PASTA_R}`,            // canto superior-direito (arredondado)
    // base RETA e encostada: o dock ancora na margem inferior, "fazendo parte"
    // dela (não flutua) — cantos de baixo sem arredondar
    `L${w} ${H}`,                                // lateral direita até a base
    `L0 ${H}`,                                   // base
    "Z",                                         // fecha subindo pela esquerda
  ].join(" ");
}

/* Ícone de cada tipo de notificação, na mesma linguagem visual do mock anterior. */
const ICONE_NOTIFICACAO: Record<TipoNotificacao, LucideIcon> = {
  BOAS_VINDAS: UserPlus,
  CURTIDA:     ThumbsUp,
  COMENTARIO:  MessageCircle,
  VAGA:        Briefcase,
  AVISO:       Megaphone,
};

/* "agora", "5min atrás", "2h atrás" — ou a data, para o que tem mais de uma semana. */
function quando(iso: string): string {
  const r = relativo(iso);
  return r === "agora" || r.includes("/") ? r : `${r} atrás`;
}

/* Título curto por papel + primeiro nome: "Prof. Carlos", "Coord. Ana"… */
export default function Header({ currentUser }: Props) {
  const router = useRouter();
  const [menuOpen,      setMenuOpen]      = useState(false);
  const [notifOpen,     setNotifOpen]     = useState(false);
  const [msgOpen,       setMsgOpen]       = useState(false);
  const [searchFocused, setSearchFocused] = useState(false);
  const [teclas,        setTeclas]        = useState({ meta: false, ctrl: false, k: false });
  const [so,            setSo]            = useState<"mac" | "windows" | "linux">("windows");
  const [searchValue,   setSearchValue]   = useState("");
  const [suggestions,   setSuggestions]   = useState<{ category: string; label: string; sub: string; href: string }[]>([]);
  const [showSugg,      setShowSugg]      = useState(false);
  // de onde a busca partiu: o texto fica na barra ao abrir o resultado, e um
  // botão leva de volta para lá
  const [origemDaBusca, setOrigemDaBusca] = useState<string | null>(null);
  const caminhoAtual = usePathname();

  const menuRef   = useRef<HTMLDivElement>(null);
  const notifRef  = useRef<HTMLDivElement>(null);
  const msgRef    = useRef<HTMLDivElement>(null);
  const msgDockRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const suggRef   = useRef<HTMLDivElement>(null);

  const cor        = CORES[currentUser.id % CORES.length];
  const myInitials = initials(currentUser.nome);
  const statusAtual = useStatus();

  const [msgCollapsed, setMsgCollapsed] = useState(false);

  // Estado das mensagens lembrado entre visitas (só com consentimento — ver
  // lib/janelas.ts). Lido depois de montar, para não divergir da renderização
  // do servidor; e só grava depois de ler, para não apagar o que estava guardado.
  const janelasLidas = useRef(false);
  useEffect(() => {
    const guardado = lerJanelas().mensagens;
    if (guardado && guardado !== "fechada") { setMsgOpen(true); setMsgCollapsed(guardado === "recolhida"); }
    janelasLidas.current = true;
    const padrao = () => { setMsgOpen(false); setMsgCollapsed(false); };
    window.addEventListener(REDEFINIR_JANELAS, padrao);
    return () => window.removeEventListener(REDEFINIR_JANELAS, padrao);
  }, []);
  // com as mensagens abertas, a marca do fundo sobe para o canto de cima,
  // para não ficar escondida atrás do dock (AppLayout.module.css)
  useEffect(() => {
    document.documentElement.toggleAttribute("data-mensagens-abertas", msgOpen);
    return () => document.documentElement.removeAttribute("data-mensagens-abertas");
  }, [msgOpen]);
  useEffect(() => {
    if (!janelasLidas.current) return;
    gravarJanela({ mensagens: !msgOpen ? "fechada" : msgCollapsed ? "recolhida" : "aberta" });
  }, [msgOpen, msgCollapsed]);

  // dimensões reais do dock → path da pasta se ajusta ao crescer a lista
  const [pasta, setPasta] = useState({ w: 320, h: 240 });

  // busca dentro das mensagens (nome ou texto)
  const [buscaMsg, setBuscaMsg] = useState("");

  // popup de confirmação (excluir mensagens/notificações)
  const [confirmar, setConfirmar] = useState<{ texto: string; acao: () => void } | null>(null);

  // modo seleção: as bolinhas de marcar só aparecem quando ativado
  const [selNotif, setSelNotif] = useState(false);
  const [selMsg,   setSelMsg]   = useState(false);
  const toggleSelNotif = () => { setSelNotif(v => !v); setMarcadas(new Set()); };
  const toggleSelMsg   = () => { setSelMsg(v => !v);   setMarcadasMsg(new Set()); };

  // ── Notificações: vêm do notificacao-service, ao vivo (SSE) ──
  // Até o TP3 eram uma lista fixa em @/data/inbox. Agora cada curtida,
  // comentário ou vaga nova vira aviso real, gerado por evento no RabbitMQ.
  const notificacoes = useNotificacoes();
  const notifs = notificacoes.itens;
  const [marcadas, setMarcadas] = useState<Set<number>>(new Set());
  const unreadNotif = notificacoes.naoLidas;
  const toggleMarca = (id: number) => setMarcadas(prev => {
    const s = new Set(prev); if (s.has(id)) s.delete(id); else s.add(id); return s;
  });
  const todasMarcadas = notifs.length > 0 && marcadas.size === notifs.length;
  const marcarTodas = () => { setSelNotif(true); setMarcadas(todasMarcadas ? new Set() : new Set(notifs.map(n => n.id))); };
  const excluirMarcadas = () => {
    if (marcadas.size === 0) return;
    setConfirmar({
      texto: `Excluir ${marcadas.size} ${marcadas.size > 1 ? "notificações selecionadas" : "notificação selecionada"}?`,
      acao: () => { notificacoes.excluir([...marcadas]); setMarcadas(new Set()); },
    });
  };

  // Dá baixa nas não lidas quando o popup FECHA, e não quando abre: assim o
  // destaque de "nova" continua visível enquanto a pessoa lê a lista. As que
  // a pessoa marcou como não lidas de propósito continuam não lidas.
  const notifEstavaAberto = useRef(false);
  const mantidasNaoLidas = useRef<Set<number>>(new Set());
  useEffect(() => {
    if (notifEstavaAberto.current && !notifOpen) {
      notificacoes.marcarComoLidas(
        notificacoes.itens.filter(n => !n.lida && !mantidasNaoLidas.current.has(n.id)).map(n => n.id));
      mantidasNaoLidas.current = new Set();
    }
    notifEstavaAberto.current = notifOpen;
  }, [notifOpen, notificacoes]);
  const notifComoLidas = (ids: number[]) => {
    ids.forEach(id => mantidasNaoLidas.current.delete(id));
    notificacoes.marcarComoLidas(ids);
  };
  const notifComoNaoLidas = (ids: number[]) => {
    ids.forEach(id => mantidasNaoLidas.current.add(id));
    notificacoes.marcarComoNaoLidas(ids);
  };

  // ── Mensagens: estado local (marcar / excluir) ──
  const [msgs, setMsgs]         = useState(MESSAGES);
  const [marcadasMsg, setMarcadasMsg] = useState<Set<number>>(new Set());
  const unreadMsg = msgs.filter(m => m.unread).length;
  const toggleMarcaMsg = (id: number) => setMarcadasMsg(prev => {
    const s = new Set(prev); if (s.has(id)) s.delete(id); else s.add(id); return s;
  });
  const todasMsgMarcadas = msgs.length > 0 && marcadasMsg.size === msgs.length;
  const marcarTodasMsg = () => { setSelMsg(true); setMarcadasMsg(todasMsgMarcadas ? new Set() : new Set(msgs.map(m => m.id))); };
  // marcar como lida / não lida — o contador de não-lidas deriva do estado
  const marcarLida = (id: number) =>
    setMsgs(prev => prev.map(m => (m.id === id ? { ...m, unread: false } : m)));
  const marcarNaoLida = (id: number) =>
    setMsgs(prev => prev.map(m => (m.id === id ? { ...m, unread: true } : m)));
  // em lote, sobre as selecionadas (modo seleção)
  const lidasSelecionadas = () =>
    setMsgs(prev => prev.map(m => (marcadasMsg.has(m.id) ? { ...m, unread: false } : m)));
  const naoLidasSelecionadas = () =>
    setMsgs(prev => prev.map(m => (marcadasMsg.has(m.id) ? { ...m, unread: true } : m)));
  const excluirMsgMarcadas = () => {
    if (marcadasMsg.size === 0) return;
    setConfirmar({
      texto: `Excluir ${marcadasMsg.size} mensagem${marcadasMsg.size > 1 ? "s" : ""} selecionada${marcadasMsg.size > 1 ? "s" : ""}?`,
      acao: () => { setMsgs(prev => prev.filter(m => !marcadasMsg.has(m.id))); setMarcadasMsg(new Set()); },
    });
  };

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (suggRef.current && !suggRef.current.contains(e.target as Node)) setShowSugg(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  // mede o dock e mantém o path da pasta sincronizado com a altura da lista
  useEffect(() => {
    if (!msgOpen) return;
    const el = msgDockRef.current;
    if (!el) return;
    const medir = () => setPasta({ w: el.offsetWidth, h: el.offsetHeight });
    medir();
    const ro = new ResizeObserver(medir);
    ro.observe(el);
    return () => ro.disconnect();
  }, [msgOpen, msgCollapsed, msgs.length, buscaMsg, selMsg]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current  && !menuRef.current.contains(e.target  as Node)) setMenuOpen(false);
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) setNotifOpen(false);
      // o dock de mensagens é persistente: clicar na página NÃO fecha
      // (só fecha pelo X ou pelo botão da tab bar)
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  useEffect(() => {
    // detecta o sistema para mostrar só o atalho e o ícone correspondentes
    const ua = navigator.userAgent.toLowerCase();
    if (/mac|iphone|ipad/.test(ua)) setSo("mac");
    else if (/linux|x11|ubuntu|fedora/.test(ua) && !/android/.test(ua)) setSo("linux");
    else setSo("windows");
  }, []);

  useEffect(() => {
    // acompanha cada tecla do atalho isoladamente: segura Ctrl → acende Ctrl,
    // aperta K → acende K; cada uma afunda no momento em que é pressionada
    const down = (e: KeyboardEvent) => {
      if (e.key === "Control") setTeclas(t => ({ ...t, ctrl: true }));
      if (e.key === "Meta")    setTeclas(t => ({ ...t, meta: true }));
      if (e.key.toLowerCase() === "k") setTeclas(t => ({ ...t, k: true }));
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        searchRef.current?.focus();
      }
      if (e.key === "Escape") {
        searchRef.current?.blur();
        setMenuOpen(false); setNotifOpen(false); setMsgOpen(false);
      }
    };
    const up = (e: KeyboardEvent) => {
      if (e.key === "Control") setTeclas(t => ({ ...t, ctrl: false }));
      if (e.key === "Meta")    setTeclas(t => ({ ...t, meta: false }));
      if (e.key.toLowerCase() === "k") setTeclas(t => ({ ...t, k: false }));
    };
    window.addEventListener("keydown", down);
    window.addEventListener("keyup", up);
    return () => {
      window.removeEventListener("keydown", down);
      window.removeEventListener("keyup", up);
    };
  }, []);

  useEffect(() => {
    const q = normalizar(searchValue.trim());
    if (q.length < 2) { setSuggestions([]); setShowSugg(false); return; }
    const casa = (...campos: (string | null | undefined)[]) => casaComBusca(q, ...campos);
    const timer = setTimeout(async () => {
      try {
        const [postsRes, vagasRes, indiceAluno] = await Promise.all([
          // pelos serviços, que mandam o cookie de sessão: um fetch cru levava 401
          // e a busca voltava vazia
          postService.listarTodos().catch(() => []),
          vagaService.listarAtivas().catch(() => []),
          indiceDoAluno(currentUser.id),
        ]);
        const results: typeof suggestions = [];
        (postsRes as { id: number; titulo: string | null; conteudo: string; autorNome: string }[])
          .filter(p => casa(p.titulo, p.conteudo, p.autorNome))
          .slice(0, 4)
          .forEach(p => results.push({
            category: "Posts",
            label: p.titulo ?? p.conteudo.slice(0, 50),
            sub: p.autorNome,
            href: `/feed?q=${encodeURIComponent(q)}`,
          }));
        (vagasRes as { id: number; titulo: string; empresa: string; categoria: string | null; localizacao: string | null }[])
          .filter(v => casa(v.titulo, v.empresa, v.categoria, v.localizacao))
          .slice(0, 4)
          .forEach(v => results.push({
            category: "Vagas",
            label: v.titulo,
            sub: v.empresa,
            href: `/vagas?q=${encodeURIComponent(q)}`,
          }));
        // Páginas acadêmicas: as seções e regras vêm do índice estrutural, e o
        // percurso do aluno (disciplinas e atividades) do boletim-service. Os
        // dois são filtrados juntos para que a busca não precise saber de onde
        // cada resultado veio.
        [...indiceAluno, ...INDICE_PAGINAS]
          .filter(p => casa(p.label, p.sub, p.termos))
          .slice(0, 5)
          .forEach(p => results.push({ category: p.category, label: p.label, sub: p.sub, href: p.href }));

        setSuggestions(results);
        setShowSugg(results.length > 0);
      } catch { setSuggestions([]); }
    }, 300);
    return () => clearTimeout(timer);
  }, [searchValue, currentUser.id]);

  const handleSearchNav = (href?: string) => {
    const q = searchValue.trim();
    setShowSugg(false);
    // o texto continua na barra; guarda-se só a primeira origem, para "voltar"
    // levar ao ponto de partida mesmo depois de abrir vários resultados
    if (!origemDaBusca) setOrigemDaBusca(window.location.pathname + window.location.search + window.location.hash);
    const target = href ?? `/feed?q=${encodeURIComponent(q)}`;
    window.dispatchEvent(new CustomEvent("infnet:search", { detail: { query: q } }));
    router.push(target);
  };

  // path da pasta (usado pelo vidro recortado e pelo contorno SVG)
  const pastaD = pastaPath(pasta.w, pasta.h);
  // nome curto: título + primeiro nome ("Prof. Carlos"), sem sobrenome
  const partesNome = currentUser.nome.trim().split(/\s+/);
  const nomeCurto = /\.$/.test(partesNome[0]) && partesNome[1]
    ? `${partesNome[0]} ${partesNome[1]}`
    : partesNome[0];

  return (
    <header className={styles.header}>
      <div className={styles.inner}>

        {/* ── Busca ── */}
        <div className={styles.center}>

          {/* Busca sempre expandida: knob de vidro com a lupa + cursor de
              terminal esmaecendo suave antes de "Buscar" */}
          <div ref={suggRef} className={`${styles.searchWrap} ${searchFocused ? styles.searchFocused : ""}`}>
            <span className={styles.searchKnob}>
              <span className={styles.searchIconAnim}><Search size={14} /></span>
            </span>

            {!searchValue && <span className={styles.caret} aria-hidden />}

            <input
              ref={searchRef}
              className={`${styles.searchInput} ${searchValue ? "" : styles.searchInputEmpty}`}
              placeholder="Buscar"
              value={searchValue}
              onChange={e => setSearchValue(e.target.value)}
              onFocus={() => { setSearchFocused(true); if (suggestions.length > 0) setShowSugg(true); }}
              onBlur={() => setSearchFocused(false)}
              onKeyDown={e => {
                if (e.key === "Enter") { handleSearchNav(); searchRef.current?.blur(); }
                if (e.key === "Escape") { setShowSugg(false); setSearchValue(""); setOrigemDaBusca(null); searchRef.current?.blur(); }
              }}
            />
            {origemDaBusca && origemDaBusca.split(/[?#]/)[0] !== caminhoAtual && (
              <button className={styles.searchVoltar} title="Voltar para onde você estava" aria-label="Voltar para onde você estava"
                      onClick={() => { const o = origemDaBusca; setOrigemDaBusca(null); setSearchValue(""); setShowSugg(false); router.push(o); }}>
                <ArrowLeft size={11} />
              </button>
            )}
            {searchValue ? (
              <button className={styles.searchClear} title="Limpar a busca"
                      onClick={() => { setSearchValue(""); setShowSugg(false); setOrigemDaBusca(null); searchRef.current?.focus(); }}>
                <X size={12} />
              </button>
            ) : (
              <span className={styles.searchKbd}>
                {so === "mac" ? (
                  <>
                    <svg className={styles.kbdGlifo} width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>
                    <kbd className={teclas.meta ? styles.kbdPress : ""}>⌘</kbd>
                    <kbd className={teclas.k ? styles.kbdPress : ""}>K</kbd>
                  </>
                ) : so === "linux" ? (
                  <>
                    <span className={styles.kbdGlifo} aria-hidden style={{ fontSize: "13px", lineHeight: 1 }}>🐧</span>
                    <kbd className={teclas.ctrl ? styles.kbdPress : ""}>Ctrl</kbd>
                    <kbd className={teclas.k ? styles.kbdPress : ""}>K</kbd>
                  </>
                ) : (
                  <>
                    <svg className={styles.kbdGlifo} width="11" height="11" viewBox="0 0 88 88" fill="currentColor"><path d="M0 12.402l35.687-4.86.016 34.423-35.67.203zm35.67 33.529l.017 34.453L.001 75.48V45.7zm4.326-38.025L87.314 0v41.527l-47.318.376zm47.329 41.123l-.011 41.343-47.318-6.678-.066-34.739z"/></svg>
                    <kbd className={teclas.ctrl ? styles.kbdPress : ""}>Ctrl</kbd>
                    <kbd className={teclas.k ? styles.kbdPress : ""}>K</kbd>
                  </>
                )}
              </span>
            )}

            {showSugg && suggestions.length > 0 && (
              <div className={styles.suggBox}>
                {["Posts", "Vagas", "Perfil", "Boletim", "Meu Desempenho"].map(cat => {
                  const items = suggestions.filter(s => s.category === cat);
                  if (!items.length) return null;
                  const CatIco = cat === "Posts" ? FileText
                    : cat === "Vagas" ? Briefcase
                    : cat === "Perfil" ? User
                    : cat === "Boletim" ? GraduationCap : Activity;
                  return (
                    <div key={cat}>
                      <div className={styles.suggCat}>
                        <CatIco size={11} /> {cat}
                      </div>
                      {items.map((s, i) => (
                        <button key={i} className={styles.suggItem} onMouseDown={() => handleSearchNav(s.href)}>
                          <span className={styles.suggLabel}>{s.label}</span>
                          <span className={styles.suggSub}>{s.sub}</span>
                        </button>
                      ))}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* ── Direita: ícones + usuário ── */}
        <div className={styles.right}>

          {/* Mensagens */}
          <div ref={msgRef} className={styles.popWrap}>
            <button
              className={styles.iconBtn}
              title="Mensagens"
              onClick={() => { setMsgOpen(v => !v); setNotifOpen(false); setMenuOpen(false); }}
            >
              <MessageSquare size={18} />
              {unreadMsg > 0 && <span className={styles.badge}>{unreadMsg}</span>}
            </button>

            {msgOpen && createPortal(
              <div ref={msgDockRef} className={`${styles.msgDock} ${msgCollapsed ? styles.msgDockCollapsed : ""}`}>
                {/* PASTA (corpo + aba numa forma só), dimensionada pela altura
                    real do dock → cresce com a lista.
                    · vidro: div com backdrop-filter recortado no path (glassmorphism)
                    · contorno: SVG só com o stroke por cima */}
                <div className={styles.pastaGlass} style={{
                  width: pasta.w, height: pasta.h + ABA_PEEK,
                  clipPath: `path("${pastaD}")`, WebkitClipPath: `path("${pastaD}")`,
                }} />
                <svg className={styles.pastaSvg} width={pasta.w} height={pasta.h + ABA_PEEK}
                     viewBox={`0 0 ${pasta.w} ${pasta.h + ABA_PEEK}`} aria-hidden="true">
                  <path className={styles.pastaStroke} d={pastaD} />
                </svg>
                {/* título dentro da abinha da pasta */}
                <div className={styles.msgAba}>
                  <MessageSquare size={15} strokeWidth={2.6} className={styles.msgAbaIco} />
                  <span className={styles.msgAbaTitle}>Mensagens</span>
                </div>
                {/* plano superior (barra do avatar + busca) — flutua acima da
                    lista, com sombra em cima e embaixo e uma folga separando */}
                <div className={styles.msgPlano}>
                {/* barra: avatar + nome · ações (flat) · janela recolher/fechar (carved) */}
                <div className={styles.msgTab} onClick={() => msgCollapsed && setMsgCollapsed(false)}>
                  <div className={styles.meuAv} style={{ background: cor }} title={nomeCurto}>{myInitials}</div>
                  <StatusPicker />
                  <div className={styles.msgTabActions}>
                    {/* padrão: só Selecionar + Nova mensagem. No modo seleção
                        abre o kit: marcar/desmarcar todas · lidas · não lidas
                        · excluir (as três últimas exigem algo selecionado) */}
                    <button className={`${styles.popIco} ${styles.msgBtnSel} ${selMsg ? styles.popIcoOn : ""}`}
                      title={selMsg ? "Cancelar seleção" : "Selecionar"}
                      onClick={e => { e.stopPropagation(); toggleSelMsg(); }}>
                      {selMsg ? <ListChecks size={15} /> : <CircleDashed size={15} />}
                    </button>
                    {!selMsg && (
                      <button className={styles.popIco} title="Nova mensagem" onClick={e => e.stopPropagation()}><SquarePen size={15} /></button>
                    )}
                  </div>
                  <div className={styles.msgTabWindow}>
                    <button className={styles.popIco} title={msgCollapsed ? "Expandir" : "Recolher"}
                      onClick={e => { e.stopPropagation(); setMsgCollapsed(v => !v); }}>
                      {msgCollapsed ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </button>
                    <button className={`${styles.popIco} ${styles.popIcoClose}`} title="Fechar"
                      onClick={e => { e.stopPropagation(); setMsgOpen(false); }}><X size={15} /></button>
                  </div>
                </div>
                {/* as ações da seleção numa linha própria, abaixo da aba: na mesma
                    linha do status elas passavam da borda do dock */}
                {selMsg && (
                  <div className={styles.msgSelBarra} onClick={e => e.stopPropagation()}>
                    <span className={styles.msgSelInfo}>
                      {marcadasMsg.size === 0 ? "Selecione mensagens"
                        : `${marcadasMsg.size} selecionada${marcadasMsg.size > 1 ? "s" : ""}`}
                    </span>
                    <div className={styles.msgSelAcoes}>
                        <button className={`${styles.popIco} ${todasMsgMarcadas ? styles.icoNeutro : styles.icoVerde}`}
                          title={todasMsgMarcadas ? "Desmarcar todas" : "Marcar todas"}
                          onClick={e => { e.stopPropagation(); marcarTodasMsg(); }}>
                          {todasMsgMarcadas ? <CircleSlash size={15} /> : <CheckCheck size={15} />}
                        </button>
                        {marcadasMsg.size > 0 && (
                          <>
                            <button className={`${styles.popIco} ${styles.icoCiano}`} title="Marcar como lidas"
                              onClick={e => { e.stopPropagation(); lidasSelecionadas(); }}>
                              <MailOpen size={15} />
                            </button>
                            <button className={`${styles.popIco} ${styles.icoAmbar}`} title="Marcar como não lidas"
                              onClick={e => { e.stopPropagation(); naoLidasSelecionadas(); }}>
                              <Mail size={15} />
                            </button>
                            <button className={`${styles.popIco} ${styles.icoVermelho}`} title="Excluir selecionadas"
                              onClick={e => { e.stopPropagation(); excluirMsgMarcadas(); }}>
                              <Trash2 size={15} />
                            </button>
                          </>
                        )}
                    </div>
                  </div>
                )}
                {/* busca dentro das mensagens */}
                <div className={styles.msgBusca}>
                  <Search size={14} className={styles.msgBuscaIco} />
                  <input
                    className={styles.msgBuscaInput}
                    placeholder="Buscar mensagens"
                    value={buscaMsg}
                    onChange={e => setBuscaMsg(e.target.value)}
                    onClick={e => e.stopPropagation()}
                  />
                  {buscaMsg && (
                    <button className={styles.msgBuscaClear} title="Limpar"
                      onClick={e => { e.stopPropagation(); setBuscaMsg(""); }}>
                      <X size={12} />
                    </button>
                  )}
                </div>
                </div>
                <DragScroll className={styles.msgList}>
                  {(() => {
                    const q = buscaMsg.trim().toLowerCase();
                    const lista = q
                      ? msgs.filter(m => m.nome.toLowerCase().includes(q) || m.texto.toLowerCase().includes(q))
                      : msgs;
                    if (msgs.length === 0) return <div className={styles.popVazio}>Nenhuma mensagem.</div>;
                    if (lista.length === 0) return <div className={styles.popVazio}>Nada encontrado para “{buscaMsg}”.</div>;
                    return lista.map(m => {
                    const msgCor = CORES[m.id % CORES.length];
                    const marc = marcadasMsg.has(m.id);
                    return (
                      <div key={m.id} data-marquee-host className={`${styles.msgItem} ${m.unread ? styles.unread : ""} ${marc ? styles.marcada : ""}`}>
                        {selMsg && (
                          <button className={`${styles.radar} ${marc ? styles.radarOn : ""}`}
                            onClick={() => toggleMarcaMsg(m.id)} title={marc ? "Desmarcar" : "Marcar"}>
                            <Circle size={20} />
                            {marc && <Check size={13} className={styles.radarTick} />}
                          </button>
                        )}
                        <div className={styles.msgAv} style={{ background: msgCor }}>{initials(m.nome)}</div>
                        <div className={styles.msgBody}>
                          <span className={styles.msgNome}>{m.nome}</span>
                          <MarqueeText className={styles.msgTexto}>{m.texto}</MarqueeText>
                        </div>
                        <div className={styles.msgFim}>
                          <span className={styles.msgTime}>{m.time}</span>
                          {m.unread ? (
                            <button className={styles.lidaBtn} title="Marcar como lida"
                              onClick={() => marcarLida(m.id)}>
                              <span className={styles.lidaDot} aria-hidden />
                              <MailOpen size={13} />
                            </button>
                          ) : (
                            <button className={`${styles.lidaBtn} ${styles.naoLidaBtn}`} title="Marcar como não lida"
                              onClick={() => marcarNaoLida(m.id)}>
                              <Mail size={13} />
                            </button>
                          )}
                        </div>
                      </div>
                    );
                    });
                  })()}
                </DragScroll>
              </div>,
              document.body
            )}
          </div>

          {/* Notificações */}
          <div ref={notifRef} className={styles.popWrap}>
            <button
              className={styles.iconBtn}
              title="Notificações"
              onClick={() => { setNotifOpen(v => !v); setMenuOpen(false); /* o dock de mensagens fica: é persistente */ }}
            >
              <Bell size={18} />
              {unreadNotif > 0 && <span className={styles.badge}>{unreadNotif}</span>}
            </button>

            {notifOpen && (
              <div className={styles.popup}>
                <div className={styles.popHeader}>
                  <span className={styles.popTitle}><Bell size={15} className={styles.popTitleIco} /> Notificações</span>
                  <div className={styles.popTools}>
                    <button className={`${styles.popIco} ${selNotif ? styles.popIcoOn : ""}`}
                      title={selNotif ? "Cancelar seleção" : "Selecionar"} onClick={toggleSelNotif}>
                      {selNotif ? <ListChecks size={15} /> : <CircleDashed size={15} />}
                    </button>
                    {selNotif && (
                      <>
                        <button className={`${styles.popIco} ${todasMarcadas ? styles.icoNeutro : styles.icoVerde}`}
                          title={todasMarcadas ? "Desmarcar todas" : "Marcar todas"} onClick={marcarTodas}>
                          {todasMarcadas ? <CircleSlash size={15} /> : <CheckCheck size={15} />}
                        </button>
                        {marcadas.size > 0 && (
                          <>
                            <button className={`${styles.popIco} ${styles.icoCiano}`} title="Marcar como lidas"
                              onClick={() => notifComoLidas([...marcadas])}>
                              <MailOpen size={15} />
                            </button>
                            <button className={`${styles.popIco} ${styles.icoAmbar}`} title="Marcar como não lidas"
                              onClick={() => notifComoNaoLidas([...marcadas])}>
                              <Mail size={15} />
                            </button>
                            <button className={`${styles.popIco} ${styles.icoVermelho}`} title="Excluir selecionadas" onClick={excluirMarcadas}>
                              <Trash2 size={15} />
                            </button>
                          </>
                        )}
                      </>
                    )}
                  </div>
                </div>
                {notifs.length === 0 ? (
                  <div className={styles.popVazio}>
                    {notificacoes.disponivel ? "Nenhuma notificação." : "Notificações indisponíveis no momento."}
                  </div>
                ) : notifs.map(n => {
                  // um tipo que o front ainda não conheça ganha o sino, em vez de
                  // derrubar a lista inteira (foi o que o AVISO fazia)
                  const NIcon = ICONE_NOTIFICACAO[n.tipo] ?? Bell;
                  const marc = marcadas.has(n.id);
                  const abrir = () => {
                    if (selNotif) return;
                    if (!n.lida) notifComoLidas([n.id]);
                    if (!linkDaPlataforma(n.link)) return;
                    setNotifOpen(false);
                    router.push(n.link!);
                  };
                  return (
                    <div key={n.id} className={`${styles.notifItem} ${!n.lida ? styles.unread : ""} ${marc ? styles.marcada : ""}`}
                      onClick={abrir} style={!selNotif && linkDaPlataforma(n.link) ? { cursor: "pointer" } : undefined}>
                      {selNotif && (
                        <button className={`${styles.radar} ${marc ? styles.radarOn : ""}`} onClick={() => toggleMarca(n.id)}
                          title={marc ? "Desmarcar" : "Marcar"} aria-label={marc ? "Desmarcar" : "Marcar"}>
                          <Circle size={22} />
                          {marc && <Check size={14} className={styles.radarTick} />}
                        </button>
                      )}
                      <div className={styles.notifIconWrap}><NIcon size={14} /></div>
                      <div className={styles.notifBody}>
                        <span className={styles.notifText}>{n.texto}</span>
                        <span className={styles.notifTime}>{quando(n.criadaEm)}</span>
                      </div>
                      {!selNotif && (n.lida ? (
                        <button className={`${styles.lidaBtn} ${styles.naoLidaBtn} ${styles.notifLidaBtn}`} title="Marcar como não lida"
                          onClick={e => { e.stopPropagation(); notifComoNaoLidas([n.id]); }}>
                          <Mail size={13} />
                        </button>
                      ) : (
                        <button className={`${styles.lidaBtn} ${styles.notifLidaBtn}`} title="Marcar como lida"
                          onClick={e => { e.stopPropagation(); notifComoLidas([n.id]); }}>
                          <span className={styles.lidaDot} aria-hidden />
                          <MailOpen size={13} />
                        </button>
                      ))}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Avatar / dropdown */}
          <div ref={menuRef} className={styles.avatarWrap}>
            <button
              className={styles.avatarBtn}
              onClick={() => { setMenuOpen(v => !v); setNotifOpen(false); }}
            >
              <div className={styles.avatarContainer}>
                <div className={styles.avatar} style={{ background: cor }}>{myInitials}</div>
                <span className={styles.onlineDot} data-status={statusAtual}
                  style={{ background: infoStatus(statusAtual).cor }} title={infoStatus(statusAtual).label} />
              </div>
            </button>

            {menuOpen && (
              <div className={styles.dropdown}>
                <div className={styles.dropUser}>
                  <div className={styles.dropAvatar} style={{ background: cor }}>{myInitials}</div>
                  <div className={styles.dropName}>{currentUser.nome}</div>
                  <div className={styles.dropPapel}>{currentUser.papelDescricao}</div>
                  <div className={styles.dropEmail}>{currentUser.email}</div>
                  <div className={styles.dropStatus}><StatusPicker /></div>
                </div>
                <div className={styles.dropDivider} />
                {MENU_ITEMS.map(({ label, icon: Icon, href }) => (
                  <button key={label} className={styles.dropItem}
                    onClick={() => { setMenuOpen(false); if (href) router.push(href); }}>
                    <Icon size={14} />{label}
                  </button>
                ))}
                {currentUser.papel === "SUPORTE_TI" && (
                  <button className={styles.dropItem}
                    onClick={() => { setMenuOpen(false); window.open(PAINEL_DE_OPERACAO, "_blank", "noopener"); }}>
                    <Activity size={14} />Painel de operação
                  </button>
                )}
                <button className={styles.dropItem}
                  onClick={() => { setMenuOpen(false); window.dispatchEvent(new Event(ABRIR_PREFERENCIAS)); }}>
                  <Cookie size={14} />Privacidade e cookies
                </button>
                <div className={styles.dropDivider} />
                <button className={styles.dropSair} onClick={signOut}>
                  <LogOut size={14} /> Sair
                </button>
              </div>
            )}
          </div>

        </div>
      </div>

      {confirmar && createPortal(
        <div className={styles.confirmOverlay} onMouseDown={() => setConfirmar(null)}>
          <div className={styles.confirmBox} onMouseDown={e => e.stopPropagation()}>
            <div className={styles.confirmIco}><Trash2 size={20} /></div>
            <p className={styles.confirmTxt}>{confirmar.texto}</p>
            <span className={styles.confirmSub}>Esta ação não pode ser desfeita.</span>
            <div className={styles.confirmActions}>
              <button className={styles.confirmCancel} onClick={() => setConfirmar(null)}>Cancelar</button>
              <button className={styles.confirmDel} onClick={() => { confirmar.acao(); setConfirmar(null); }}>Excluir</button>
            </div>
          </div>
        </div>,
        document.body
      )}
    </header>
  );
}
