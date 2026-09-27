-- =========================================================================
-- V8 — O papel SUPORTE_TI
--
-- Quem opera a infraestrutura (filas, métricas, saúde dos serviços) não é
-- aluno, professor, secretaria nem coordenação. É outra função, com outro
-- acesso: o painel de operação. A restrição de valores da coluna `papel` é
-- refeita para aceitá-lo.
-- =========================================================================

ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_papel;

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_papel
    CHECK (papel IN ('ALUNO', 'PROFESSOR', 'SECRETARIA', 'COORDENADOR', 'SUPORTE_TI'));
