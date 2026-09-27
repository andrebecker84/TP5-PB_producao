"use client";

import { useRouter } from "next/navigation";
import {
  User, Mail, GraduationCap, Award, CalendarCheck, Clock,
  Activity, ArrowRight, Briefcase, HandHeart, Sparkles, BookMarked,
} from "lucide-react";
import { useUsuarioLogado } from "@/hooks/useCurrentUser";
import { initials } from "@/utils/format";
import { CORES } from "@/utils/colors";
import { useStatus, infoStatus } from "@/hooks/useStatus";
import StatusPicker from "@/components/layout/StatusPicker";
import styles from "./page.module.css";

/* Resumo do aluno — os mesmos números do boletim e do painel de desempenho,
   aqui só como panorama, com atalho para as páginas que detalham cada um. */
const RESUMO = [
  { icon: Award,         label: "Competências demonstradas", valor: "22",  hint: "9 DML · 11 DL · 2 D" },
  { icon: GraduationCap, label: "Disciplinas aprovadas",     valor: "6",   hint: "de 9 cursadas" },
  { icon: CalendarCheck, label: "Presença média",            valor: "92%", hint: "mínimo de 75%" },
  { icon: Activity,      label: "Conclusão do curso",        valor: "30%", hint: "bloco 3 de 12" },
];

const HORAS = [
  { icon: Sparkles,  label: "Atividades complementares", feitas: 70,  total: 140 },
  { icon: Briefcase, label: "Estágio obrigatório",       feitas: 220, total: 400 },
  { icon: HandHeart, label: "Projetos de extensão",      feitas: 300, total: 400 },
];

const ATALHOS = [
  { icon: Activity,      titulo: "Meu Desempenho", texto: "score, tendência e projeção do semestre", href: "/desempenho" },
  { icon: BookMarked,    titulo: "Boletim",        texto: "histórico por competências e carga horária", href: "/boletim" },
  { icon: Briefcase,     titulo: "Vagas",          texto: "estágios e oportunidades abertas",         href: "/vagas" },
];

export default function PerfilPage() {
  const router = useRouter();
  const currentUser = useUsuarioLogado();
  const status = useStatus();
  const cor = CORES[currentUser.id % CORES.length];

  return (
    <div className={styles.page}>
      {/* ── Identificação ── */}
      <header className={styles.capa}>
        <div className={styles.avatarWrap}>
          <div className={styles.avatar} style={{ background: cor }}>{initials(currentUser.nome)}</div>
          <span className={styles.pontoStatus} style={{ background: infoStatus(status).cor }}
            title={infoStatus(status).label} />
        </div>
        <div className={styles.identidade}>
          <h1 className={styles.nome}>{currentUser.nome}</h1>
          <p className={styles.papel}>{currentUser.papelDescricao} · Engenharia de Software</p>
          <p className={styles.email}><Mail size={13} /> {currentUser.email}</p>
        </div>
        <div className={styles.statusArea}>
          <span className={styles.statusLbl}>Status</span>
          <StatusPicker />
        </div>
      </header>

      {/* ── Panorama ── */}
      <section className={styles.resumo}>
        {RESUMO.map(r => {
          const Ico = r.icon;
          return (
            <div key={r.label} className={styles.kpi}>
              <span className={styles.kpiIco}><Ico size={15} /></span>
              <div>
                <div className={styles.kpiVal}>{r.valor}</div>
                <div className={styles.kpiLbl}>{r.label}</div>
                <div className={styles.kpiHint}>{r.hint}</div>
              </div>
            </div>
          );
        })}
      </section>

      {/* ── Horas obrigatórias ── */}
      <section className={styles.card}>
        <h2 className={styles.cardTitulo}><Clock size={15} /> Horas obrigatórias</h2>
        <ul className={styles.horas}>
          {HORAS.map(h => {
            const pct = Math.round((h.feitas / h.total) * 100);
            const Ico = h.icon;
            return (
              <li key={h.label} className={styles.hora}>
                <span className={styles.horaIco}><Ico size={13} /></span>
                <div className={styles.horaBody}>
                  <div className={styles.horaTop}>
                    <span className={styles.horaLbl}>{h.label}</span>
                    <span className={styles.horaNum}>{h.feitas}<i> / {h.total}h</i></span>
                  </div>
                  <div className={styles.trilho}><span style={{ width: `${pct}%` }} /></div>
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      {/* ── Atalhos ── */}
      <section className={styles.atalhos}>
        {ATALHOS.map(a => {
          const Ico = a.icon;
          return (
            <button key={a.href} className={styles.atalho} onClick={() => router.push(a.href)}>
              <span className={styles.atalhoIco}><Ico size={16} /></span>
              <span className={styles.atalhoTxt}>
                <b>{a.titulo}</b>
                <i>{a.texto}</i>
              </span>
              <ArrowRight size={15} className={styles.atalhoSeta} />
            </button>
          );
        })}
      </section>

      <p className={styles.rodape}>
        <User size={12} /> Dados ilustrativos para demonstração acadêmica.
      </p>
    </div>
  );
}
