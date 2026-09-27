#!/usr/bin/env bash
#
# Lê um número do painel de gestão do RabbitMQ.
#
#   ./demo/contar.sh messages  boletim.usuarios
#   ./demo/contar.sh consumers notificacao.eventos
#
# Existe como arquivo, e não como função dentro do roteiro, por uma razão
# mundana: os cenários esperam condições com `bash -c "..."`, e aninhar aspas
# simples, duplas e um programa Python dentro dessa string produz algo que
# ninguém consegue ler nem corrigir. Um comando com dois argumentos resolve.
#
# Imprime 0 quando não consegue ler, para que quem chama possa comparar números
# sem tratar erro — a espera vai expirar de qualquer forma se o valor nunca
# chegar ao esperado.
set -u

CAMPO="${1:-messages}"
FILA="${2:?informe a fila}"
PAINEL="${PAINEL:-http://localhost:21673}"
USUARIO="${RABBIT_USER:-infnethub}"
SENHA="${RABBIT_PASSWORD:-infnethub}"

curl -s -u "$USUARIO:$SENHA" "$PAINEL/api/queues/%2F/$FILA" \
  | python -c "import sys,json
try:
    print(json.load(sys.stdin).get('$CAMPO', 0))
except Exception:
    print(0)" 2>/dev/null || echo 0
