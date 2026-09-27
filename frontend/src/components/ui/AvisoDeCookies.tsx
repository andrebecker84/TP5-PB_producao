"use client";

import { useCallback, useEffect, useState } from "react";
import { ChevronDown, Cookie, LogOut, Minus, RotateCcw, Trash2, X } from "lucide-react";
import * as consentimento from "@/lib/consentimento";
import { redefinirJanelas } from "@/lib/janelas";
import { sessaoService } from "@/services/sessaoService";
import styles from "./AvisoDeCookies.module.css";

interface Item { nome: string; para: string; duracao: string }

const ESSENCIAIS: Item[] = [
  { nome: "SESSION", para: "Mantém você conectado à plataforma. Não é legível por scripts da página (HttpOnly).", duracao: "até sair ou fechar o navegador" },
  { nome: "XSRF-TOKEN", para: "Impede que outro site envie ações em seu nome (proteção contra falsificação de requisições).", duracao: "a sessão" },
  { nome: "KEYCLOAK_SESSION, AUTH_SESSION_ID, KC_AUTH_SESSION_HASH, KC_RESTART", para: "Login no provedor de identidade da Infnet, onde a senha é digitada.", duracao: "até sair" },
  { nome: consentimento.COOKIE, para: "Guarda esta escolha, para não perguntar de novo.", duracao: "1 ano" },
];
const PREFERENCIAS: Item[] = [
  { nome: "infnet-theme", para: "Tema claro ou escuro da interface (armazenamento local).", duracao: "até você apagar" },
  { nome: "infnet_status", para: "Seu status de presença — disponível, ausente, ocupado (armazenamento local).", duracao: "até você apagar" },
];
const JANELAS: Item[] = [
  { nome: "infnet-janelas", para: "Menu lateral compacto ou expandido; mensagens abertas, recolhidas ou fechadas (armazenamento local).", duracao: "até você apagar ou redefinir" },
];

/**
 * Aviso de cookies e preferências de privacidade (LGPD).
 *
 * Na primeira visita, um aviso com as três escolhas de sempre — aceitar
 * todos, rejeitar os opcionais, personalizar —, com o mesmo peso visual para
 * aceitar e rejeitar. A escolha pode ser revista a qualquer momento: pelo
 * botão flutuante nas telas de entrada e pelo menu do usuário dentro da
 * plataforma. O painel lista cada cookie, para que serve e quanto dura, e
 * oferece apagar os dados deste navegador — e, com sessão, sair. "Personalizar"
 * expande o próprio aviso, no mesmo canto: nada abre no meio da tela. O aviso
 * pode ser minimizado sem decidir nada: vira só o ícone no canto, e a
 * pergunta volta ao clicar nele.
 */
export default function AvisoDeCookies({ botaoFlutuante = false, comSessao = false }: {
  botaoFlutuante?: boolean;
  /** dentro da plataforma: o painel oferece também encerrar a sessão */
  comSessao?: boolean;
}) {
  const [banner, setBanner] = useState(false);
  const [painel, setPainel] = useState(false);
  const [preferencias, setPreferencias] = useState(false);
  const [janelas, setJanelas] = useState(false);
  const [redefinidas, setRedefinidas] = useState(false);
  const [aberto, setAberto] = useState<string | null>(null);
  const [minimizado, setMinimizado] = useState(false);

  useEffect(() => {
    const atual = consentimento.ler();
    // recusadas em outra tela (a de login usa o mesmo cookie): nada fica guardado
    if (atual && !atual.preferencias) consentimento.apagarPreferencias();
    if (atual && !atual.janelas) consentimento.apagarJanelas();
    setPreferencias(atual?.preferencias ?? false);
    setJanelas(atual?.janelas ?? false);
    const t = setTimeout(() => setBanner(!atual), 500);
    const abrir = () => {
      const c = consentimento.ler();
      setPreferencias(c?.preferencias ?? false); setJanelas(c?.janelas ?? false); setPainel(true);
    };
    window.addEventListener(consentimento.ABRIR_PREFERENCIAS, abrir);
    return () => { clearTimeout(t); window.removeEventListener(consentimento.ABRIR_PREFERENCIAS, abrir); };
  }, []);

  const decidir = useCallback((aceitaPreferencias: boolean, aceitaJanelas: boolean = aceitaPreferencias) => {
    consentimento.salvar(aceitaPreferencias, aceitaJanelas);
    setPreferencias(aceitaPreferencias);
    setJanelas(aceitaJanelas);
    setBanner(false);
    setPainel(false);
    setMinimizado(false);
  }, []);

  const minimizar = () => { setBanner(false); setPainel(false); setMinimizado(true); };
  // do ícone: sem escolha feita, volta o aviso; com escolha, abre as preferências
  const reabrir = () => {
    setMinimizado(false);
    const atual = consentimento.ler();
    if (atual) { setPreferencias(atual.preferencias); setJanelas(atual.janelas); setPainel(true); } else setBanner(true);
  };

  const apagar = () => {
    consentimento.apagarTudo();
    window.location.reload();
  };

  useEffect(() => {
    if (!painel) return;
    const esc = (e: KeyboardEvent) => { if (e.key === "Escape") setPainel(false); };
    window.addEventListener("keydown", esc);
    return () => window.removeEventListener("keydown", esc);
  }, [painel]);

  const lista = (itens: Item[]) => (
    <ul className={styles.itens}>
      {itens.map(i => (
        <li key={i.nome}>
          <code>{i.nome}</code>
          <span>{i.para}</span>
          <small>Duração: {i.duracao}</small>
        </li>
      ))}
    </ul>
  );

  const categoria = (id: string, titulo: string, descricao: string, controle: React.ReactNode, itens?: Item[]) => (
    <div className={styles.categoria}>
      <div className={styles.categoriaTopo}>
        <button type="button" className={styles.expandir} aria-expanded={aberto === id}
                onClick={() => setAberto(aberto === id ? null : id)} disabled={!itens}>
          {itens && <ChevronDown size={15} className={styles.seta} data-aberto={aberto === id || undefined} />}
          <span><strong>{titulo}</strong><small>{descricao}</small></span>
        </button>
        {controle}
      </div>
      {itens && aberto === id && lista(itens)}
    </div>
  );

  return (
    <>
      {banner && !painel && (
        <aside className={styles.banner} role="dialog" aria-live="polite" aria-label="Aviso de cookies">
          <div className={styles.bannerTopo}>
            <Cookie size={18} className={styles.icone} />
            <strong>Privacidade e cookies</strong>
            <button type="button" className={styles.fechar} onClick={minimizar} aria-label="Minimizar" title="Minimizar">
              <Minus size={16} />
            </button>
          </div>
          <p>
            Usamos cookies <b>essenciais</b> para manter sua sessão segura. Com sua permissão, guardamos
            também <b>preferências</b> de interface neste navegador. Não usamos cookies de publicidade
            nem de rastreamento.
          </p>
          <div className={styles.acoes}>
            <button type="button" className={styles.secundario} onClick={() => setPainel(true)}>Personalizar</button>
            <button type="button" className={styles.secundario} onClick={() => decidir(false)}>Rejeitar opcionais</button>
            <button type="button" className={styles.primario} onClick={() => decidir(true)}>Aceitar todos</button>
          </div>
        </aside>
      )}

      {painel && (
          <div className={styles.painel} role="dialog" aria-labelledby="titulo-cookies">
            <header className={styles.painelTopo}>
              <Cookie size={18} className={styles.icone} />
              <h2 id="titulo-cookies">Privacidade e cookies</h2>
              <button type="button" className={styles.fechar} onClick={minimizar} aria-label="Minimizar" title="Minimizar">
                <Minus size={16} />
              </button>
              <button type="button" className={styles.fechar} onClick={() => setPainel(false)} aria-label="Fechar">
                <X size={16} />
              </button>
            </header>

            <p className={styles.intro}>
              Escolha o que este navegador pode guardar. Os essenciais não podem ser desligados: sem eles
              não há como entrar com segurança. Você pode mudar de ideia quando quiser.
            </p>

            {categoria("essenciais", "Essenciais", "Sessão, proteção e login — sempre ativos",
              <span className={styles.sempre}>Sempre ativos</span>, ESSENCIAIS)}
            {categoria("preferencias", "Preferências", "Tema e status de presença neste navegador",
              <label className={styles.chave}>
                <input type="checkbox" checked={preferencias} onChange={e => setPreferencias(e.target.checked)} />
                <span aria-hidden />
                <em className={styles.oculto}>Permitir preferências</em>
              </label>, PREFERENCIAS)}
            {categoria("janelas", "Estado das janelas", "Menu lateral e mensagens como você deixou",
              <label className={styles.chave}>
                <input type="checkbox" checked={janelas} onChange={e => setJanelas(e.target.checked)} />
                <span aria-hidden />
                <em className={styles.oculto}>Lembrar o estado das janelas</em>
              </label>, JANELAS)}
            {categoria("estatistica", "Estatística e publicidade", "Não utilizamos",
              <span className={styles.nenhum}>Não usamos</span>)}

            <div className={styles.dados}>
              <strong>Seus dados neste navegador</strong>
              <div className={styles.dadosAcoes}>
                {comSessao && (
                  <button type="button" className={styles.neutro} disabled={redefinidas}
                          onClick={() => { redefinirJanelas(); setRedefinidas(true); setTimeout(() => setRedefinidas(false), 2000); }}>
                    <RotateCcw size={14} /> {redefinidas ? "Janelas no padrão" : "Redefinir janelas ao padrão"}
                  </button>
                )}
                <button type="button" className={styles.perigo} onClick={apagar}>
                  <Trash2 size={14} /> Apagar dados locais e esta escolha
                </button>
                {comSessao && (
                  <button type="button" className={styles.perigo} onClick={() => sessaoService.sair()}>
                    <LogOut size={14} /> Sair e encerrar a sessão
                  </button>
                )}
              </div>
            </div>

            <footer className={styles.acoes}>
              <button type="button" className={styles.secundario} onClick={() => decidir(false)}>Rejeitar opcionais</button>
              <button type="button" className={styles.secundario} onClick={() => decidir(preferencias, janelas)}>Salvar escolhas</button>
              <button type="button" className={styles.primario} onClick={() => decidir(true)}>Aceitar todos</button>
            </footer>
          </div>
      )}

      {(botaoFlutuante || minimizado) && !banner && !painel && (
        <button type="button" className={styles.flutuante} onClick={reabrir}
                aria-label="Privacidade e cookies" title="Privacidade e cookies">
          <Cookie size={16} />
        </button>
      )}
    </>
  );
}
