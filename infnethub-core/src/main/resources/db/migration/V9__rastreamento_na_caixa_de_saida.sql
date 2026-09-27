-- =========================================================================
-- V9 — Contexto de rastreamento na caixa de saída (TP5)
--
-- O relay publica o evento depois, em outra thread, sem saber de requisição
-- nenhuma. Guardar aqui o traceparent (W3C) de quem gerou o evento é o que
-- permite que a publicação — e o consumo, no outro serviço — apareçam no mesmo
-- trace da requisição de origem, e não como um trace novo, sem pai.
--
-- Anulável: eventos gerados fora de uma requisição (a carga inicial, o prazo
-- vencido de uma saga) não têm contexto a guardar.
-- =========================================================================

ALTER TABLE outbox_mensagem
    ADD COLUMN rastreamento VARCHAR(55);

COMMENT ON COLUMN outbox_mensagem.rastreamento IS
    'traceparent (W3C Trace Context) da requisição que gerou a mensagem; nulo fora de um rastreamento.';
