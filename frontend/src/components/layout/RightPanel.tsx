"use client";

import {
  ExternalLink, Building2, GraduationCap, Briefcase, Wrench,
  Boxes, Layers, Package, Atom, Smartphone, Target, Megaphone, Sparkles, Handshake,
  LifeBuoy, Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import DragScroll from "@/components/ui/DragScroll";
import MarqueeText from "@/components/ui/MarqueeText";
import styles from "./RightPanel.module.css";

const ATENDIMENTO = [
  { label: "Secretaria Faculdade Infnet", icon: Building2,     href: "https://infnet.online/members/atendimento-infnet/"    },
  { label: "Secretaria ECDD",             icon: GraduationCap, href: "https://infnet.online/members/atendimento-ecdd/"      },
  { label: "Central de Carreiras",        icon: Briefcase,     href: "https://infnet.online/members/atendimento-carreiras/" },
  { label: "Suporte de TI",               icon: Wrench,        href: "https://infnet.online/members/atendimento-suporteti/" },
];

const GRUPOS: { nome: string; tipo: "Bloco" | "Geral"; icon: LucideIcon }[] = [
  { nome: "Engenharia de Softwares Escaláveis [26E2-26E3]", tipo: "Bloco", icon: Boxes      },
  { nome: "Design Patterns e DDD com Java [26E2-26E3]",     tipo: "Bloco", icon: Layers     },
  { nome: "Projeto de Bloco: Eng. Softwares Escaláveis",    tipo: "Bloco", icon: Package    },
  { nome: "Desenvolvimento Web com React [26E2-26E3]",       tipo: "Bloco", icon: Atom       },
  { nome: "Desenvolvimento Mobile c/ React Native [26E2]",   tipo: "Bloco", icon: Smartphone },
  { nome: "Domain-Driven Design e Arquitetura Escalável",    tipo: "Bloco", icon: Target     },
  { nome: "Avisos e novidades da graduação",                 tipo: "Geral", icon: Megaphone  },
  { nome: "Histórias que inspiram",                         tipo: "Geral", icon: Sparkles   },
  { nome: "Calouros — Ingresso 2026",                       tipo: "Geral", icon: GraduationCap },
  { nome: "Alumni Infnet — Rede de Egressos",               tipo: "Geral", icon: Handshake  },
];

export default function RightPanel() {
  return (
    <aside className={styles.panel}>

      {/* ── Atendimento ── */}
      <div className={styles.card}>
        <h3 className={styles.title}><LifeBuoy size={15} className={styles.titleIco} /> Atendimento</h3>
        <p className={styles.desc}>Precisando de ajuda? Fale com a equipe correspondente.</p>
        {ATENDIMENTO.map(a => {
          const Icon = a.icon;
          return (
            <a key={a.href} href={a.href} target="_blank" rel="noopener noreferrer" className={styles.atendBtn}>
              <Icon size={15} className={styles.atendIco} />
              <span>{a.label}</span>
              <ExternalLink size={11} className={styles.extIco} />
            </a>
          );
        })}
      </div>

      {/* ── Meus Grupos ── */}
      <div className={styles.card}>
        <h3 className={styles.title}><Users size={15} className={styles.titleIco} /> Meus Grupos</h3>
        <DragScroll className={styles.grupoScroll}>
          {GRUPOS.map(g => {
            const Icon = g.icon;
            return (
              <div key={g.nome} data-marquee-host className={styles.grupo}>
                <div className={`${styles.grupoIcon} ${g.tipo === "Geral" ? styles.grupoIconGeral : ""}`}>
                  <Icon size={18} />
                </div>
                <div className={styles.grupoInfo}>
                  <MarqueeText className={styles.grupoNome}>{g.nome}</MarqueeText>
                  <span className={styles.grupoTipo}>{g.tipo}</span>
                </div>
              </div>
            );
          })}
        </DragScroll>
      </div>

    </aside>
  );
}
