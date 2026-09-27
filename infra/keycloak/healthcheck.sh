#!/usr/bin/env bash
# Verificação de saúde do Keycloak, para o Docker Compose.
#
# A imagem do Keycloak não traz curl nem wget, e o /bin/sh dela não abre
# socket. O bash abre: /dev/tcp é um recurso do próprio shell, e com ele dá
# para falar HTTP com a porta de gestão (9000), onde o endpoint de saúde
# responde quando KC_HEALTH_ENABLED=true.
#
# Fica num arquivo, e não numa linha do docker-compose.yml, porque a requisição
# HTTP precisa de CRLF de verdade — e escapá-lo dentro do YAML é o caminho mais
# curto para um healthcheck que falha sem dizer por quê.
set -euo pipefail

exec 3<>/dev/tcp/localhost/9000
printf 'GET /health/ready HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
grep -q '"status": "UP"' <&3
