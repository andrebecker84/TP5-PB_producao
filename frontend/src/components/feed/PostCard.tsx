"use client";

import { useState, useEffect, useRef, FormEvent, KeyboardEvent } from "react";
import { ThumbsUp, MessageCircle, Share2, Pencil, Trash2, X, ChevronDown, ChevronUp, Eye, EyeOff, MoreVertical, Send, Image as ImageIcon, Film, Smile, Code2, CornerDownRight } from "lucide-react";
import { Post, Usuario, Comentario } from "@/types";
import { postService } from "@/services/postService";
import { showToast } from "@/utils/toast";
import { initials, relativo, EMOJIS } from "@/utils/format";
import { CORES, PAPEL_TXT } from "@/utils/colors";
import GifPicker from "./GifPicker";
import styles from "./PostCard.module.css";

interface Props {
  post: Post;
  currentUser: Usuario;
  onEditar: () => void;
  onDeletar: () => void;
  onPostUpdated: (p: Post) => void;
}

function renderWithCode(text: string) {
  const parts = text.split(/(`[^`]+`)/g);
  return parts.map((part, i) =>
    part.startsWith("`") && part.endsWith("`") && part.length > 2
      ? <code key={i} className={styles.inlineCode}>{part.slice(1, -1)}</code>
      : <span key={i}>{part}</span>
  );
}

interface TextoExpansivelProps {
  texto: string;
  className: string;
  compacto?: boolean;
}

/**
 * Texto recolhido em 2 linhas com "Ler mais"/"Ver menos".
 *
 * O botão só aparece quando o texto de fato transborda o corte — medido no
 * elemento renderizado (scrollHeight vs clientHeight), e não por heurística de
 * contagem de caracteres, que erra quando linhas longas quebram no wrap.
 * O ResizeObserver refaz a medição quando a largura da coluna muda.
 */
function TextoExpansivel({ texto, className, compacto = false }: TextoExpansivelProps) {
  const [expandido,  setExpandido]  = useState(false);
  const [transborda, setTransborda] = useState(false);
  const ref = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el || expandido) return;   // só se mede no estado recolhido
    const medir = () => setTransborda(el.scrollHeight > el.clientHeight + 1);
    medir();
    const observer = new ResizeObserver(medir);
    observer.observe(el);
    return () => observer.disconnect();
  }, [texto, expandido]);

  const tamanhoIcone = compacto ? 11 : 13;

  return (
    <>
      <p ref={ref} className={`${className} ${!expandido ? styles.truncated : ""}`}>
        {renderWithCode(texto)}
      </p>
      {(transborda || expandido) && (
        <div className={styles.lerMaisRow}>
          <button
            className={`${styles.lerMais} ${compacto ? styles.lerMaisCompacto : ""}`}
            onClick={() => setExpandido(v => !v)}
            title={expandido ? "Ver menos" : "Ler mais"}
            aria-label={expandido ? "Ver menos" : "Ler mais"}
          >
            {expandido
              ? <ChevronUp size={tamanhoIcone + 1} />
              : <MoreVertical size={tamanhoIcone + 2} />}
          </button>
        </div>
      )}
    </>
  );
}

export default function PostCard({ post, currentUser, onEditar, onDeletar, onPostUpdated }: Props) {
  const cor      = CORES[post.autorId % CORES.length];
  const isAuthor = post.autorId === currentUser.id;

  const [totalCurtidas,  setTotal]         = useState(post.curtidas);
  const [curtidoPorMim,  setCurtidoPorMim] = useState(false);
  const [quemCurtiu,     setQuemCurtiu]    = useState<{ usuarioId: number; usuarioNome: string }[]>([]);
  const [showQuem,       setShowQuem]      = useState(false);
  const [curtindo,       setCurtindo]      = useState(false);
  const [showComents,    setShowComents]   = useState(false);
  const [comentarios,    setComentarios]   = useState<Comentario[]>([]);
  /* Nasce `true`: os comentários são buscados já na montagem do card, e o
     estado precisa refletir isso desde o primeiro quadro. Iniciando em `false`,
     a tela mostrava "Nenhum comentário ainda" no intervalo entre abrir a lista
     e a resposta chegar — dizendo que não havia comentários quando ainda não se
     sabia. Reiniciar o estado a cada post não é necessário: o feed monta um
     PostCard por publicação (`key={post.id}`), então trocar de post cria uma
     instância nova, com o estado inicial de volta. */
  const [loadingComents, setLoadingC]      = useState(true);
  const [novoComent,     setNovoComent]    = useState("");
  const [enviandoC,      setEnviandoC]     = useState(false);
  const [editingCId,     setEditingCId]    = useState<number | null>(null);
  const [editText,       setEditText]      = useState("");
  const [showEmoji,      setShowEmoji]     = useState(false);
  const [showGif,        setShowGif]       = useState(false);
  const [showAll,        setShowAll]       = useState(false);
  const cImgInputRef = useRef<HTMLInputElement>(null);
  const gifRef       = useRef<HTMLDivElement>(null);
  const [listCollapsed,  setListCollapsed] = useState(false);
  const [shares,         setShares]        = useState(() => (post.id * 3) % 7);
  const [compartilhado,  setCompartilhado] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const emojiRef    = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!showEmoji) return;
    const handler = (e: MouseEvent) => {
      if (emojiRef.current && !emojiRef.current.contains(e.target as Node)) setShowEmoji(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [showEmoji]);

  const insertAtCursor = (text: string) => {
    const ta = textareaRef.current;
    if (!ta) return;
    const start = ta.selectionStart;
    setNovoComent(v => v.slice(0, start) + text + v.slice(start));
    setTimeout(() => { ta.selectionStart = ta.selectionEnd = start + text.length; ta.focus(); }, 0);
  };

  // abre o campo de comentário (opcionalmente respondendo a alguém) e foca
  const abrirComentario = (mencao?: string) => {
    setShowComents(true);
    if (mencao) setNovoComent(v => (v.startsWith(`@${mencao}`) ? v : `@${mencao} ${v}`));
    setTimeout(() => {
      textareaRef.current?.focus();
      textareaRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    }, 80);
  };

  useEffect(() => {
    if (!showGif) return;
    const h = (e: MouseEvent) => { if (gifRef.current && !gifRef.current.contains(e.target as Node)) setShowGif(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, [showGif]);

  const handleCodeWrap = () => {
    const ta = textareaRef.current;
    if (!ta) return;
    const { selectionStart: s, selectionEnd: e } = ta;
    if (s !== e) {
      setNovoComent(v => v.slice(0, s) + "`" + v.slice(s, e) + "`" + v.slice(e));
      setTimeout(() => { ta.selectionStart = s + 1; ta.selectionEnd = e + 1; ta.focus(); }, 0);
    } else {
      setNovoComent(v => v.slice(0, s) + "``" + v.slice(s));
      setTimeout(() => { ta.selectionStart = ta.selectionEnd = s + 1; ta.focus(); }, 0);
    }
  };

  useEffect(() => {
    postService.listarCurtidas(post.id).then(lista => {
      setQuemCurtiu(lista);
      setTotal(lista.length);
      setCurtidoPorMim(lista.some(c => c.usuarioId === currentUser.id));
    }).catch(() => {});

    // O `finally` é o que encerra o estado de carregamento — inclusive quando a
    // busca falha. Sem ele o card ficaria preso em "Carregando..." para sempre.
    postService.listarComentarios(post.id)
      .then(setComentarios)
      .catch(() => {})
      .finally(() => setLoadingC(false));
  }, [post.id, currentUser.id]);

  const handleToggleCurtir = async () => {
    if (curtindo) return;
    setCurtindo(true);
    const era = curtidoPorMim;
    setCurtidoPorMim(!era);
    setTotal(v => era ? v - 1 : v + 1);
    try {
      const res = await postService.toggleCurtir(post.id);
      setCurtidoPorMim(res.curtido);
      setTotal(Number(res.total));
      const lista = await postService.listarCurtidas(post.id);
      setQuemCurtiu(lista);
      onPostUpdated({ ...post, curtidas: Number(res.total) });
      showToast(res.curtido ? "Publicação curtida!" : "Curtida removida", res.curtido ? "success" : "info");
    } catch {
      setCurtidoPorMim(era);
      setTotal(v => era ? v + 1 : v - 1);
      showToast("Erro ao curtir. Tente novamente.", "error");
    } finally { setCurtindo(false); }
  };

  const toggleComentarios = () => {
    const opening = !showComents;
    setShowComents(opening);
    if (opening) {
      setTimeout(() => {
        textareaRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
        textareaRef.current?.focus();
      }, 80);
    }
  };

  const handleEnviarComent = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!novoComent.trim() || enviandoC) return;
    setEnviandoC(true);
    try {
      const novo = await postService.criarComentario(post.id, novoComent.trim());
      setComentarios(prev => [...prev, novo]);
      setNovoComent("");
      showToast("Comentário publicado!", "success");
    } catch {
      showToast("Erro ao publicar comentário.", "error");
    } finally { setEnviandoC(false); }
  };

  const handleCommentKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleEnviarComent();
    }
    if (e.key === "Escape") {
      e.preventDefault();
      setShowComents(false);
      (e.target as HTMLTextAreaElement).blur();
    }
  };

  const handleSalvarEdit = async (c: Comentario) => {
    if (!editText.trim()) return;
    const upd = await postService.editarComentario(post.id, c.id, editText.trim()).catch(() => null);
    if (upd) {
      setComentarios(prev => prev.map(x => x.id === c.id ? upd : x));
      setEditingCId(null);
      showToast("Comentário atualizado.", "info");
    }
  };

  const handleDeletarComent = async (id: number) => {
    if (!confirm("Remover comentário?")) return;
    await postService.deletarComentario(post.id, id).catch(() => {});
    setComentarios(prev => prev.filter(c => c.id !== id));
    showToast("Comentário removido.", "info");
  };

  const handleCompartilhar = () => {
    if (compartilhado) return;
    setCompartilhado(true);
    setShares(v => v + 1);
    if (navigator.clipboard) navigator.clipboard.writeText(`${location.origin}/feed#post-${post.id}`).catch(() => {});
    showToast("Link da publicação copiado!", "success");
  };

  const totalComents = comentarios.length || post.totalComentarios || 0;

  return (
    <article className={styles.card}>

      {/* ── Header ── */}
      <div className={styles.header}>
        <div className={styles.avatar} style={{ background: cor }}>{initials(post.autorNome)}</div>
        <div className={styles.meta}>
          <div className={styles.authorLine}>
            <span className={styles.autor}>{post.autorNome}</span>
            <span
              className={styles.papel}
              style={{ borderColor: PAPEL_TXT[post.autorPapel], color: PAPEL_TXT[post.autorPapel] }}
            >
              {post.autorPapelDescricao}
            </span>
          </div>
          <span className={styles.tempo}>{relativo(post.criadoEm)}</span>
        </div>
        {isAuthor && (
          <div className={styles.actions}>
            <button className={`${styles.iconBtn} ${styles.editBtn}`} onClick={onEditar} title="Editar">
              <Pencil size={13} />
            </button>
            <button className={`${styles.iconBtn} ${styles.delBtn}`} onClick={onDeletar} title="Remover">
              <Trash2 size={13} />
            </button>
          </div>
        )}
      </div>

      {/* ── Conteúdo ── */}
      <div className={styles.body}>
        {post.titulo && <h3 className={styles.titulo}>{post.titulo}</h3>}
        <TextoExpansivel texto={post.conteudo} className={styles.conteudo} />
      </div>

      {/* ── Capa ── */}
      {post.imagemUrl && (
        <figure className={styles.capa}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={post.imagemUrl} alt={post.titulo ?? "Imagem do post"} loading="lazy" />
        </figure>
      )}

      {/* ── Engajamento: reações · comentar · compartilhar (contadores à esquerda,
             avatares de quem curtiu à direita) ── */}
      <div className={styles.engage}>
        <button
          className={`${styles.engBtn} ${curtidoPorMim ? styles.liked : ""}`}
          onClick={handleToggleCurtir}
          disabled={curtindo}
          title={curtidoPorMim ? "Remover curtida" : "Curtir"}
        >
          <ThumbsUp size={16} fill={curtidoPorMim ? "currentColor" : "none"} />
          {totalCurtidas > 0 && <span className={styles.engNum}>{totalCurtidas}</span>}
        </button>
        <button
          className={`${styles.engBtn} ${showComents ? styles.commentActive : ""}`}
          onClick={toggleComentarios}
          title="Comentar"
        >
          <MessageCircle size={16} />
          {totalComents > 0 && <span className={styles.engNum}>{totalComents}</span>}
        </button>
        <button
          className={`${styles.engBtn} ${compartilhado ? styles.shared : ""}`}
          onClick={handleCompartilhar}
          title="Compartilhar"
        >
          <Share2 size={15} />
          {shares > 0 && <span className={styles.engNum}>{shares}</span>}
        </button>

        {quemCurtiu.length > 0 && (
          <div className={styles.reacoes}>
            <button
              className={styles.avatarStack}
              onClick={() => setShowQuem(v => !v)}
              onMouseEnter={() => setShowQuem(true)}
              onMouseLeave={() => setShowQuem(false)}
              title="Quem curtiu"
              aria-label="Ver quem curtiu"
            >
              {quemCurtiu.slice(0, 3).map(c => (
                <span key={c.usuarioId} className={styles.stackAv}
                  style={{ background: CORES[c.usuarioId % CORES.length] }}>
                  {initials(c.usuarioNome)}
                </span>
              ))}
              {quemCurtiu.length > 3 && <span className={styles.stackMais}>+{quemCurtiu.length - 3}</span>}
            </button>
            {showQuem && (
              <div className={styles.quemPop} role="tooltip">
                <span className={styles.quemPopTit}>Curtiram esta publicação</span>
                {quemCurtiu.map(c => (
                  <span key={c.usuarioId} className={styles.quemPopItem}>
                    <span className={styles.quemPopAv} style={{ background: CORES[c.usuarioId % CORES.length] }}>
                      {initials(c.usuarioNome)}
                    </span>
                    {c.usuarioNome}
                  </span>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Comentários ── */}
      {(comentarios.length > 0 || showComents) && (
        <div className={styles.comentSection}>
          <div className={styles.comentHeader}>
            <MessageCircle size={13} /> Comentários
            {comentarios.length > 0 && (
              <button
                className={styles.toggleComents}
                onClick={() => setListCollapsed(v => !v)}
                title={listCollapsed ? "Revelar" : "Ocultar"}
                aria-label={listCollapsed ? "Revelar comentários" : "Ocultar comentários"}
              >
                {listCollapsed ? <Eye size={15} /> : <EyeOff size={15} />}
              </button>
            )}
          </div>

          {!listCollapsed && loadingComents && (
            <p className={styles.info}>Carregando...</p>
          )}
          {!listCollapsed && !loadingComents && comentarios.length === 0 && (
            <p className={styles.info}>Nenhum comentário ainda.</p>
          )}
          {!listCollapsed && !loadingComents && comentarios.length > 0 && (
            <div className={styles.comentList}>
              {(showAll ? comentarios : comentarios.slice(0, 5)).map(c => (
                <div key={c.id} className={styles.comentItem}>
                  <div className={styles.cAv} style={{ background: CORES[c.autorId % CORES.length] }}>
                    {initials(c.autorNome)}
                  </div>
                  <div className={styles.cBody}>
                    <div className={styles.cMeta}>
                      <span className={styles.cAutor}>{c.autorNome}</span>
                      {c.autorPapel && (
                        <span className={styles.cPapel} style={{ borderColor: PAPEL_TXT[c.autorPapel], color: PAPEL_TXT[c.autorPapel] }}>
                          {c.autorPapelDescricao}
                        </span>
                      )}
                      <span className={styles.cTempo}>{relativo(c.criadoEm)}</span>
                      {c.autorId === currentUser.id && editingCId !== c.id && (
                        <div className={styles.cActions}>
                          <button className={styles.cIconBtn} onClick={() => { setEditingCId(c.id); setEditText(c.conteudo); }}>
                            <Pencil size={11} />
                          </button>
                          <button className={`${styles.cIconBtn} ${styles.cDel}`} onClick={() => handleDeletarComent(c.id)}>
                            <X size={11} />
                          </button>
                        </div>
                      )}
                    </div>
                    {editingCId === c.id ? (
                      <div className={styles.editRow}>
                        <input
                          className={styles.editInput}
                          value={editText}
                          onChange={e => setEditText(e.target.value)}
                          onKeyDown={e => {
                            if (e.key === "Enter") { e.preventDefault(); handleSalvarEdit(c); }
                            if (e.key === "Escape") { e.preventDefault(); setEditingCId(null); }
                          }}
                          autoFocus
                        />
                        <button className={styles.btnSave} onClick={() => handleSalvarEdit(c)}>Salvar</button>
                        <button className={styles.btnCnl} onClick={() => setEditingCId(null)}><X size={12} /></button>
                      </div>
                    ) : (
                      <>
                        <TextoExpansivel texto={c.conteudo} className={styles.cText} compacto />
                        <button className={styles.cReply} onClick={() => abrirComentario(c.autorNome.split(" ")[0])}>
                          <CornerDownRight size={11} /> Responder
                        </button>
                      </>
                    )}
                  </div>
                </div>
              ))}
              {!showAll && comentarios.length > 5 && (
                <button className={styles.verMais} onClick={() => setShowAll(true)}>
                  <ChevronDown size={14} /> Ver mais {comentarios.length - 5} comentário{comentarios.length - 5 !== 1 ? "s" : ""}
                </button>
              )}
              {showAll && comentarios.length > 5 && (
                <button className={styles.verMais} onClick={() => setShowAll(false)}>
                  <ChevronUp size={14} /> Ver menos
                </button>
              )}
            </div>
          )}

          {/* CTA de comentar após a lista (quando o campo está fechado) */}
          {!listCollapsed && !showComents && (
            <button className={styles.comentarCta} onClick={() => abrirComentario()}>
              <MessageCircle size={14} /> Comentar
            </button>
          )}

          {/* ── Barra de novo comentário ── */}
          {showComents && (
            <div className={styles.novoComentArea}>
              <div className={styles.cAv} style={{ background: CORES[currentUser.id % CORES.length] }}>
                {initials(currentUser.nome)}
              </div>
              <div className={styles.novoComentBox}>
                <textarea
                  ref={textareaRef}
                  className={styles.novoInput}
                  placeholder="Escreva um comentário..."
                  value={novoComent}
                  onChange={e => setNovoComent(e.target.value)}
                  onKeyDown={handleCommentKeyDown}
                  disabled={enviandoC}
                  rows={2}
                />
                <div className={styles.novoToolbar}>
                  <div className={styles.novoTools}>
                    <input ref={cImgInputRef} type="file" accept="image/*" hidden
                      onChange={e => { const f = e.target.files?.[0]; if (f) insertAtCursor(`[imagem: ${f.name}] `); e.target.value = ""; }} />
                    <button className={styles.toolBtn} type="button" title="Anexar imagem" onClick={() => cImgInputRef.current?.click()}>
                      <ImageIcon size={14} />
                    </button>
                    <div ref={gifRef} className={styles.emojiWrap}>
                      <button className={styles.toolBtn} type="button" title="Adicionar GIF" onClick={() => setShowGif(v => !v)}>
                        <Film size={14} />
                      </button>
                      {showGif && (
                        <GifPicker onSelect={url => { insertAtCursor(`${url} `); setShowGif(false); }} />
                      )}
                    </div>
                    <div ref={emojiRef} className={styles.emojiWrap}>
                      <button className={styles.toolBtn} type="button" title="Emoji" onClick={() => setShowEmoji(v => !v)}>
                        <Smile size={14} />
                      </button>
                      {showEmoji && (
                        <div className={styles.emojiPicker}>
                          {EMOJIS.map(e => (
                            <button key={e} className={styles.emojiBtn} type="button"
                              onClick={() => { insertAtCursor(e); setShowEmoji(false); }}>
                              {e}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                    <button className={styles.toolBtn} type="button" title="Formatar código" onClick={handleCodeWrap}>
                      <Code2 size={14} />
                    </button>
                  </div>
                  <div className={styles.novoHints}>
                    <span><kbd>Enter</kbd> enviar</span>
                    <span><kbd>Shift+Enter</kbd> nova linha</span>
                    <span><kbd>Esc</kbd> cancelar</span>
                  </div>
                  <button
                    className={styles.btnEnviar}
                    type="button"
                    disabled={!novoComent.trim() || enviandoC}
                    onClick={() => handleEnviarComent()}
                  >
                    <Send size={14} />
                  </button>
                </div>
              </div>
            </div>
          )}

        </div>
      )}
    </article>
  );
}
