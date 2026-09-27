"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, TriangleAlert } from "lucide-react";
import { sessaoService } from "@/services/sessaoService";
import styles from "../entrada.module.css";

type Falha = "servidor" | "login" | null;

/**
 * Entrada da plataforma.
 *
 * Não pede nada: quem já tem sessão vai para o feed, e quem não tem segue
 * direto para o provedor de identidade. A tela de usuário e senha é a do
 * Keycloak, com o tema do Infnet Hub — a abertura "Conectando ao provedor de
 * identidade…", a digital, o aviso de ambiente e o atalho do painel de
 * operação estão lá. A senha é digitada só nela, nunca nesta aplicação.
 *
 * Esta página só aparece de fato em dois casos:
 * - o servidor está fora do ar — mostrar o erro é melhor que mandar a pessoa
 *   para uma tela que não vai carregar;
 * - o login não fechou (`?erro=login`): o pedido expirou, a pessoa voltou no
 *   navegador ou abriu duas abas de login. O gateway manda para cá, e daqui
 *   ela tenta de novo.
 */
export default function LoginPage() {
  const router = useRouter();
  const [falha, setFalha] = useState<Falha>(null);

  useEffect(() => {
    if (new URLSearchParams(window.location.search).get("erro") === "login") {
      setFalha("login");
      return;
    }
    // voltou da saída do Grafana: a sessão do Keycloak já acabou; encerra a
    // da plataforma também, em vez de deixar a pessoa no feed ainda logada
    const saiu = new URLSearchParams(window.location.search).has("saiu");
    router.prefetch("/feed");
    sessaoService.atual()
      .then(sessao => {
        if (sessao && saiu) { sessaoService.sair(); return; }
        // já com sessão: o feed — ou, se o destino marcado é o painel de
        // operação, a /entrando, que confere o papel e abre o Grafana
        if (sessao) router.replace(document.cookie.includes("ih_destino=grafana") ? "/entrando" : "/feed");
        else sessaoService.entrar();
      })
      .catch(() => setFalha("servidor"));
  }, [router]);

  if (!falha) return null;

  return (
    <section className={styles.card}>
      <h1 className={styles.titulo}>Login</h1>
      <div className={styles.erro} role="alert">
        <TriangleAlert size={18} className={styles.erroIco} />
        <span>
          {falha === "login"
            ? "O login não foi concluído — o pedido expirou ou foi aberto em outra aba. Tente de novo."
            : "Não foi possível falar com o servidor. Verifique se o ambiente está no ar."}
        </span>
      </div>
      <button
        className={styles.btn}
        onClick={() => falha === "login" ? sessaoService.entrar() : window.location.reload()}
      >
        {falha === "login" ? <>Entre com a sua conta Infnet <ArrowRight size={16} /></> : "Tentar de novo"}
      </button>
    </section>
  );
}
