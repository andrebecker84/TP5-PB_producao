"use client";

import { useState, useEffect, FormEvent } from "react";
import { X } from "lucide-react";
import { Vaga, VagaRequest, TipoVaga, Usuario } from "@/types";
import styles from "./VagaForm.module.css";

interface Props {
  vagaParaEditar: Vaga | null;
  currentUser: Usuario;
  salvando: boolean;
  onSalvar: (dados: VagaRequest) => void;
  onCancelar: () => void;
}

const TIPOS: { value: TipoVaga; label: string }[] = [
  { value: "ESTAGIO",  label: "Estágio" },
  { value: "CLT",      label: "Emprego (CLT)" },
  { value: "PJ",       label: "PJ (Terceirizado)" },
  { value: "TRAINEE",  label: "Emprego Trainee (CLT)" },
  { value: "AUTONOMO", label: "Autônomo" },
  { value: "EXTERIOR", label: "Emprego no Exterior" },
];

const VAZIO: Omit<VagaRequest, "criadorId"> = {
  titulo: "", empresa: "", descricao: "", localizacao: "", tipo: "CLT", categoria: "",
};

export default function VagaForm({ vagaParaEditar, currentUser, salvando, onSalvar, onCancelar }: Props) {
  const [form, setForm] = useState({ ...VAZIO });

  useEffect(() => {
    if (vagaParaEditar) {
      setForm({
        titulo:      vagaParaEditar.titulo,
        empresa:     vagaParaEditar.empresa,
        descricao:   vagaParaEditar.descricao ?? "",
        localizacao: vagaParaEditar.localizacao ?? "",
        tipo:        vagaParaEditar.tipo,
        categoria:   vagaParaEditar.categoria ?? "",
      });
    } else {
      setForm({ ...VAZIO });
    }
  }, [vagaParaEditar]);

  const set = (field: keyof typeof VAZIO) =>
    (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
      setForm(prev => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSalvar({ ...form, criadorId: currentUser.id });
  };

  const isEdit = !!vagaParaEditar;

  return (
    <div className={styles.overlay} onClick={e => { if (e.target === e.currentTarget) onCancelar(); }}>
      <div className={styles.modal}>
        <div className={styles.header}>
          <h2>{isEdit ? "Editar Vaga" : "Nova Vaga"}</h2>
          <button className={styles.closeBtn} onClick={onCancelar} disabled={salvando}>
            <X size={16} />
          </button>
        </div>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.row}>
            <div className={styles.field}>
              <label>Título da Vaga *</label>
              <input placeholder="Ex: Desenvolvedor Java Backend" value={form.titulo} onChange={set("titulo")} required maxLength={150} disabled={salvando} />
            </div>
            <div className={styles.field}>
              <label>Empresa *</label>
              <input placeholder="Ex: TechSolutions Brasil" value={form.empresa} onChange={set("empresa")} required maxLength={150} disabled={salvando} />
            </div>
          </div>

          <div className={styles.field}>
            <label>Descrição</label>
            <textarea placeholder="Descreva a vaga, requisitos e diferenciais..." value={form.descricao} onChange={set("descricao")} rows={3} disabled={salvando} />
          </div>

          <div className={styles.row}>
            <div className={styles.field}>
              <label>Tipo *</label>
              <select value={form.tipo} onChange={set("tipo")} required disabled={salvando}>
                {TIPOS.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
            <div className={styles.field}>
              <label>Localização</label>
              <input placeholder="Ex: São Paulo, SP / Remoto" value={form.localizacao} onChange={set("localizacao")} maxLength={100} disabled={salvando} />
            </div>
          </div>

          <div className={styles.field}>
            <label>Categoria</label>
            <input placeholder="Ex: Backend, Frontend, Mobile, Data Science" value={form.categoria} onChange={set("categoria")} maxLength={100} disabled={salvando} />
          </div>

          <div className={styles.actions}>
            <button type="button" className={styles.btnCancelar} onClick={onCancelar} disabled={salvando}>Cancelar</button>
            <button type="submit" className={styles.btnSalvar} disabled={salvando}>
              {salvando ? "Salvando..." : isEdit ? "Salvar Alterações" : "Publicar Vaga"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
