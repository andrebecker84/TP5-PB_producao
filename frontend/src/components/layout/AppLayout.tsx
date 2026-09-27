"use client";

import { useState, useEffect } from "react";
import { gravarJanela, lerJanelas, REDEFINIR_JANELAS } from "@/lib/janelas";
import FundoMeioTom from "@/components/ui/FundoMeioTom";
import HexLogo from "@/components/ui/HexLogo";
import Header from "./Header";
import Sidebar from "./Sidebar";
import Toaster from "@/components/ui/Toaster";
import { Usuario } from "@/types";
import styles from "./AppLayout.module.css";

interface Props {
  currentUser: Usuario;
  children: React.ReactNode;
}

export default function AppLayout({ currentUser, children }: Props) {
  // Expandida por padrão: a navegação principal agora vive só na sidebar
  const [sidebarExpanded, setSidebarExpanded] = useState(true);

  // Recolhe automaticamente em telas estreitas; volta a expandir quando há espaço.
  // Entre uma quebra e outra o usuário ainda pode alternar manualmente no hambúrguer.
  // Com o estado das janelas lembrado (aviso de cookies), a escolha guardada
  // vale na abertura; "redefinir padrão" volta a seguir a largura da tela.
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 1280px)");
    const apply = () => setSidebarExpanded(!mq.matches);
    const guardada = lerJanelas().sidebar;
    if (guardada) setSidebarExpanded(guardada === "expandida"); else apply();
    mq.addEventListener("change", apply);
    window.addEventListener(REDEFINIR_JANELAS, apply);
    return () => {
      mq.removeEventListener("change", apply);
      window.removeEventListener(REDEFINIR_JANELAS, apply);
    };
  }, []);

  const alternarSidebar = () => setSidebarExpanded(v => {
    gravarJanela({ sidebar: v ? "compacta" : "expandida" });
    return !v;
  });

  return (
    <div
      className={styles.app}
      data-sidebar-expandida={sidebarExpanded || undefined}
      style={{
        '--sidebar-w': sidebarExpanded ? 'var(--sidebar-full)' : 'var(--sidebar-mini)',
      } as React.CSSProperties}
    >
      {/* o mesmo fundo da tela de entrada, parado: o movimento fica só na entrada */}
      <FundoMeioTom className={styles.meioTom} estatico />
      <div className={styles.luz} aria-hidden />
      {/* a marca no canto, como na entrada: parte do fundo, atrás do conteúdo */}
      <div className={styles.marca} aria-hidden>
        <HexLogo size={48} id="hexGradHub" />
        <span className={styles.marcaTxt}>Infnet<b>Hub</b></span>
      </div>
      <Header currentUser={currentUser} />
      <Sidebar
        expanded={sidebarExpanded}
        currentUser={currentUser}
        onToggleSidebar={alternarSidebar}
      />
      <div className={styles.content}>{children}</div>
      <Toaster />
    </div>
  );
}
