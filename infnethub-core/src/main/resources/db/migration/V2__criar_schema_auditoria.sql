-- =========================================================================
-- V2 — Schema de auditoria (Hibernate Envers)
--
-- Estrutura em duas partes:
--
--   revisao_auditoria  Uma linha por transação que alterou algo. É o "quem,
--                      quando e de onde", compartilhado por todas as entidades
--                      tocadas na mesma transação.
--
--   <tabela>_aud       Uma linha por entidade por revisão, com o snapshot
--                      completo: o estado inteiro do registro naquele ponto,
--                      não um diff. Responder "como estava em T?" é ler uma
--                      linha, sem reconstruir nada desde o início.
--
-- Colunas de controle das tabelas _aud:
--   rev          revisão em que este estado passou a valer
--   revend       revisão em que deixou de valer (NULL = estado atual)
--   revend_tstmp momento correspondente a revend
--   revtype      0 = criação, 1 = alteração, 2 = exclusão
--
-- revend/revend_tstmp são a ValidityAuditStrategy: em vez de descobrir o estado
-- vigente com um subselect de máximo por linha, a consulta vira um range scan
-- sobre [rev, revend). Custa uma escrita a mais por alteração (fechar a revisão
-- anterior) e paga isso em toda leitura de histórico.
--
-- Não há curtidas_aud: curtidas não são auditadas. Ver o javadoc de Curtida.
-- =========================================================================

-- Numeração das revisões. allocationSize=1 no mapeamento: revisões são poucas
-- em relação às leituras, e um contador sem buracos é mais legível na auditoria.
CREATE SEQUENCE seq_revisao_auditoria START WITH 1 INCREMENT BY 1;

CREATE TABLE revisao_auditoria (
    rev           INTEGER      NOT NULL,
    rev_timestamp BIGINT       NOT NULL,
    -- Autor por extenso (nome e e-mail), não uma FK para usuarios: o registro
    -- precisa continuar legível depois que o usuário for removido.
    autor         VARCHAR(150) NOT NULL,
    origem        VARCHAR(100),
    CONSTRAINT pk_revisao_auditoria PRIMARY KEY (rev)
);

CREATE INDEX idx_revisao_auditoria_timestamp ON revisao_auditoria (rev_timestamp);
CREATE INDEX idx_revisao_auditoria_autor ON revisao_auditoria (autor);

-- ── Tabelas de auditoria ─────────────────────────────────────────────────
-- Colunas de negócio são anuláveis de propósito: uma revisão de exclusão ou o
-- estado anterior a um campo obrigatório podem não ter todos os valores.

CREATE TABLE usuarios_aud (
    rev          INTEGER NOT NULL,
    revtype      SMALLINT,
    revend       INTEGER,
    revend_tstmp TIMESTAMP(6),
    id           BIGINT  NOT NULL,
    nome         VARCHAR(100),
    email        VARCHAR(150),
    escola       VARCHAR(100),
    ultimo_bloco VARCHAR(50),
    classe       VARCHAR(20),
    papel        VARCHAR(20),
    CONSTRAINT pk_usuarios_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_usuarios_aud_rev FOREIGN KEY (rev) REFERENCES revisao_auditoria (rev),
    CONSTRAINT fk_usuarios_aud_revend FOREIGN KEY (revend) REFERENCES revisao_auditoria (rev)
);

-- posts_aud não tem a coluna curtidas: o contador é valor derivado e está
-- marcado como @NotAudited — auditá-lo geraria uma revisão a cada clique.
CREATE TABLE posts_aud (
    rev          INTEGER NOT NULL,
    revtype      SMALLINT,
    revend       INTEGER,
    revend_tstmp TIMESTAMP(6),
    id           BIGINT  NOT NULL,
    titulo       VARCHAR(200),
    conteudo     TEXT,
    autor_id     BIGINT,
    CONSTRAINT pk_posts_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_posts_aud_rev FOREIGN KEY (rev) REFERENCES revisao_auditoria (rev),
    CONSTRAINT fk_posts_aud_revend FOREIGN KEY (revend) REFERENCES revisao_auditoria (rev)
);

CREATE TABLE vagas_aud (
    rev          INTEGER NOT NULL,
    revtype      SMALLINT,
    revend       INTEGER,
    revend_tstmp TIMESTAMP(6),
    id           BIGINT  NOT NULL,
    titulo       VARCHAR(150),
    empresa      VARCHAR(150),
    descricao    TEXT,
    localizacao  VARCHAR(100),
    tipo         VARCHAR(20),
    categoria    VARCHAR(100),
    -- Registra o encerramento lógico da vaga: quem a desativou e quando.
    ativo        BOOLEAN,
    criador_id   BIGINT,
    CONSTRAINT pk_vagas_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_vagas_aud_rev FOREIGN KEY (rev) REFERENCES revisao_auditoria (rev),
    CONSTRAINT fk_vagas_aud_revend FOREIGN KEY (revend) REFERENCES revisao_auditoria (rev)
);

CREATE TABLE comentarios_aud (
    rev          INTEGER NOT NULL,
    revtype      SMALLINT,
    revend       INTEGER,
    revend_tstmp TIMESTAMP(6),
    id           BIGINT  NOT NULL,
    conteudo     TEXT,
    post_id      BIGINT,
    autor_id     BIGINT,
    CONSTRAINT pk_comentarios_aud PRIMARY KEY (rev, id),
    CONSTRAINT fk_comentarios_aud_rev FOREIGN KEY (rev) REFERENCES revisao_auditoria (rev),
    CONSTRAINT fk_comentarios_aud_revend FOREIGN KEY (revend) REFERENCES revisao_auditoria (rev)
);

-- Consultar o histórico de um registro filtra por id e ordena por rev; a PK
-- (rev, id) está na ordem oposta e não serve para isso.
CREATE INDEX idx_usuarios_aud_id ON usuarios_aud (id, rev);
CREATE INDEX idx_posts_aud_id ON posts_aud (id, rev);
CREATE INDEX idx_vagas_aud_id ON vagas_aud (id, rev);
CREATE INDEX idx_comentarios_aud_id ON comentarios_aud (id, rev);
