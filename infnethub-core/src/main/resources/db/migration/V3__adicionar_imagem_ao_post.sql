-- Capa opcional do post.
--
-- Guarda só a referência (caminho ou URL), nunca o binário: a consulta do feed
-- é ordenada, paginada e lida a todo momento — carregar blob nessa linha
-- encareceria cada página do feed sem necessidade.
--
-- A coluna também entra em posts_aud porque a imagem faz parte do conteúdo
-- editorial: trocar a capa é uma edição e precisa aparecer no histórico ao lado
-- do texto. Sem isso, o Hibernate falharia na validação do schema, já que a
-- entidade é @Audited e o campo não está marcado como @NotAudited.

ALTER TABLE posts     ADD COLUMN imagem_url VARCHAR(500);
ALTER TABLE posts_aud ADD COLUMN imagem_url VARCHAR(500);
