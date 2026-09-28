export type Papel = 'ALUNO' | 'PROFESSOR' | 'SECRETARIA' | 'COORDENADOR' | 'SUPORTE_TI';
export type TipoVaga = 'ESTAGIO' | 'CLT' | 'PJ' | 'TRAINEE' | 'AUTONOMO' | 'EXTERIOR';

export interface Usuario {
  id: number;
  nome: string;
  email: string;
  escola: string | null;
  ultimoBloco: string | null;
  classe: string | null;
  papel: Papel;
  papelDescricao: string;
  criadoEm: string;
}

export interface Post {
  id: number;
  titulo: string | null;
  conteudo: string;
  /** capa opcional do post — caminho servido pelo front ou URL */
  imagemUrl: string | null;
  autorId: number;
  autorNome: string;
  autorEmail: string;
  autorPapel: Papel;
  autorPapelDescricao: string;
  curtidas: number;
  totalComentarios: number;
  criadoEm: string;
  /** quem curtiu e os comentários vêm junto com o post, na resposta do feed */
  curtidores: Curtidor[];
  comentarios: Comentario[];
}

export interface Curtidor {
  usuarioId: number;
  usuarioNome: string;
}

export interface PostRequest {
  titulo?: string;
  conteudo: string;
  imagemUrl?: string | null;
}

export interface Comentario {
  id: number;
  conteudo: string;
  autorId: number;
  autorNome: string;
  autorPapel?: Papel;
  autorPapelDescricao?: string;
  criadoEm: string;
}

export interface CurtidaResponse {
  curtido: boolean;
  total: number;
}

export interface Vaga {
  id: number;
  titulo: string;
  empresa: string;
  descricao: string | null;
  localizacao: string | null;
  tipo: TipoVaga;
  tipoDescricao: string;
  categoria: string | null;
  ativo: boolean;
  criadorId: number;
  criadorNome: string;
  criadoEm: string;
  atualizadoEm: string;
}

export interface VagaRequest {
  titulo: string;
  empresa: string;
  descricao?: string;
  localizacao?: string;
  tipo: TipoVaga;
  categoria?: string;
  criadorId: number;
}
