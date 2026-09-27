"use client";

import { useRef, ReactNode } from "react";
import styles from "./DragScroll.module.css";

/**
 * Contêiner rolável com barra de rolagem estilizada e "mãozinha": segure o mouse
 * e arraste para rolar. Um arraste não dispara o clique dos itens internos.
 */
export default function DragScroll({
  children, className, style,
}: { children: ReactNode; className?: string; style?: React.CSSProperties }) {
  const ref = useRef<HTMLDivElement>(null);
  const st  = useRef({ down: false, startY: 0, top: 0, moved: false });

  const onDown = (e: React.MouseEvent) => {
    const el = ref.current; if (!el) return;
    st.current = { down: true, startY: e.clientY, top: el.scrollTop, moved: false };
    el.classList.add(styles.grabbing);
  };
  const onMove = (e: React.MouseEvent) => {
    const el = ref.current; if (!el || !st.current.down) return;
    const dy = e.clientY - st.current.startY;
    if (Math.abs(dy) > 3) st.current.moved = true;
    el.scrollTop = st.current.top - dy;
  };
  const end = () => {
    const el = ref.current; if (el) el.classList.remove(styles.grabbing);
    st.current.down = false;
  };
  // se houve arraste, cancela o clique que se seguiria (não abre o item)
  const onClickCapture = (e: React.MouseEvent) => {
    if (st.current.moved) { e.preventDefault(); e.stopPropagation(); }
  };

  return (
    <div
      ref={ref}
      className={`${styles.scroll} ${className ?? ""}`}
      style={style}
      onMouseDown={onDown}
      onMouseMove={onMove}
      onMouseUp={end}
      onMouseLeave={end}
      onClickCapture={onClickCapture}
    >
      {children}
    </div>
  );
}
