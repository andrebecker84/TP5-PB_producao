"use client";

import { MapPin, Briefcase, Pencil, Trash2, Calendar } from "lucide-react";
import { Vaga, Usuario } from "@/types";
import { initials, relativo } from "@/utils/format";
import { TIPO_COLOR, TIPO_TXT } from "@/utils/colors";
import styles from "./VagaCard.module.css";

interface Props {
  vaga: Vaga;
  currentUser: Usuario;
  onEditar: () => void;
  onDeletar: () => void;
}

export default function VagaCard({ vaga, currentUser, onEditar, onDeletar }: Props) {
  const isOwner = vaga.criadorId === currentUser.id;
  const cor = TIPO_COLOR[vaga.tipo] ?? "rgba(124,111,247,.18)";
  const txt = TIPO_TXT[vaga.tipo]   ?? "var(--primary)";

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <div className={styles.empresaLogo}>
          {initials(vaga.empresa)}
        </div>
        <div className={styles.headerInfo}>
          <h3 className={styles.titulo}>{vaga.titulo}</h3>
          <span className={styles.empresa}>{vaga.empresa}</span>
        </div>
        <span className={styles.tipoBadge} style={{ background: cor, color: txt }}>
          {vaga.tipoDescricao}
        </span>
      </div>

      {vaga.descricao && (
        <p className={styles.descricao}>{vaga.descricao}</p>
      )}

      <div className={styles.meta}>
        {vaga.localizacao && (
          <span className={styles.metaItem}>
            <MapPin size={12} /> {vaga.localizacao}
          </span>
        )}
        {vaga.categoria && (
          <span className={styles.metaItem}>
            <Briefcase size={12} /> {vaga.categoria}
          </span>
        )}
        <span className={styles.metaItem}>
          <Calendar size={12} /> {relativo(vaga.criadoEm)}
        </span>
      </div>

      <div className={styles.footer}>
        <button className={styles.btnCandidatar}>Candidatar-se</button>
        {isOwner && (
          <div className={styles.ownerActions}>
            <button className={styles.iconBtn} onClick={onEditar} title="Editar vaga">
              <Pencil size={14} />
            </button>
            <button className={`${styles.iconBtn} ${styles.delBtn}`} onClick={onDeletar} title="Remover vaga">
              <Trash2 size={14} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
