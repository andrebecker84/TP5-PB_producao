#!/usr/bin/env bash
#
# Lê um único valor de um dos bancos, para os cenários provarem o que afirmam.
#
#   ./demo/consultar.sh "SELECT tentativas FROM outbox_mensagem WHERE chave = '7'"
#   ./demo/consultar.sh boletim "SELECT nome FROM aluno_replica WHERE id = 7"
#
# Sem o primeiro argumento, consulta o banco do core.
#
# Olhar o banco numa demonstração não é trapaça: é a única evidência que não
# depende de uma API intermediária. Quando o cenário afirma que a linha ficou
# pendente na caixa de saída enquanto o broker estava fora, é a tabela que
# responde — não um registro de log que poderia ser de outra hora.
#
# Existe como arquivo, e não como função, pelo mesmo motivo do contar.sh: os
# cenários esperam condições dentro de `bash -c "..."`, e SQL com aspas dentro
# dessa string vira algo ilegível.
set -u

case "${1:-}" in
  core|boletim|notificacao)
    ALVO="$1"; SQL="${2:?informe a consulta}" ;;
  *)
    ALVO="core"; SQL="${1:?informe a consulta}" ;;
esac

case "$ALVO" in
  core)        SERVICO="postgres";              USUARIO="${DB_USER:-infnethub}";             BANCO="${DB_NAME:-infnethub}" ;;
  boletim)     SERVICO="postgres-boletim";      USUARIO="${BOLETIM_DB_USER:-boletim}";       BANCO="${BOLETIM_DB_NAME:-boletim}" ;;
  notificacao) SERVICO="postgres-notificacao";  USUARIO="${NOTIFICACAO_DB_USER:-notificacao}"; BANCO="${NOTIFICACAO_DB_NAME:-notificacao}" ;;
esac

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

# -tAc: sem cabeçalho, sem alinhamento, um valor por linha. O tr tira o \r que
# o Docker no Windows acrescenta e que faria toda comparação de texto falhar.
docker compose exec -T "$SERVICO" psql -U "$USUARIO" -d "$BANCO" -tAc "$SQL" 2>/dev/null \
  | tr -d '\r' | head -1
