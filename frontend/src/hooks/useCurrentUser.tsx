"use client";

import { createContext, useContext } from "react";
import { sessaoService } from "@/services/sessaoService";
import { Usuario } from "@/types";

const CurrentUserContext = createContext<Usuario | null>(null);

export function CurrentUserProvider({ user, children }: { user: Usuario; children: React.ReactNode }) {
  return <CurrentUserContext.Provider value={user}>{children}</CurrentUserContext.Provider>;
}

/**
 * Usuário logado, fornecido pelo layout do hub — que só renderiza as páginas
 * depois de resolvê-lo do localStorage, então aqui ele nunca é nulo.
 */
export function useUsuarioLogado(): Usuario {
  const user = useContext(CurrentUserContext);
  if (!user) throw new Error("useUsuarioLogado usado fora do CurrentUserProvider");
  return user;
}

/**
 * Sair encerra a sessão no gateway e no Keycloak, e o navegador volta para a
 * tela de entrada pelo caminho de saída do provedor. Não há mais nada a apagar
 * no navegador: o cookie é HttpOnly e quem o invalida é o servidor.
 */
export function signOut() {
  sessaoService.sair();
}
