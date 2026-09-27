"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import AppLayout from "@/components/layout/AppLayout";
import CarregandoLuz from "@/components/ui/CarregandoLuz";
import AvisoDeCookies from "@/components/ui/AvisoDeCookies";
import { CurrentUserProvider } from "@/hooks/useCurrentUser";
import { sessaoService } from "@/services/sessaoService";
import { usuarioService } from "@/services/usuarioService";
import { Usuario } from "@/types";

/**
 * Layout persistente das páginas autenticadas.
 *
 * Header, Sidebar e painéis são montados uma única vez e sobrevivem à
 * navegação feed ↔ vagas — a troca de rota substitui apenas o conteúdo
 * central, em vez de remontar (e re-buscar) a interface inteira.
 */
export default function HubLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<Usuario | null>(null);

  useEffect(() => {
    // Quem está logado é decidido pelo gateway, a partir do cookie de sessão —
    // e não mais por uma anotação no localStorage, que qualquer um editava. O
    // perfil completo vem do infnethub-core, pelo id que o token carrega.
    sessaoService.atual()
      .then(sessao => {
        if (!sessao?.usuarioId) { router.replace("/login"); return null; }
        return usuarioService.buscarPorId(sessao.usuarioId);
      })
      .then(usuario => { if (usuario) setUser(usuario); })
      .catch(() => router.replace("/login"));

    // Deixa os bundles das duas rotas prontos antes do primeiro clique.
    router.prefetch("/feed");
    router.prefetch("/vagas");
  }, [router]);

  if (!user) return <CarregandoLuz />;

  return (
    <CurrentUserProvider user={user}>
      <AppLayout currentUser={user}>{children}</AppLayout>
      {/* a escolha de cookies; revista pelo menu do usuário ("Privacidade e cookies") */}
      <AvisoDeCookies comSessao />
    </CurrentUserProvider>
  );
}
