-- =========================================================================
-- V2 — O aviso da secretaria entra na lista de tipos
--
-- A restrição existe para que um valor fora da enumeração não entre no banco
-- por um caminho que não passe pela aplicação. O preço é este: acrescentar um
-- tipo é uma migração, e não só uma constante a mais no código Java.
--
-- É um preço que vale — sem ela, um INSERT manual ou um serviço de outra
-- versão poderia gravar um tipo que a interface não sabe desenhar, e o defeito
-- só apareceria na tela de quem recebesse a notificação.
-- =========================================================================

ALTER TABLE notificacao
    DROP CONSTRAINT ck_notificacao_tipo;

ALTER TABLE notificacao
    ADD CONSTRAINT ck_notificacao_tipo
        CHECK (tipo IN ('BOAS_VINDAS', 'CURTIDA', 'COMENTARIO', 'VAGA', 'AVISO'));
