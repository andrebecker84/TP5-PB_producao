import HexLogo from "@/components/ui/HexLogo";
import FundoMeioTom from "@/components/ui/FundoMeioTom";
import AvisoDeCookies from "@/components/ui/AvisoDeCookies";
import styles from "./entrada.module.css";

/**
 * Moldura das telas de entrada (/login e /entrando): o fundo em meio-tom, a
 * luz e a marca no canto — os mesmos da tela de usuário e senha, que é do
 * provedor de identidade com o tema do Infnet Hub. Assim a passagem entre as
 * três parece uma tela só. Por isso a entrada é sempre escura, qualquer que
 * seja o tema escolhido: a tela do provedor não tem tema claro, e a troca de
 * cores no meio do login pareceria outra aplicação.
 */
export default function EntradaLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={styles.page} data-theme="dark">
      <FundoMeioTom className={styles.meioTom} />
      <div className={styles.luz} aria-hidden />
      <main className={styles.coluna}>{children}</main>
      <div className={styles.marca}>
        <HexLogo size={48} id="hexGradEntrada" />
        <span className={styles.marcaTxt}>Infnet<b>Hub</b></span>
      </div>
      <AvisoDeCookies botaoFlutuante />
    </div>
  );
}
