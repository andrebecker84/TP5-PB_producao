"use client";

import { Suspense, useState, useEffect, useCallback, SetStateAction } from "react";
import { useRouter } from "next/navigation";
import { Plus, X, Briefcase } from "lucide-react";
import { Vaga, VagaRequest } from "@/types";
import { vagaService } from "@/services/vagaService";
import { useUsuarioLogado } from "@/hooks/useCurrentUser";
import VagaCard from "@/components/vagas/VagaCard";
import VagaForm from "@/components/vagas/VagaForm";
import styles from "./page.module.css";
import { casaComBusca } from "@/utils/format";

// Mesmo padrão do feed: a última lista aparece na hora ao voltar para a página,
// e uma busca silenciosa a atualiza por trás.
let cacheVagas: Vaga[] | null = null;

function VagasContent() {
  const router = useRouter();
  const currentUser = useUsuarioLogado();
  const [query, setQuery] = useState("");
  const [vagas, setVagasState] = useState<Vaga[]>(cacheVagas ?? []);
  const [loading, setLoading] = useState(cacheVagas === null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingVaga, setEditingVaga] = useState<Vaga | null>(null);
  const [salvando, setSalvando] = useState(false);

  const setVagas = useCallback((updater: SetStateAction<Vaga[]>) => {
    setVagasState(prev => {
      const next = typeof updater === "function" ? updater(prev) : updater;
      cacheVagas = next;
      return next;
    });
  }, []);

  useEffect(() => {
    // lê query da URL no mount (deep link)
    const params = new URLSearchParams(window.location.search);
    setQuery(params.get("q")?.toLowerCase().trim() ?? "");
    // escuta evento de busca do Header
    const handler = (e: Event) => setQuery((e as CustomEvent<{query:string}>).detail.query.toLowerCase().trim());
    window.addEventListener("infnet:search", handler);
    return () => window.removeEventListener("infnet:search", handler);
  }, []);

  const buscarVagas = useCallback(async (silencioso = false) => {
    try {
      if (!silencioso) { setLoading(true); setError(null); }
      setVagas(await vagaService.listarAtivas());
      setError(null);
    } catch {
      // na atualização silenciosa a lista em cache continua válida na tela
      if (!silencioso) setError("Não foi possível carregar as vagas. Verifique se o back-end está rodando.");
    } finally { setLoading(false); }
  }, [setVagas]);

  useEffect(() => { buscarVagas(cacheVagas !== null); }, [buscarVagas]);

  const handleSalvar = async (dados: VagaRequest) => {
    setSalvando(true);
    try {
      if (editingVaga) {
        const atualizada = await vagaService.atualizar(editingVaga.id, dados);
        setVagas(prev => prev.map(v => v.id === editingVaga.id ? atualizada : v));
      } else {
        await vagaService.criar(dados);
        buscarVagas(true);
      }
      setShowForm(false); setEditingVaga(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Erro ao salvar vaga");
    } finally { setSalvando(false); }
  };

  const handleDeletar = async (id: number) => {
    if (!confirm("Deseja remover esta vaga?")) return;
    try {
      await vagaService.deletar(id);
      setVagas(prev => prev.filter(v => v.id !== id));
    } catch (err) { alert(err instanceof Error ? err.message : "Erro ao remover"); }
  };

  const filteredVagas = query
    ? vagas.filter(v =>
        casaComBusca(query, v.titulo, v.empresa, v.categoria, v.localizacao, v.descricao)
      )
    : vagas;

  return (
    <>
      <div className={styles.body}>
        <main className={styles.main}>

          <div className={styles.pageHeader}>
            <div className={styles.headIco}><Briefcase size={20} /></div>
            <div className={styles.headInfo}>
              <h1 className={styles.pageTitle}>Vagas &amp; Oportunidades</h1>
              <p className={styles.pageSubtitle}>
                Encontre estágios, empregos e oportunidades para a sua carreira
              </p>
            </div>
            <button
              className={styles.btnNova}
              onClick={() => { setEditingVaga(null); setShowForm(true); }}
            >
              <Plus size={16} /> Nova Vaga
            </button>
          </div>

          {error && (
            <div className={styles.error}>
              ⚠️ {error}
              <button onClick={() => buscarVagas()}>Tentar novamente</button>
            </div>
          )}

          {query && (
            <div className={styles.searchBanner}>
              <span>Resultados para <strong>&quot;{query}&quot;</strong> — {filteredVagas.length} encontrada{filteredVagas.length !== 1 ? "s" : ""}</span>
              <button className={styles.clearSearch} onClick={() => { setQuery(""); router.push("/vagas"); }}><X size={13} /> Limpar</button>
            </div>
          )}

          {loading ? (
            <div className={styles.grid}>
              {[...Array(4)].map((_, i) => <div key={i} className={styles.skeleton} />)}
            </div>
          ) : filteredVagas.length === 0 ? (
            <div className={styles.empty}>
              <Briefcase size={48} className={styles.emptyIcon} />
              <p>{query ? `Nenhuma vaga encontrada para "${query}".` : "Nenhuma vaga disponível no momento."}</p>
            </div>
          ) : (
            <div className={styles.grid}>
              {filteredVagas.map(vaga => (
                <VagaCard
                  key={vaga.id}
                  vaga={vaga}
                  currentUser={currentUser}
                  onEditar={() => { setEditingVaga(vaga); setShowForm(true); }}
                  onDeletar={() => handleDeletar(vaga.id)}
                />
              ))}
            </div>
          )}
        </main>
      </div>

      {showForm && (
        <VagaForm
          vagaParaEditar={editingVaga}
          currentUser={currentUser}
          salvando={salvando}
          onSalvar={handleSalvar}
          onCancelar={() => { setShowForm(false); setEditingVaga(null); }}
        />
      )}
    </>
  );
}

export default function VagasPage() {
  return (
    <Suspense>
      <VagasContent />
    </Suspense>
  );
}
