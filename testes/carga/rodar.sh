#!/usr/bin/env bash
#
# Roda o teste de carga (infnethub.js) contra o ambiente no ar — Compose ou
# cluster kind, os dois atendem nas mesmas portas.
#
#   ./testes/carga/rodar.sh            perfil "fumaca": 30 s, 5 usuários
#   ./testes/carga/rodar.sh escala     7 min, até 60 usuários — a carga que faz
#                                      o autoescalonamento agir; acompanhe em
#                                      outro terminal: kubectl -n infnethub get hpa -w
#
# O k6 roda em contêiner: nada a instalar. Dentro do contêiner, "localhost" é o
# próprio contêiner; o gateway e o Keycloak da máquina são alcançados por
# host.docker.internal (Docker Desktop) ou pela rede do host (Linux). O token
# continua válido nos dois casos: o Keycloak assina com o endereço fixo em
# KC_HOSTNAME, e não com o endereço pelo qual foi chamado.
set -euo pipefail

PERFIL="${1:-fumaca}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIR_HOST="$(cd "$DIR" && { pwd -W 2>/dev/null || pwd; })"

if [ "$(uname -s)" = "Linux" ] && [ -z "${WSL_DISTRO_NAME:-}" ]; then
  REDE=(--network host); HOST=localhost
else
  REDE=(); HOST=host.docker.internal
fi

MSYS_NO_PATHCONV=1 docker run --rm "${REDE[@]}" \
  -v "$DIR_HOST:/carga:ro" \
  -e PERFIL="$PERFIL" \
  -e BASE_URL="http://$HOST:21080" \
  -e KEYCLOAK_URL="http://$HOST:21180" \
  grafana/k6:2.3.0 run /carga/infnethub.js
