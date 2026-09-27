-- =========================================================================
-- V2 — Réplica local dos alunos e registro de mensagens processadas
--
-- No TP3 o boletim buscava o nome do aluno no infnethub-core, por HTTP, a cada
-- boletim aberto. Se o core estivesse fora, o boletim saía sem identificação.
-- A partir daqui o boletim guarda a sua própria cópia dos dados que usa,
-- mantida pelos eventos que o core publica. A leitura deixa de atravessar a
-- rede — e deixa de depender de o outro serviço estar no ar.
--
-- O preço é a consistência eventual: entre o commit no core e a chegada do
-- evento aqui, a réplica mostra o estado anterior. Na prática, fração de
-- segundo com os dois no ar; com o boletim fora, o tempo que ele levar para
-- voltar — e aí os eventos esperam na fila, sem se perder.
-- =========================================================================

-- Só os campos que o boletim exibe. Sem e-mail: o evento nem o transporta.
-- O id não é gerado aqui: é o mesmo id que o core deu ao Usuario, e é por ele
-- que matricula.aluno_id se relaciona com esta tabela — por valor, sem FK,
-- porque uma matrícula pode existir antes de o cadastro do aluno chegar.
CREATE TABLE aluno_replica (
    id               BIGINT        PRIMARY KEY,
    nome             VARCHAR(100)  NOT NULL,
    escola           VARCHAR(100),
    ultimo_bloco     VARCHAR(50),
    classe           VARCHAR(20),
    papel            VARCHAR(20)   NOT NULL,
    papel_descricao  VARCHAR(50),

    -- Versão do Usuario NO CORE, copiada do evento. Não é bloqueio otimista
    -- desta tabela: é o que permite descartar um fato mais velho que chegue
    -- depois de um mais novo.
    versao_origem    BIGINT        NOT NULL,
    sincronizado_em  TIMESTAMP     NOT NULL
);

-- Consumidor idempotente. A entrega do RabbitMQ é "pelo menos uma vez": uma
-- mensagem processada cujo ack se perdeu volta. A chave primária no id da
-- mensagem é o que impede o efeito de acontecer duas vezes — gravada na mesma
-- transação do efeito, as duas coisas acontecem juntas ou não acontecem.
CREATE TABLE mensagem_processada (
    mensagem_id    UUID          PRIMARY KEY,
    tipo           VARCHAR(100)  NOT NULL,
    processada_em  TIMESTAMP     NOT NULL
);
