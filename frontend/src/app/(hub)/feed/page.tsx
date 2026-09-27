"use client";

import { Suspense, useState, useEffect, useCallback, SetStateAction } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import { Inbox, X, TriangleAlert, RotateCw } from "lucide-react";
import { Post, PostRequest } from "@/types";
import { postService } from "@/services/postService";
import { useUsuarioLogado } from "@/hooks/useCurrentUser";
import LeftPanel from "@/components/layout/LeftPanel";
import RightPanel from "@/components/layout/RightPanel";
import PostCard from "@/components/feed/PostCard";
import PostForm from "@/components/feed/PostForm";
import PostTimeline from "@/components/feed/PostTimeline";
import SaudacaoBanner from "@/components/feed/SaudacaoBanner";
import AviaoPapel from "@/components/ui/AviaoPapel";
import styles from "./page.module.css";
import { casaComBusca } from "@/utils/format";

// Cache de módulo: ao voltar para o feed, a lista da última visita aparece na
// hora — sem skeleton — enquanto uma busca silenciosa a atualiza por trás.
let cachePosts: Post[] | null = null;

/* ── Rastro do avião ────────────────────────────────────────────────────
   Os traços de fumaça ficam PARADOS sobre a rota (offset-distance fixo) e
   precisam acender no instante exato em que o avião passa por eles. Como o
   voo usa cubic-bezier(.42,.05,.3,1), o tempo de chegada a cada ponto não é
   proporcional à distância: invertemos a curva para achá-lo. */
const VOO_MS = 1950;
const CB = { x1: 0.42, y1: 0.05, x2: 0.3, y2: 1 };
const bezier = (a: number, b: number, s: number) =>
  3 * (1 - s) * (1 - s) * s * a + 3 * (1 - s) * s * s * b + s * s * s;

/** instante (0–1) em que o voo alcança a fração `p` do trajeto */
function tempoNoTrajeto(p: number) {
  let lo = 0, hi = 1;
  for (let i = 0; i < 26; i++) {
    const m = (lo + hi) / 2;
    if (bezier(CB.y1, CB.y2, m) < p) lo = m; else hi = m;
  }
  return bezier(CB.x1, CB.x2, (lo + hi) / 2);
}

/* O rastro imita o rastro de condensação de um jato: trechos parados sobre a
   rota, cada um "condensando" quando o avião passa e depois se abrindo e
   evaporando. A largura deixa uma folga mínima entre eles, então a linha se
   forma pontilhada — e some na mesma ordem em que apareceu. */
const MARCAS = 24;                             // ~36px entre marcas na rota
const RASTRO = Array.from({ length: MARCAS }, (_, i) => {
  const p = 0.02 + (i / (MARCAS - 1)) * 0.97;
  return {
    p,
    atraso: (tempoNoTrajeto(p) * VOO_MS) / 1000,
    w: 15,                                     // traço bem menor que o vão
    h: 5.2 - p * 2.4,
    op: 0.62 - p * 0.3,
  };
});


function FeedContent() {
  const router = useRouter();
  const currentUser = useUsuarioLogado();
  const [query, setQuery] = useState("");
  const [posts, setPostsState] = useState<Post[]>(cachePosts ?? []);
  const [loading, setLoading] = useState(cachePosts === null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingPost, setEditingPost] = useState<Post | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [launching, setLaunching] = useState(false);
  const [flyer, setFlyer] = useState<{ x: number; y: number } | null>(null);
  const [soMac, setSoMac] = useState(false);
  // teclas seguradas → efeito de "afundar" no hint (igual ao ⌘K da busca)
  const [teclas, setTeclas] = useState({ mod: false, enter: false });

  // ao publicar: o aviãozinho decola a partir do botão e então o editor abre.
  // `lancar` recebe o elemento do avião (do clique ou buscado no DOM p/ atalho)
  const lancar = (planeEl: Element | null) => {
    if (launching || showForm) return;
    const r = planeEl?.getBoundingClientRect();
    if (r) setFlyer({ x: r.left + r.width / 2, y: r.top + r.height / 2 });
    setLaunching(true);
    // editor abre BEM cedo (o avião ainda está no início do voo) → durante o
    // mergulho o avião cruza por cima da tela que aparece e some nela; o flyer
    // fica até o fim da animação (0.8s)
    setTimeout(() => { setEditingPost(null); setShowForm(true); setLaunching(false); }, 190);
    // o avião some em ~1,95s; o portal fica até o último trecho do rastro
    // terminar de se apagar sobre a trajetória
    setTimeout(() => setFlyer(null), 3000);
  };
  const abrirEditor = (e: React.MouseEvent<HTMLDivElement>) =>
    lancar(e.currentTarget.querySelector("[data-plane]"));

  // detecta o SO só para exibir ⌘ (mac) ou Ctrl no hint do atalho
  useEffect(() => { setSoMac(/mac|iphone|ipad/i.test(navigator.userAgent)); }, []);

  // atalho de teclado p/ abrir o editor — ⌘/Ctrl + Enter (o "comando" da barra
  // de publicação, no espírito do ⌘K da busca). Não dispara se o foco estiver
  // num campo de texto (para não atropelar o Enter de envio de comentários etc.)
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
        const t = (document.activeElement?.tagName || "");
        if (t === "INPUT" || t === "TEXTAREA") return;
        if (showForm || launching) return;
        e.preventDefault();
        lancar(document.querySelector("[data-plane]"));
      }
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [showForm, launching]);

  // acende/afunda cada tecla do hint enquanto está pressionada (Ctrl/⌘ e Enter)
  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === "Control" || e.key === "Meta") setTeclas(t => ({ ...t, mod: true }));
      if (e.key === "Enter") setTeclas(t => ({ ...t, enter: true }));
    };
    const up = (e: KeyboardEvent) => {
      if (e.key === "Control" || e.key === "Meta") setTeclas(t => ({ ...t, mod: false }));
      if (e.key === "Enter") setTeclas(t => ({ ...t, enter: false }));
    };
    const reset = () => setTeclas({ mod: false, enter: false });
    window.addEventListener("keydown", down);
    window.addEventListener("keyup", up);
    window.addEventListener("blur", reset);
    return () => {
      window.removeEventListener("keydown", down);
      window.removeEventListener("keyup", up);
      window.removeEventListener("blur", reset);
    };
  }, []);

  const setPosts = useCallback((updater: SetStateAction<Post[]>) => {
    setPostsState(prev => {
      const next = typeof updater === "function" ? updater(prev) : updater;
      cachePosts = next;
      return next;
    });
  }, []);

  useEffect(() => {
    // lê query da URL no mount (deep link)
    const params = new URLSearchParams(window.location.search);
    setQuery(params.get("q")?.toLowerCase().trim() ?? "");
    // escuta evento de busca do Header
    const handler = (e: Event) => setQuery((e as CustomEvent<{query:string}>).detail.query.toLowerCase().trim());
    window.addEventListener("infnet:search", handler);
    return () => window.removeEventListener("infnet:search", handler);
  }, []);

  const buscarPosts = useCallback(async (silencioso = false) => {
    try {
      if (!silencioso) { setLoading(true); setError(null); }
      setPosts(await postService.listarTodos());
      setError(null);
    } catch {
      // na atualização silenciosa a lista em cache continua válida na tela
      if (!silencioso) setError("Não foi possível carregar o feed. Verifique se o back-end está rodando.");
    } finally { setLoading(false); }
  }, [setPosts]);

  useEffect(() => { buscarPosts(cachePosts !== null); }, [buscarPosts]);

  const handleSalvar = async (dados: PostRequest) => {
    setEnviando(true);
    try {
      if (editingPost) {
        const atualizado = await postService.atualizar(editingPost.id, dados);
        setPosts(prev => prev.map(p => p.id === editingPost.id ? atualizado : p));
      } else {
        await postService.criar(dados);
        buscarPosts(true);
      }
      setShowForm(false); setEditingPost(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Erro ao salvar publicação");
    } finally { setEnviando(false); }
  };

  const handleDeletar = async (id: number) => {
    if (!confirm("Deseja remover esta publicação?")) return;
    try {
      await postService.deletar(id);
      setPosts(prev => prev.filter(p => p.id !== id));
    } catch (err) { alert(err instanceof Error ? err.message : "Erro ao remover"); }
  };

  const filteredPosts = query
    ? posts.filter(p =>
        casaComBusca(query, p.conteudo, p.titulo, p.autorNome)
      )
    : posts;

  return (
    <div className={styles.body}>

      <LeftPanel />

      {!loading && filteredPosts.length > 0 && <PostTimeline posts={filteredPosts} />}

      <main className={styles.feed}>
        {!showForm ? (
          <div className={`${styles.composeShell} ${launching ? styles.launching : ""}`}>
            <div className={styles.composeBox} onClick={abrirEditor}>
              <div className={styles.composePlaceholder}>
                Compartilhe uma ideia com a comunidade...
              </div>
              <span className={styles.composeKbd} aria-hidden>
                <kbd className={teclas.mod ? styles.kbdPress : ""}>{soMac ? "⌘" : "Ctrl"}</kbd>
                <kbd className={`${styles.kbdEnter} ${teclas.enter ? styles.kbdPress : ""}`}>↵</kbd>
              </span>
              <button className={styles.btnPublicar} title={`Publicar  (${soMac ? "⌘" : "Ctrl"} + Enter)`} aria-label="Publicar" data-plane>
                <AviaoPapel className={styles.btnAviao} size={19} />
              </button>
            </div>
          </div>
        ) : (
          <PostForm
            currentUser={currentUser}
            postParaEditar={editingPost}
            enviando={enviando}
            onPostar={handleSalvar}
            onCancelar={() => { setShowForm(false); setEditingPost(null); }}
          />
        )}

        {!query && <SaudacaoBanner nome={currentUser.nome} />}

        {error && (
          <div className={styles.error}>
            <span className={styles.errorIco}><TriangleAlert size={22} /></span>
            <span className={styles.errorMsg}>{error}</span>
            <button onClick={() => buscarPosts()}><RotateCw size={13} /> Tentar novamente</button>
          </div>
        )}

        {query && (
          <div className={styles.searchBanner}>
            <span>Resultados para <strong>&ldquo;{query}&rdquo;</strong> — {filteredPosts.length} encontrado{filteredPosts.length !== 1 ? "s" : ""}</span>
            <button className={styles.clearSearch} onClick={() => { setQuery(""); router.push("/feed"); }}><X size={13} /> Limpar</button>
          </div>
        )}

        {loading ? (
          <div className={styles.skeletons}>
            {[...Array(3)].map((_, i) => <div key={i} className={styles.skeleton} />)}
          </div>
        ) : filteredPosts.length === 0 ? (
          <div className={styles.empty}>
            <Inbox size={48} className={styles.emptyIcon} />
            <p>{query ? `Nenhum resultado para "${query}".` : "Nenhuma publicação ainda. Seja o primeiro!"}</p>
          </div>
        ) : (
          filteredPosts.map(post => (
            <div key={post.id} id={`post-${post.id}`} style={{ scrollMarginTop: "calc(var(--header-h) + 1rem)" }}>
              <PostCard
                post={post}
                currentUser={currentUser}
                onEditar={() => { setEditingPost(post); setShowForm(true); }}
                onDeletar={() => handleDeletar(post.id)}
                onPostUpdated={updated => setPosts(prev => prev.map(p => p.id === updated.id ? updated : p))}
              />
            </div>
          ))
        )}
      </main>

      <RightPanel />

      {flyer && createPortal(
        <div className={styles.flyer} style={{ left: flyer.x, top: flyer.y }} aria-hidden>
          {/* feixe do rastro: segmentos emendados sobre a rota, acendendo na
              passagem do avião e afinando até apagar */}
          {RASTRO.map(f => (
            <i key={f.p} className={styles.traco} style={{
              offsetDistance: `${(f.p * 100).toFixed(2)}%`,
              // 1º atraso: o instante em que o avião passa por este ponto.
              // 2º: fixo, quando todas as marcas se apagam juntas no fim do voo
              animationDelay: `${f.atraso.toFixed(3)}s, 2.15s`,
              ["--fw" as string]: `${f.w.toFixed(1)}px`,
              ["--fh" as string]: `${f.h.toFixed(1)}px`,
              ["--fop" as string]: f.op.toFixed(2),
            }} />
          ))}
          {/* a dobradura + dois ecos de contorno logo atrás (repetição do traço) */}
          {/* um par de pássaros cruza o campo enquanto o avião passa — só a
              silhueta, bem discreta, para dar escala ao voo */}
          {[{ d: ".18s", t: "-42px, -30px", e: 1 }, { d: ".34s", t: "26px, -54px", e: .78 }].map(p => (
            <svg key={p.d} className={styles.passaro} viewBox="0 0 24 10" width={18 * p.e} height={7.5 * p.e}
              fill="none" style={{ animationDelay: p.d, ["--pt" as string]: p.t }}>
              <path d="M1 6 Q6 0 11 5.5 Q16 0 23 5" stroke="currentColor" strokeWidth="1.5"
                strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          ))}

          <AviaoPapel className={styles.flyerGhost} size={34} horizontal style={{ animationDelay: ".07s" }} />
          <AviaoPapel className={styles.flyerGhost} size={34} horizontal style={{ animationDelay: ".16s" }} />
          <AviaoPapel className={styles.flyerPlane} size={34} horizontal />
        </div>,
        document.body
      )}
    </div>
  );
}

export default function FeedPage() {
  return (
    <Suspense>
      <FeedContent />
    </Suspense>
  );
}
