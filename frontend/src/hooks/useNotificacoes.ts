"use client";

import { useCallback, useEffect, useState } from "react";
import { Notificacao } from "@/types/notificacao";
import { notificacaoService } from "@/services/notificacaoService";

/**
 * As notificações do usuário logado, mantidas em dia sem recarregar a página.
 *
 * Dois caminhos, de propósito:
 *  1. **Leitura inicial** pela API — o estado completo, inclusive o que chegou
 *     enquanto a aba estava fechada.
 *  2. **Conexão ao vivo** (SSE) — cada notificação nova chega assim que o
 *     serviço a grava, e entra no topo da lista.
 *
 * O segundo é só antecipação do primeiro. Se a conexão cair, o `EventSource`
 * reconecta sozinho; e, ao reconectar, a lista é relida, para não perder o que
 * chegou no intervalo.
 *
 * Se o serviço de notificações estiver fora do ar, o sino fica vazio e o resto
 * da interface segue funcionando: é outro serviço, com outro ciclo de vida.
 */
export function useNotificacoes() {
  const [itens, setItens] = useState<Notificacao[]>([]);
  const [disponivel, setDisponivel] = useState(true);

  const recarregar = useCallback(() => {
    notificacaoService.minhas()
      .then(lista => { setItens(lista); setDisponivel(true); })
      .catch(() => setDisponivel(false));
  }, []);

  useEffect(() => {
    recarregar();

    // withCredentials: sem isto o cookie de sessão não acompanha a conexão,
    // que é de outra origem — e o gateway responderia 401.
    const fonte = new EventSource(notificacaoService.urlAoVivo(), { withCredentials: true });
    let primeiraConexao = true;

    fonte.addEventListener("conectado", () => {
      setDisponivel(true);
      // Reconexão: relê a lista para cobrir o que chegou com a conexão caída.
      if (!primeiraConexao) recarregar();
      primeiraConexao = false;
    });

    fonte.addEventListener("notificacao", (e) => {
      const nova: Notificacao = JSON.parse((e as MessageEvent).data);
      // O id evita duplicata: a mesma notificação pode chegar pela conexão e
      // pela releitura, se as duas coincidirem.
      setItens(atual => (atual.some(n => n.id === nova.id) ? atual : [nova, ...atual]));
    });

    // Apagadas em outro lugar: o post de que falavam saiu do feed, ou a pessoa
    // as excluiu em outra aba.
    fonte.addEventListener("removidas", (e) => {
      const ids: number[] = JSON.parse((e as MessageEvent).data);
      setItens(atual => atual.filter(n => !ids.includes(n.id)));
    });

    fonte.onerror = () => setDisponivel(false);

    return () => fonte.close();
  }, [recarregar]);

  const naoLidas = itens.filter(n => !n.lida).length;

  const marcarTodasComoLidas = useCallback(() => {
    if (!itens.some(n => !n.lida)) return;
    setItens(atual => atual.map(n => ({ ...n, lida: true })));
    notificacaoService.marcarTodasComoLidas().catch(recarregar);
  }, [itens, recarregar]);

  const marcarComoLidas = useCallback((ids: number[]) => {
    if (ids.length === 0) return;
    setItens(atual => atual.map(n => (ids.includes(n.id) ? { ...n, lida: true } : n)));
    notificacaoService.marcarComoLidas(ids).catch(recarregar);
  }, [recarregar]);

  const marcarComoNaoLidas = useCallback((ids: number[]) => {
    if (ids.length === 0) return;
    setItens(atual => atual.map(n => (ids.includes(n.id) ? { ...n, lida: false } : n)));
    notificacaoService.marcarComoNaoLidas(ids).catch(recarregar);
  }, [recarregar]);

  const excluir = useCallback((ids: number[]) => {
    if (ids.length === 0) return;
    setItens(atual => atual.filter(n => !ids.includes(n.id)));
    notificacaoService.excluir(ids).catch(recarregar);
  }, [recarregar]);

  return { itens, naoLidas, disponivel, marcarTodasComoLidas, marcarComoLidas, marcarComoNaoLidas, excluir };
}
