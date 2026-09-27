"use client";

import { useState, useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { useRouter, usePathname } from "next/navigation";
import {
  LayoutDashboard, User, Users, Trophy, Briefcase,
  MessageSquare, Settings, LogOut, Activity, GraduationCap,
  FileText, BookOpen, Monitor, CalendarDays, BookMarked,
  Menu, PanelLeftClose, ExternalLink, X, Sun, Moon, Palette,
} from "lucide-react";
import { podeGuardarPreferencias } from "@/lib/consentimento";
import type { LucideIcon } from "lucide-react";
import { Usuario } from "@/types";
import { signOut } from "@/hooks/useCurrentUser";
import { initials } from "@/utils/format";
import { CORES } from "@/utils/colors";
import { MENSAGENS_NAO_LIDAS } from "@/data/inbox";
import { useStatus, infoStatus } from "@/hooks/useStatus";
import StatusPicker from "./StatusPicker";
import HexLogo from "@/components/ui/HexLogo";
import styles from "./Sidebar.module.css";

interface Props {
  expanded: boolean;
  currentUser: Usuario;
  onToggleSidebar: () => void;
}

const NAV: { label: string; icon: LucideIcon; href: string; badge?: number }[] = [
  { label: "Feed",           icon: LayoutDashboard, href: "/feed"  },
  { label: "Perfil",         icon: User,            href: "/perfil" },
  { label: "Meu Desempenho", icon: Activity,        href: "/desempenho" },
  { label: "Boletim",        icon: GraduationCap,   href: "/boletim" },
  { label: "Grupos",         icon: Users,           href: "/feed"  },
  { label: "Trilhas",        icon: Trophy,          href: "/feed"  },
  { label: "Vagas",          icon: Briefcase,       href: "/vagas" },
  { label: "Mensagens",      icon: MessageSquare,   href: "/feed", badge: MENSAGENS_NAO_LIDAS },
  { label: "Configurações",  icon: Settings,        href: "/feed"  },
];

/* rotas reais (recebem destaque de "ativo" quando a URL bate) */
const ROTAS_REAIS = new Set(["Feed", "Perfil", "Vagas", "Meu Desempenho", "Boletim"]);

const LINKS = [
  { label: "Requerimentos",        icon: FileText,     noEmbed: false, href: "https://requerimentos.infnet.edu.br/",                                                             extHref: "https://requerimentos.infnet.edu.br/" },
  { label: "Manual de Graduação",  icon: BookOpen,     noEmbed: false, href: "https://docs.google.com/document/d/1f1X-9SuN02CYcXcNlFrzsJ_Xb9y6AUwRQ8B2782e8vk/preview",      extHref: "https://docs.google.com/document/d/1f1X-9SuN02CYcXcNlFrzsJ_Xb9y6AUwRQ8B2782e8vk/" },
  { label: "Manual da Pós Live",   icon: Monitor,      noEmbed: true,  href: "https://sites.google.com/infnet.edu.br/manualposlive/pos-live",                                  extHref: "https://sites.google.com/infnet.edu.br/manualposlive/pos-live" },
  { label: "Calendário Acadêmico", icon: CalendarDays, noEmbed: false, href: "https://docs.google.com/spreadsheets/d/1b-CaoKxQZVM9zH1q0Bruoyf3BimlUiwIJSvL828N5JU/preview",  extHref: "https://docs.google.com/spreadsheets/d/1b-CaoKxQZVM9zH1q0Bruoyf3BimlUiwIJSvL828N5JU/" },
  { label: "Biblioteca Virtual",   icon: BookMarked,   noEmbed: true,  href: "https://learning.oreilly.com/home/",                                                              extHref: "https://learning.oreilly.com/home/" },
];

export default function Sidebar({ expanded, currentUser, onToggleSidebar }: Props) {
  const router   = useRouter();
  const pathname = usePathname();
  const [theme,    setTheme]    = useState<"dark" | "light">("dark");
  const [modal,    setModal]    = useState<{ url: string; extHref: string; title: string } | null>(null);
  const [iframeOk, setIframeOk] = useState(true);
  const [flash,    setFlash]    = useState<{ id: number; mode: "dark" | "light" } | null>(null);
  const [contaMenu, setContaMenu] = useState<{ x: number; y: number } | null>(null);
  const contaRef = useRef<HTMLButtonElement>(null);

  const cor = CORES[currentUser.id % CORES.length];
  const statusAtual = useStatus();

  useEffect(() => {
    if (!contaMenu) return;
    const close = (e: MouseEvent) => {
      if (contaRef.current && !contaRef.current.contains(e.target as Node)) setContaMenu(null);
    };
    window.addEventListener("mousedown", close);
    return () => window.removeEventListener("mousedown", close);
  }, [contaMenu]);

  const abrirContaMenu = () => {
    const r = contaRef.current?.getBoundingClientRect();
    if (!r) return;
    // abre para a lateral direita, alinhado pela base do botão
    setContaMenu(prev => (prev ? null : { x: r.right + 12, y: window.innerHeight - r.bottom }));
  };

  useEffect(() => {
    const saved = localStorage.getItem("infnet-theme") as "dark" | "light" | null;
    if (saved) setTheme(saved);
  }, []);

  const toggleTheme = () => {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    // guardar a escolha só com consentimento para preferências (aviso de cookies)
    if (podeGuardarPreferencias()) localStorage.setItem("infnet-theme", next);
    const root = document.documentElement;
    // a classe transiciona as cores da página inteira só durante a troca
    root.classList.add("theme-transition");
    root.setAttribute("data-theme", next);
    window.setTimeout(() => root.classList.remove("theme-transition"), 350);
    // efeito de fecho: névoa de crepúsculo (dark) ou flare de sol (light)
    setFlash({ id: Date.now(), mode: next });
  };

  return (
    <aside className={`${styles.sidebar} ${expanded ? styles.expanded : ""}`}>

      {/* ── Topo: marca (esquerda/centro) + recolher (direita) ── */}
      <div className={styles.sidebarTop}>
        <div className={styles.sidebarLogo}>
          <HexLogo size={48} id="hexGradSidebar" />
          <span className={styles.sidebarLogoText}>Infnet<b>Hub</b></span>
        </div>
        <button
          className={styles.hamburger}
          onClick={onToggleSidebar}
          title={expanded ? "Recolher menu" : "Expandir menu"}
        >
          {expanded ? <PanelLeftClose size={18} /> : <Menu size={18} />}
        </button>
      </div>

      {/* divisória entre a marca e o menu (sem linha em branco) */}
      <div className={`${styles.divider} ${styles.dividerNav}`} />

      {/* ── Navegação principal (Plataforma) ── */}
      <nav className={styles.nav}>
        {NAV.map(({ label, icon: Icon, href, badge }) => {
          const active = pathname === href && ROTAS_REAIS.has(label);
          return (
            <button
              key={label}
              className={`${styles.navItem} ${active ? styles.active : ""}`}
              onClick={() => router.push(href)}
              title={!expanded ? label : undefined}
            >
              <span className={styles.icoWrap}>
                <Icon size={19} className={styles.ico} />
                {badge ? <span className={styles.navBadgeDot}>{badge}</span> : null}
              </span>
              <span className={styles.lbl}>{label}</span>
              {badge ? <span className={styles.navBadge}>{badge}</span> : null}
            </button>
          );
        })}
      </nav>

      {/* divisória entre Configurações (Plataforma) e Requerimentos (Institucional) */}
      <div className={`${styles.divider} ${styles.dividerNav}`} />

      <div className={styles.linkList}>
        {LINKS.map(({ label, icon: Icon, href, extHref, noEmbed }) => (
          <button
            key={href}
            className={styles.linkItem}
            title={!expanded ? label : undefined}
            onClick={() => { setIframeOk(!noEmbed); setModal({ url: href, extHref, title: label }); }}
          >
            <Icon size={18} className={styles.ico} />
            <span className={styles.lbl}>{label}</span>
          </button>
        ))}
      </div>

      {modal && createPortal(
        <div className={styles.modalOverlay} onClick={() => setModal(null)}>
          <div className={styles.modalBox} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <span className={styles.modalTitle}>{modal.title}</span>
              <div className={styles.modalActions}>
                <a href={modal.extHref} target="_blank" rel="noopener noreferrer" className={styles.modalExtBtn}>
                  <ExternalLink size={13} /> Abrir em nova aba
                </a>
                <button className={styles.modalClose} onClick={() => setModal(null)}>
                  <X size={16} />
                </button>
              </div>
            </div>
            {!iframeOk ? (
              <div className={styles.modalBlocked}>
                <ExternalLink size={32} className={styles.modalBlockedIcon} />
                <p>Este site não permite visualização incorporada.</p>
                <a href={modal.extHref} target="_blank" rel="noopener noreferrer" className={styles.modalBlockedBtn}>
                  Abrir em nova aba
                </a>
              </div>
            ) : (
              <iframe
                src={modal.url}
                className={styles.modalFrame}
                title={modal.title}
                onError={() => setIframeOk(false)}
                onLoad={e => {
                  try {
                    const doc = (e.target as HTMLIFrameElement).contentDocument;
                    if (doc && doc.body && doc.body.innerHTML === "") setIframeOk(false);
                  } catch { /* cross-origin — carregou normalmente */ }
                }}
              />
            )}
          </div>
        </div>,
        document.body
      )}

      {flash && createPortal(
        <div
          key={flash.id}
          className={flash.mode === "dark" ? styles.flashDusk : styles.flashFlare}
          onAnimationEnd={() => setFlash(null)}
          aria-hidden
        />,
        document.body
      )}

      {/* ── Rodapé: tema + conta concentrados embaixo ── */}
      <div className={styles.sidebarFooter}>
        <div className={styles.divider} />

        {/* toggle de tema */}
        {expanded ? (
          <button
            className={styles.temaRow}
            onClick={toggleTheme}
            role="switch"
            aria-checked={theme === "light"}
            title={theme === "dark" ? "Mudar para tema claro" : "Mudar para tema escuro"}
          >
            <Palette size={16} className={styles.temaRowIco} />
            <span className={styles.temaRowLabel}>Tema de Cores</span>
            <span className={styles.temaSwitch}>
              <span className={`${styles.temaKnob} ${theme === "light" ? styles.temaKnobDir : ""}`}>
                <span key={theme} className={styles.temaIcon}>
                  {theme === "dark" ? <Moon size={11} /> : <Sun size={11} />}
                </span>
              </span>
            </span>
          </button>
        ) : (
          <button
            className={styles.temaIconBtn}
            onClick={toggleTheme}
            title={theme === "dark" ? "Tema claro" : "Tema escuro"}
          >
            <span key={theme} className={styles.temaIcon}>
              {theme === "dark" ? <Moon size={19} /> : <Sun size={19} />}
            </span>
          </button>
        )}

        {/* conta: avatar (com status) + nome + e-mail */}
        <button
          ref={contaRef}
          className={styles.contaBtn}
          onClick={abrirContaMenu}
          title={!expanded ? currentUser.nome : undefined}
        >
          <div className={styles.contaAvatarWrap}>
            <div className={styles.contaAvatar} style={{ background: cor }}>{initials(currentUser.nome)}</div>
            <span className={styles.contaStatus} data-status={statusAtual}
              style={{ background: infoStatus(statusAtual).cor }} title={infoStatus(statusAtual).label} />
          </div>
          <div className={styles.contaInfo}>
            <span className={styles.contaNome}>{currentUser.nome}</span>
            <span className={styles.contaEmail}>{currentUser.email}</span>
          </div>
        </button>
      </div>

      {contaMenu && createPortal(
        <div
          className={styles.contaMenu}
          style={{ left: contaMenu.x, bottom: contaMenu.y }}
          onMouseDown={e => e.stopPropagation()}
        >
          <div className={styles.contaMenuHeader}>
            <div className={styles.contaAvatarBig} style={{ background: cor }}>{initials(currentUser.nome)}</div>
            <div className={styles.contaMenuNome}>{currentUser.nome}</div>
            <div className={styles.contaMenuPapel}>{currentUser.papelDescricao}</div>
            <div className={styles.contaMenuEmail}>{currentUser.email}</div>
            <div className={styles.contaMenuStatus}><StatusPicker /></div>
          </div>
          <button className={styles.contaSair} onClick={signOut}>
            <LogOut size={15} /> Sair
          </button>
        </div>,
        document.body
      )}

    </aside>
  );
}
