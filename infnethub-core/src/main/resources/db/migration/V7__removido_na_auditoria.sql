-- =========================================================================
-- V7 — A coluna `removido` também na tabela de auditoria
--
-- Migração separada da V6 porque a V6 já havia sido aplicada quando o defeito
-- apareceu, e alterar uma migração já executada muda a assinatura que o Flyway
-- guarda: a subida seguinte falharia com "checksum mismatch" em qualquer
-- ambiente que já tivesse rodado a anterior. Migração é forward-only.
--
-- O DEFEITO, que vale registrar: `Usuario` é auditada pelo Hibernate Envers, e
-- toda coluna auditada existe DUAS vezes no banco — em `usuarios` e em
-- `usuarios_aud`, que guarda o valor a cada revisão. Acrescentar a coluna só na
-- primeira passa pela migração sem erro e derruba a aplicação na subida, na
-- validação do esquema:
--
--   Schema validation: missing column [removido] in table [usuarios_aud]
--
-- É uma falha que nenhum teste de unidade apanha, porque o banco em memória dos
-- testes é criado a partir das entidades. Só aparece contra o banco de verdade.
-- =========================================================================

ALTER TABLE usuarios_aud
    ADD COLUMN removido BOOLEAN;

COMMENT ON COLUMN usuarios_aud.removido IS
    'Estado de remoção em cada revisão. Sem NOT NULL: revisões anteriores à V6 não têm valor.';
