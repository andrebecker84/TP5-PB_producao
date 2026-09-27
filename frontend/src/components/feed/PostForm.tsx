"use client";

import { useState, useEffect, useRef, FormEvent } from "react";
import { X, Image as ImageIcon, Paperclip, Smile, Code2, Send, UploadCloud, FileText } from "lucide-react";
import { Usuario, Post, PostRequest } from "@/types";
import { initials, EMOJIS } from "@/utils/format";
import { CORES } from "@/utils/colors";
import styles from "./PostForm.module.css";

interface Props {
  currentUser: Usuario;
  postParaEditar: Post | null;
  enviando: boolean;
  onPostar: (dados: PostRequest) => void;
  onCancelar: () => void;
}

interface Anexo { id: string; nome: string; tipo: "imagem" | "documento"; tamanho: number; url?: string; }

const fmtTamanho = (b: number) => b < 1024 ? `${b} B` : b < 1048576 ? `${(b / 1024).toFixed(0)} KB` : `${(b / 1048576).toFixed(1)} MB`;

export default function PostForm({ currentUser, postParaEditar, enviando, onPostar, onCancelar }: Props) {
  const [titulo,    setTitulo]    = useState("");
  const [conteudo,  setConteudo]  = useState("");
  const [showEmoji, setShowEmoji] = useState(false);
  const [anexos,    setAnexos]    = useState<Anexo[]>([]);
  const [showDocModal, setShowDocModal] = useState(false);
  const [dragOver,  setDragOver]  = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const emojiRef    = useRef<HTMLDivElement>(null);
  const imgInputRef = useRef<HTMLInputElement>(null);
  const docInputRef = useRef<HTMLInputElement>(null);

  const cor    = CORES[currentUser.id % CORES.length];
  const isEdit = !!postParaEditar;

  useEffect(() => {
    setTitulo(postParaEditar?.titulo ?? "");
    setConteudo(postParaEditar?.conteudo ?? "");
  }, [postParaEditar]);

  useEffect(() => {
    if (!showEmoji) return;
    const handler = (e: MouseEvent) => {
      if (emojiRef.current && !emojiRef.current.contains(e.target as Node)) setShowEmoji(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [showEmoji]);

  // libera as object URLs das imagens ao desmontar
  useEffect(() => () => { anexos.forEach(a => a.url && URL.revokeObjectURL(a.url)); }, [anexos]);

  const insertAtCursor = (text: string) => {
    const ta = textareaRef.current;
    if (!ta) return;
    const start = ta.selectionStart;
    setConteudo(v => v.slice(0, start) + text + v.slice(start));
    setTimeout(() => { ta.selectionStart = ta.selectionEnd = start + text.length; ta.focus(); }, 0);
  };

  const handleCodeWrap = () => {
    const ta = textareaRef.current;
    if (!ta) return;
    const { selectionStart: s, selectionEnd: e } = ta;
    if (s !== e) {
      setConteudo(v => v.slice(0, s) + "`" + v.slice(s, e) + "`" + v.slice(e));
      setTimeout(() => { ta.selectionStart = s + 1; ta.selectionEnd = e + 1; ta.focus(); }, 0);
    } else {
      setConteudo(v => v.slice(0, s) + "``" + v.slice(s));
      setTimeout(() => { ta.selectionStart = ta.selectionEnd = s + 1; ta.focus(); }, 0);
    }
  };

  const addImagens = (files: FileList | null) => {
    if (!files) return;
    const novos: Anexo[] = Array.from(files).filter(f => f.type.startsWith("image/")).map(f => ({
      id: crypto.randomUUID(), nome: f.name, tipo: "imagem", tamanho: f.size, url: URL.createObjectURL(f),
    }));
    setAnexos(a => [...a, ...novos]);
  };
  const addDocumentos = (files: FileList | null) => {
    if (!files) return;
    const novos: Anexo[] = Array.from(files).map(f => ({
      id: crypto.randomUUID(), nome: f.name, tipo: "documento", tamanho: f.size,
    }));
    setAnexos(a => [...a, ...novos]);
    setShowDocModal(false);
  };
  const removeAnexo = (id: string) => setAnexos(a => {
    const alvo = a.find(x => x.id === id);
    if (alvo?.url) URL.revokeObjectURL(alvo.url);
    return a.filter(x => x.id !== id);
  });

  const enviar = () => {
    if (!conteudo.trim() || enviando) return;
    onPostar({ titulo: titulo.trim() || undefined, conteudo: conteudo.trim() });
  };
  const handleSubmit = (e: FormEvent) => { e.preventDefault(); enviar(); };
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); enviar(); }
    if (e.key === "Escape") { e.preventDefault(); onCancelar(); }
  };

  const imagens = anexos.filter(a => a.tipo === "imagem");
  const documentos = anexos.filter(a => a.tipo === "documento");

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <div className={styles.avatar} style={{ background: cor }}>{initials(currentUser.nome)}</div>
        <div className={styles.info}>
          <span className={styles.name}>{currentUser.nome}</span>
          <span className={styles.sub}>{isEdit ? "Editando publicação" : "Publicar para todos"}</span>
        </div>
        <button className={styles.closeBtn} onClick={onCancelar} disabled={enviando}>
          <X size={16} />
        </button>
      </div>

      <form className={styles.form} onSubmit={handleSubmit}>
        <input
          className={styles.tituloInput}
          placeholder="Título (opcional)"
          value={titulo}
          onChange={e => setTitulo(e.target.value)}
          maxLength={200}
          disabled={enviando}
        />

        <div className={styles.inputBox}>
          <textarea
            ref={textareaRef}
            className={styles.conteudo}
            placeholder="O que você está pensando?"
            value={conteudo}
            onChange={e => setConteudo(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={4}
            required
            disabled={enviando}
            autoFocus
          />

          {/* pré-visualização dos anexos */}
          {(imagens.length > 0 || documentos.length > 0) && (
            <div className={styles.anexos}>
              {imagens.map(a => (
                <div key={a.id} className={styles.anexoImg}>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={a.url} alt={a.nome} />
                  <button type="button" className={styles.anexoRem} onClick={() => removeAnexo(a.id)} title="Remover"><X size={12} /></button>
                </div>
              ))}
              {documentos.map(a => (
                <div key={a.id} className={styles.anexoDoc}>
                  <FileText size={15} />
                  <div className={styles.anexoDocInfo}>
                    <span className={styles.anexoDocNome}>{a.nome}</span>
                    <span className={styles.anexoDocTam}>{fmtTamanho(a.tamanho)}</span>
                  </div>
                  <button type="button" className={styles.anexoRem} onClick={() => removeAnexo(a.id)} title="Remover"><X size={12} /></button>
                </div>
              ))}
            </div>
          )}

          {/* inputs de arquivo ocultos */}
          <input ref={imgInputRef} type="file" accept="image/*" multiple hidden onChange={e => { addImagens(e.target.files); e.target.value = ""; }} />
          <input ref={docInputRef} type="file" multiple hidden onChange={e => { addDocumentos(e.target.files); e.target.value = ""; }} />

          <div className={styles.toolbar}>
            <div className={styles.tools}>
              <button className={styles.toolBtn} type="button" title="Anexar imagem" onClick={() => imgInputRef.current?.click()}><ImageIcon size={14} /></button>
              <button className={styles.toolBtn} type="button" title="Anexar documento" onClick={() => setShowDocModal(true)}><Paperclip size={14} /></button>
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

            <div className={styles.hints}>
              <span><kbd>Enter</kbd> enviar</span>
              <span><kbd>Shift+Enter</kbd> nova linha</span>
              <span><kbd>Esc</kbd> cancelar</span>
            </div>

            <button type="submit" className={styles.btnEnviar} disabled={!conteudo.trim() || enviando}>
              <Send size={14} />
            </button>
          </div>
        </div>
      </form>

      {/* janela suspensa para anexar documentos do computador */}
      {showDocModal && (
        <div className={styles.docOverlay} onClick={() => setShowDocModal(false)}>
          <div className={styles.docModal} onClick={e => e.stopPropagation()}>
            <div className={styles.docHead}>
              <span className={styles.docTitle}><Paperclip size={15} /> Anexar documentos</span>
              <button className={styles.docClose} onClick={() => setShowDocModal(false)}><X size={16} /></button>
            </div>
            <div
              className={`${styles.dropzone} ${dragOver ? styles.dropOver : ""}`}
              onClick={() => docInputRef.current?.click()}
              onDragOver={e => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={e => { e.preventDefault(); setDragOver(false); addDocumentos(e.dataTransfer.files); }}
            >
              <UploadCloud size={30} className={styles.dropIco} />
              <p className={styles.dropTxt}>Arraste arquivos aqui ou <strong>clique para escolher</strong></p>
              <span className={styles.dropSub}>PDF, DOCX, XLSX, ZIP… do seu computador</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
