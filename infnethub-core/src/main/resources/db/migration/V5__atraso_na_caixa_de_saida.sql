-- =========================================================================
-- V5 — Atraso na caixa de saída
--
-- Um comando pode ser pedido agora para valer depois ("avise a turma quando
-- faltarem 24 h para o prazo"). O atraso não é decidido pelo relay, nem por um
-- agendador rodando dentro do serviço: viaja com a mensagem, gravado na mesma
-- transação do resto.
--
-- O relay traduz esta coluna na propriedade `expiration` da mensagem AMQP. Ela
-- é publicada numa fila sem consumidor, e quando vence, o próprio broker a
-- reencaminha para a fila de trabalho. Enquanto espera, está no RabbitMQ, e não
-- na memória de uma instância que pode ser reiniciada.
--
-- Zero é o caso comum: entregar agora. Por isso DEFAULT 0 e NOT NULL, em vez de
-- um nulo que todo leitor teria de interpretar.
-- =========================================================================

ALTER TABLE outbox_mensagem
    ADD COLUMN atraso_ms BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN outbox_mensagem.atraso_ms IS
    'Quanto a mensagem deve esperar no broker antes de ser entregue; 0 entrega imediatamente.';
