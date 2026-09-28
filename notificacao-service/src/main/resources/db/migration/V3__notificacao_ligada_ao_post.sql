-- =========================================================================
-- V3 — A notificação sabe de que post fala
--
-- Até a 1.0.0, apagar um post não avisava ninguém: as notificações de curtida
-- e de comentário continuavam no sino, com um link para um post que não
-- existia mais. O core passa a publicar PostRemovidoV1, e este serviço precisa
-- achar, pelo id do post, o que apagar. O link guardava o id, mas dentro de um
-- texto; procurar por texto seria frágil e sem índice.
-- =========================================================================

ALTER TABLE notificacao
    ADD COLUMN post_id BIGINT;

-- As notificações já gravadas trazem o id no link ("/feed#post-42").
UPDATE notificacao
   SET post_id = CAST(substring(link FROM '^/feed#post-([0-9]+)$') AS BIGINT)
 WHERE tipo IN ('CURTIDA', 'COMENTARIO')
   AND link ~ '^/feed#post-[0-9]+$';

-- Parcial: só as notificações de post entram no índice.
CREATE INDEX idx_notificacao_post ON notificacao (post_id) WHERE post_id IS NOT NULL;

-- Lápide dos posts apagados, pelo mesmo motivo da do destinatário (V1): com
-- consumidores em paralelo, uma curtida processada depois da remoção
-- recriaria a notificação que a remoção acabou de apagar. A lápide impede.
CREATE TABLE post_removido (
    post_id       BIGINT       PRIMARY KEY,
    removido_em   TIMESTAMPTZ  NOT NULL
);
