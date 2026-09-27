"use client";

// Diferente do layout, o template remonta a cada navegação — é o que dá o
// fade curto na troca feed ↔ vagas sem tocar no Header/Sidebar persistentes.
export default function HubTemplate({ children }: { children: React.ReactNode }) {
  return <div className="pagina-entrada">{children}</div>;
}
