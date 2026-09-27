"use client";

import { useState, useEffect } from "react";
import { Search, Loader2 } from "lucide-react";
import styles from "./GifPicker.module.css";

// A GIPHY/Tenor descontinuaram as chaves públicas de demonstração — a busca ao
// vivo exige uma chave GRÁTIS própria (developers.giphy.com, ~2 min). Defina-a
// em NEXT_PUBLIC_GIPHY_KEY (.env.local) e a busca funciona na hora.
const GIPHY_KEY = process.env.NEXT_PUBLIC_GIPHY_KEY ?? "";

interface GiphyImg { url?: string }
interface GiphyItem { id: string; title?: string; images: Record<string, GiphyImg | undefined> }
interface Gif { id: string; url: string; preview: string; title: string }

export default function GifPicker({ onSelect }: { onSelect: (url: string) => void }) {
  const [q, setQ]           = useState("");
  const [gifs, setGifs]     = useState<Gif[]>([]);
  const [loading, setLoad]  = useState(false);
  const [erro, setErro]     = useState(false);

  useEffect(() => {
    if (!GIPHY_KEY) return;
    let ativo = true;
    const t = setTimeout(async () => {
      setLoad(true); setErro(false);
      const base = q.trim()
        ? `https://api.giphy.com/v1/gifs/search?api_key=${GIPHY_KEY}&q=${encodeURIComponent(q.trim())}&limit=18&rating=g&bundle=messaging_non_clips`
        : `https://api.giphy.com/v1/gifs/trending?api_key=${GIPHY_KEY}&limit=18&rating=g&bundle=messaging_non_clips`;
      try {
        const res = await fetch(base);
        if (!res.ok) throw new Error(String(res.status));
        const json = (await res.json()) as { data?: GiphyItem[] };
        if (!ativo) return;
        setGifs((json.data ?? []).map(g => ({
          id: g.id,
          url: g.images.downsized_medium?.url || g.images.original?.url || "",
          preview: g.images.fixed_width_small?.url || g.images.fixed_width?.url || g.images.original?.url || "",
          title: g.title || "GIF",
        })).filter(g => g.preview));
      } catch {
        if (ativo) setErro(true);
      } finally {
        if (ativo) setLoad(false);
      }
    }, q ? 380 : 0);
    return () => { ativo = false; clearTimeout(t); };
  }, [q]);

  if (!GIPHY_KEY) {
    return (
      <div className={styles.picker} onClick={e => e.stopPropagation()}>
        <div className={styles.aviso}>
          <strong>Busca de GIFs pronta.</strong>
          Pegue uma chave grátis em <span className={styles.link}>developers.giphy.com</span> e defina
          <code>NEXT_PUBLIC_GIPHY_KEY</code> no <code>.env.local</code> — a busca ativa na hora.
        </div>
      </div>
    );
  }

  return (
    <div className={styles.picker} onClick={e => e.stopPropagation()}>
      <div className={styles.searchRow}>
        <Search size={14} className={styles.searchIco} />
        <input
          className={styles.input}
          autoFocus
          placeholder="Pesquisar GIFs…"
          value={q}
          onChange={e => setQ(e.target.value)}
        />
      </div>

      <div className={styles.grid}>
        {loading && <div className={styles.info}><Loader2 size={16} className={styles.spin} /> Buscando…</div>}
        {!loading && erro && <div className={styles.info}>Não foi possível carregar os GIFs.</div>}
        {!loading && !erro && gifs.length === 0 && <div className={styles.info}>Nenhum GIF encontrado.</div>}
        {!loading && !erro && gifs.map(g => (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            key={g.id}
            src={g.preview}
            alt={g.title}
            className={styles.gif}
            loading="lazy"
            onClick={() => onSelect(g.url)}
          />
        ))}
      </div>

      <div className={styles.foot}>Powered by GIPHY</div>
    </div>
  );
}
