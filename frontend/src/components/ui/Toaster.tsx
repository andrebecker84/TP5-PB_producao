"use client";

import { useState, useEffect, useCallback } from "react";
import { CheckCircle2, XCircle, Info, AlertTriangle, X } from "lucide-react";
import type { ToastType } from "@/utils/toast";
import styles from "./Toaster.module.css";

interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
  leaving?: boolean;
}

const ICON: Record<ToastType, typeof CheckCircle2> = {
  success: CheckCircle2,
  error:   XCircle,
  info:    Info,
  warning: AlertTriangle,
};

export default function Toaster() {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts(prev => prev.map(t => t.id === id ? { ...t, leaving: true } : t));
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 320);
  }, []);

  useEffect(() => {
    const onToast = (e: Event) => {
      const { id, message, type } = (e as CustomEvent<ToastItem>).detail;
      setToasts(prev => [...prev.slice(-4), { id, message, type }]);
      setTimeout(() => dismiss(id), 3800);
    };
    window.addEventListener('infnet:toast', onToast);
    return () => window.removeEventListener('infnet:toast', onToast);
  }, [dismiss]);

  if (!toasts.length) return null;

  return (
    <div className={styles.container} aria-live="polite">
      {toasts.map(t => {
        const Icon = ICON[t.type];
        return (
          <div key={t.id} className={`${styles.toast} ${styles[t.type]} ${t.leaving ? styles.leaving : ''}`}>
            <Icon size={15} className={styles.icon} />
            <span className={styles.msg}>{t.message}</span>
            <button className={styles.closeBtn} onClick={() => dismiss(t.id)} aria-label="Fechar">
              <X size={12} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
