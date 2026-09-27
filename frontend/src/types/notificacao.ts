/**
 * Notificação como o notificacao-service a entrega.
 *
 * Nasce de um evento publicado no RabbitMQ — uma curtida, um comentário, uma
 * vaga nova — e não de uma chamada feita pela interface. `criadaEm` vem em
 * ISO-8601 com fuso (sufixo Z), então `new Date()` a interpreta certo em
 * qualquer horário local.
 */
/** AVISO: o comando EnviarAviso (avisos da secretaria e da saga de expurgo). */
export type TipoNotificacao = "BOAS_VINDAS" | "CURTIDA" | "COMENTARIO" | "VAGA" | "AVISO";

export interface Notificacao {
  id: number;
  tipo: TipoNotificacao;
  texto: string;
  link: string | null;
  criadaEm: string;
  lida: boolean;
}
