/**
 * Contratos do boletim-service.
 *
 * Ficam num arquivo à parte, e não em types/index.ts, porque descrevem outro
 * serviço: o índice reúne o domínio da aplicação central (usuários, posts,
 * vagas) e misturar os dois esconderia a fronteira que o TP3 introduziu. Quando
 * um dos dois contratos mudar, fica claro qual serviço mudou.
 */

export type Conceito = "DML" | "DL" | "D" | "ND";

/** Conceito nulo = competência ainda em avaliação. Diferente de ND, que reprova. */
export type ConceitoOuPendente = Conceito | null;

export type SituacaoDisciplina = "APROVADO" | "CURSANDO" | "REPROVADO";
export type StatusProgresso = "CONCLUIDO" | "EM_CURSO" | "NAO_CONCLUIDO";
export type TipoDisciplina = "REGULAR" | "PROJETO_BLOCO";
export type CategoriaAtividade = "EXTENSAO" | "ELETIVA" | "ESTAGIO" | "COMPLEMENTAR";

/**
 * Dados do aluno, vindos da réplica local do boletim-service — alimentada pelos
 * eventos que o infnethub-core publica no RabbitMQ.
 *
 * `indisponivel` é true quando o cadastro do aluno ainda não chegou por evento:
 * o boletim continua completo, só a identificação falta. A tela deve dizer isso em vez de
 * mostrar um nome vazio como se fosse o cadastro do aluno.
 */
export interface AlunoBoletim {
  id: number;
  nome: string | null;
  escola: string | null;
  ultimoBloco: string | null;
  classe: string | null;
  papel: string | null;
  papelDescricao: string | null;
  indisponivel: boolean;
}

export interface CompetenciaAvaliada {
  avaliacaoId: number;
  competenciaId: number;
  nome: string;
  ordem: number;
  conceito: ConceitoOuPendente;
  conceitoNome: string | null;
  conceitoRegra: string | null;
}

export interface Tps {
  total: number;
  entregues: number;
  atraso: number;
  pendentes: number;
}

export interface DisciplinaBoletim {
  matriculaId: number;
  disciplinaId: number;
  nome: string;
  tipo: TipoDisciplina;
  tipoDescricao: string;
  cargaHoraria: number;
  periodo: string;
  presencaPercentual: number;
  isentaFrequencia: boolean;
  tps: Tps;
  /** Pior conceito da disciplina; nulo enquanto houver competência em avaliação. */
  conceitoFinal: ConceitoOuPendente;
  situacao: SituacaoDisciplina;
  situacaoDescricao: string;
  motivoSituacao: string;
  /** Aviso do teto imposto por TP fora do prazo; nulo quando não há restrição. */
  avisoTp: string | null;
  competencias: CompetenciaAvaliada[];
}

export interface BlocoBoletim {
  numero: number;
  titulo: string;
  periodo: string;
  status: StatusProgresso;
  statusDescricao: string;
  disciplinasAprovadas: number;
  totalDisciplinas: number;
  disciplinas: DisciplinaBoletim[];
}

export interface ResumoBoletim {
  competenciasAvaliadas: number;
  emAvaliacao: number;
  dml: number;
  dl: number;
  d: number;
  nd: number;
  disciplinasAprovadas: number;
  disciplinasCursando: number;
  disciplinasReprovadas: number;
  presencaMedia: number;
  cargaHorariaAprovada: number;
  cargaHorariaExigida: number;
  presencaMinimaExigida: number;
}

export interface Boletim {
  alunoId: number;
  aluno: AlunoBoletim;
  blocos: BlocoBoletim[];
  resumo: ResumoBoletim;
}

export interface Atividade {
  id: number;
  categoria: CategoriaAtividade;
  categoriaDescricao: string;
  nome: string;
  cargaHoraria: number;
  periodo: string | null;
  status: StatusProgresso;
  statusDescricao: string;
  presencaPercentual: number | null;
}

export interface AtividadeRequest {
  categoria: CategoriaAtividade;
  nome: string;
  cargaHoraria: number;
  periodo?: string | null;
  status: StatusProgresso;
  presencaPercentual?: number | null;
}

export interface CargaCategoria {
  categoria: CategoriaAtividade;
  descricao: string;
  concluida: number;
  exigida: number;
  percentual: number;
}

export interface EvolucaoBloco {
  numeroBloco: number;
  titulo: string;
  periodo: string;
  indice: number;
  conceitoEquivalente: Conceito;
  competenciasAvaliadas: number;
}

export interface PresencaDisciplina {
  disciplina: string;
  periodo: string;
  percentual: number;
  isentaFrequencia: boolean;
  abaixoDoMinimo: boolean;
}

export interface Desempenho {
  alunoId: number;
  aluno: AlunoBoletim;
  /** Média ponderada dos conceitos (DML 100, DL 80, D 60, ND 0). */
  indiceGeral: number;
  conceitoEquivalente: Conceito;
  competenciasAvaliadas: number;
  dml: number;
  dl: number;
  d: number;
  nd: number;
  presencaGeral: number;
  entregasNoPrazo: number;
  blocoAtual: number | null;
  presencaMinimaExigida: number;
  evolucao: EvolucaoBloco[];
  presencas: PresencaDisciplina[];
  cargaHoraria: CargaCategoria[];
}

/**
 * Um degrau da escala de conceitos.
 *
 * Vem do serviço em vez de ser uma tabela fixa aqui: enquanto os textos moraram
 * nesta página, havia duas descrições do mesmo conceito no sistema.
 */
export interface EscalaConceito {
  codigo: Conceito;
  nome: string;
  regra: string;
  peso: number;
}

export interface CatalogoCompetencia {
  id: number;
  nome: string;
  ordem: number;
}

export interface CatalogoDisciplina {
  id: number;
  nome: string;
  tipo: TipoDisciplina;
  tipoDescricao: string;
  cargaHoraria: number;
  isentaFrequencia: boolean;
  competencias: CatalogoCompetencia[];
}

export interface CatalogoBloco {
  id: number;
  numero: number;
  titulo: string;
  disciplinas: CatalogoDisciplina[];
}
