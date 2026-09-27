"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Activity, ShieldCheck } from "lucide-react";
import { sessaoService } from "@/services/sessaoService";
import Digital from "@/components/ui/Digital";
import CarregandoLuz from "@/components/ui/CarregandoLuz";
import styles from "../entrada.module.css";

const PAINEL_DE_OPERACAO = process.env.NEXT_PUBLIC_GRAFANA_URL || "http://localhost:21300";

/** O destino escolhido na tela de login: o botão do Grafana marca "grafana". */
function destinoEscolhido(): "grafana" | "feed" {
  const achado = document.cookie.split("; ").find(c => c.startsWith("ih_destino="));
  document.cookie = "ih_destino=; path=/; max-age=0; SameSite=Lax"; // vale uma vez só
  return achado?.split("=")[1] === "grafana" ? "grafana" : "feed";
}

type Seguindo = "feed" | "grafana" | "sem-acesso";

/**
 * O "acesso liberado".
 *
 * O provedor de identidade conferiu a senha e devolveu a pessoa ao gateway,
 * que a manda para cá. É aqui, e não na tela de senha, que a digital fica
 * verde: só agora o acesso foi de fato confirmado — mostrar isso antes seria
 * mentir para quem errou a senha. Uma leitura rápida, a confirmação, o feixe
 * de luz, e o destino. O cartão tem a altura e a posição do cartão do
 * provedor, e nada entra animado: a troca de página não aparece.
 *
 * O destino foi escolhido na tela de login: "Entrar" (o feed) ou o botão do
 * painel de operação. No segundo caso, só quem tem o papel SUPORTE_TI segue
 * para o Grafana — que aproveita a sessão do Keycloak e não pede a senha de
 * novo. Os demais são avisados e seguem para o feed.
 */
export default function EntrandoPage() {
  const router = useRouter();
  const [leitura, setLeitura] = useState<"escaneando" | "confirmado">("escaneando");
  const [carregando, setCarregando] = useState(false);
  const [seguindo, setSeguindo] = useState<Seguindo>("feed");

  useEffect(() => {
    router.prefetch("/feed");
    const destino = destinoEscolhido();
    let vivo = true;
    const timers: ReturnType<typeof setTimeout>[] = [];
    sessaoService.atual()
      .then(sessao => {
        if (!vivo) return;
        // sem sessão, não há o que liberar: volta para a entrada
        if (!sessao) { router.replace("/login"); return; }
        const vai: Seguindo = destino === "grafana"
          ? (sessao.papel === "SUPORTE_TI" ? "grafana" : "sem-acesso")
          : "feed";
        setSeguindo(vai);
        timers.push(setTimeout(() => setLeitura("confirmado"), 600));
        timers.push(setTimeout(() => setCarregando(true), 1300));
        timers.push(setTimeout(() => {
          if (vai === "grafana") window.location.href = PAINEL_DE_OPERACAO;
          else router.replace("/feed");
        }, vai === "sem-acesso" ? 4200 : 2600));
      })
      .catch(() => router.replace("/login"));
    return () => { vivo = false; timers.forEach(clearTimeout); };
  }, [router]);

  return (
    <>
      <section className={`${styles.card} ${styles.cardContinuo}`}>
        <h1 className={styles.titulo}>Login</h1>
        {/* os dois espaços centralizam a digital no cartão; quando o carregamento
            começa, eles se recolhem, a digital sobe e as mensagens aparecem */}
        <div className={styles.espaco} data-recolhido={carregando || undefined} aria-hidden />
        <div className={styles.digitalLugar}>
          <Digital estado={leitura} semEntrada />
        </div>
        <p className={styles.leitura} data-estado={leitura} aria-live="polite">
          {leitura === "confirmado" ? "Acesso liberado" : "Lendo digital…"}
        </p>
        <div className={styles.corpo} data-aberto={carregando || undefined}>
          {carregando && (
            <div className={styles.seguindo}>
              {seguindo === "sem-acesso" && (
                <p className={styles.semAcesso} role="status">
                  O painel de operação é restrito ao suporte de TI. Abrindo o feed.
                </p>
              )}
              {seguindo === "grafana"
                ? <CarregandoLuz embutido tom="verde" icone={<Activity size={14} />} texto="Abrindo o painel de operação…" />
                : <CarregandoLuz embutido tom="verde" icone={<ShieldCheck size={14} />} texto="Carregando o hub…" />}
            </div>
          )}
        </div>
        <div className={styles.espaco} data-recolhido={carregando || undefined} aria-hidden />
      </section>
      <div className={styles.lugarDaNota} aria-hidden />
    </>
  );
}
