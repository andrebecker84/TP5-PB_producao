import { api, ok, GATEWAY } from "./api";

/**
 * Quem está logado, segundo o gateway.
 *
 * O navegador não tem o token — ele fica na sessão do servidor —, então não há
 * como ler o próprio nome ou papel de dentro de um JWT. Pergunta-se ao gateway.
 */
export interface Sessao {
  usuarioId: number | null;
  username: string;
  nome: string;
  email: string;
  papel: "ALUNO" | "PROFESSOR" | "SECRETARIA" | "COORDENADOR" | "SUPORTE_TI";
}

/** Lê um cookie legível pelo JavaScript — o de sessão não é. */
function cookie(nome: string): string {
  if (typeof document === "undefined") return "";
  const achado = document.cookie.split("; ").find(c => c.startsWith(`${nome}=`));
  return achado ? decodeURIComponent(achado.split("=").slice(1).join("=")) : "";
}

export const sessaoService = {
  /** A sessão atual, ou `null` se não há nenhuma. */
  atual: async (): Promise<Sessao | null> => {
    const r = await api(`${GATEWAY}/bff/eu`);
    if (r.status === 401) return null;
    return ok<Sessao>(r);
  },

  /**
   * Leva ao Keycloak para autenticar.
   *
   * Navegação de página inteira, e não `fetch`: o login acontece no site do
   * provedor de identidade, com a senha sendo digitada lá — é justamente o que
   * impede esta aplicação de ver a senha de alguém.
   */
  entrar: () => {
    window.location.href = `${GATEWAY}/oauth2/authorization/keycloak`;
  },

  /**
   * Encerra a sessão do gateway e a do Keycloak.
   *
   * Um formulário submetido, e não `fetch`: a saída termina em redirecionamento
   * para o Keycloak e de lá de volta para cá, e só uma navegação de verdade
   * percorre esse caminho. O token anti-CSRF vai no corpo, como o Spring
   * Security espera de um formulário.
   */
  sair: () => {
    const formulario = document.createElement("form");
    formulario.method = "POST";
    formulario.action = `${GATEWAY}/logout`;

    const csrf = document.createElement("input");
    csrf.type = "hidden";
    csrf.name = "_csrf";
    csrf.value = cookie("XSRF-TOKEN");
    formulario.appendChild(csrf);

    document.body.appendChild(formulario);
    formulario.submit();
  },
};
