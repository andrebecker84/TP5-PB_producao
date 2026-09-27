"use client";

import { useState, useRef, useEffect } from "react";
import styles from "./MarqueeText.module.css";

/**
 * Texto que desliza de um lado ao outro quando não cabe — revelado no hover do
 * item que o hospeda (marque o ancestral com `data-marquee-host`). Assim títulos
 * e descrições longas são lidos por inteiro sem quebrar linha nem alargar o card.
 */
export default function MarqueeText({ children, className }: { children: string; className?: string }) {
  const wrapRef = useRef<HTMLSpanElement>(null);
  const [dist, setDist] = useState(0);

  useEffect(() => {
    const w = wrapRef.current;
    const txt = w?.firstElementChild as HTMLElement | null;
    if (!w || !txt) return;
    const medir = () => {
      const over = txt.scrollWidth - w.clientWidth;
      setDist(over > 4 ? over : 0);
    };
    medir();
    const ro = new ResizeObserver(medir);
    ro.observe(w);
    return () => ro.disconnect();
  }, [children]);

  return (
    <span ref={wrapRef} className={`${styles.wrap} ${className ?? ""}`}>
      <span
        className={`${styles.text} ${dist ? styles.scroll : ""}`}
        style={dist ? ({ "--dist": `-${dist}px` } as React.CSSProperties) : undefined}
      >
        {children}
      </span>
    </span>
  );
}
