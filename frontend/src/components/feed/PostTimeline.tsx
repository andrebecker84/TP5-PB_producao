"use client";

import { useRef, useState, useLayoutEffect } from "react";
import { Clock } from "lucide-react";
import { Post } from "@/types";
import { initials } from "@/utils/format";
import { CORES } from "@/utils/colors";
import styles from "./PostTimeline.module.css";

function formatar(iso: string): { hora: string; dia: string } {
  const d = new Date(iso);
  const hh = d.getHours().toString().padStart(2, "0");
  const mm = d.getMinutes().toString().padStart(2, "0");
  const dia = d.getDate().toString().padStart(2, "0");
  const mes = d.toLocaleDateString("pt-BR", { month: "short" }).replace(".", "");
  return { hora: `${hh}:${mm}`, dia: `${dia} ${mes}` };
}

/** Trilho vertical alinhado a cada post do feed: o avatar+horário de cada
 *  entrada fica na mesma altura do post correspondente (medida em runtime).
 *  Clicar leva ao post. */
export default function PostTimeline({ posts }: { posts: Post[] }) {
  const railRef = useRef<HTMLElement>(null);
  const [tops, setTops] = useState<Record<number, number>>({});

  useLayoutEffect(() => {
    const compute = () => {
      const rail = railRef.current;
      if (!rail) return;
      const railTop = rail.getBoundingClientRect().top;
      const next: Record<number, number> = {};
      posts.forEach(p => {
        const el = document.getElementById(`post-${p.id}`);
        if (el) {
          const r = el.getBoundingClientRect();
          // centraliza o nó na altura do post (meio do card)
          next[p.id] = r.top - railTop + r.height / 2;
        }
      });
      setTops(next);
    };

    compute();
    const ro = new ResizeObserver(compute);
    posts.forEach(p => {
      const el = document.getElementById(`post-${p.id}`);
      if (el) ro.observe(el);
    });
    window.addEventListener("resize", compute);
    return () => { ro.disconnect(); window.removeEventListener("resize", compute); };
  }, [posts]);

  if (posts.length === 0) return null;

  const irAoPost = (id: number) =>
    document.getElementById(`post-${id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });

  return (
    <aside ref={railRef} className={styles.rail} aria-label="Linha do tempo do feed">
      <span className={styles.linha} aria-hidden />
      {posts.map(p => {
        const { hora, dia } = formatar(p.criadoEm);
        const cor = CORES[p.autorId % CORES.length];
        const top = tops[p.id];
        return (
          <button
            key={p.id}
            className={styles.node}
            style={top != null ? { top } : { visibility: "hidden" }}
            onClick={() => irAoPost(p.id)}
            title={p.autorNome}
          >
            <span className={styles.relogio}><Clock size={11} /></span>
            <span className={styles.tempo}>
              <span className={styles.hora}>{hora}</span>
              <span className={styles.dia}>{dia}</span>
            </span>
            <span className={styles.dot} style={{ background: cor }}>{initials(p.autorNome)}</span>
          </button>
        );
      })}
    </aside>
  );
}
