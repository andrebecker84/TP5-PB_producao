/**
 * Fonte única das mensagens diretas (mock da camada social). A tab bar (Header)
 * e a sidebar leem daqui — assim os contadores de não-lidas nunca divergem.
 *
 * As NOTIFICAÇÕES saíram deste arquivo no TP4: agora são reais, geradas pelo
 * notificacao-service a partir dos eventos do RabbitMQ (ver useNotificacoes).
 * As mensagens diretas continuam de demonstração — um chat entre alunos não é
 * escopo desta etapa.
 */

export interface Mensagem    { id: number; nome: string; texto: string; time: string; unread: boolean; }

export const MENSAGENS: Mensagem[] = [
  { id: 1,  nome: "Lucas Mendonça",        texto: "Oi, como vai o TP?",                        time: "5 min", unread: true  },
  { id: 2,  nome: "Prof. Carlos Oliveira", texto: "Revise o design pattern do seu TP1...",     time: "2h",    unread: true  },
  { id: 3,  nome: "Ana Beatriz Souza",     texto: "Enviei o diagrama de entidades no grupo",   time: "3h",    unread: true  },
  { id: 4,  nome: "Rafael Nunes",          texto: "Bora fechar a modelagem hoje à noite?",     time: "5h",    unread: true  },
  { id: 5,  nome: "Coord. Marina Alves",   texto: "Lembrete: entrega do TP2 nesta sexta",      time: "8h",    unread: false },
  { id: 6,  nome: "Pedro Henrique",        texto: "Consegui rodar o Flyway, valeu demais!",    time: "1d",    unread: false },
  { id: 7,  nome: "Juliana Castro",        texto: "Curti a ideia do histórico com Envers",     time: "1d",    unread: false },
  { id: 8,  nome: "Bruno Tavares",         texto: "Me chama quando puder revisar os testes",   time: "2d",    unread: false },
  { id: 9,  nome: "Grupo Escaláveis",      texto: "Fernanda: subi a collection do Bruno",      time: "3d",    unread: false },
  { id: 10, nome: "Prof. Ricardo Lima",    texto: "Ótimo progresso na camada de persistência", time: "4d",    unread: false },
];

export const MENSAGENS_NAO_LIDAS = MENSAGENS.filter(m => m.unread).length;
