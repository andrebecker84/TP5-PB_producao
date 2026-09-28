#!/usr/bin/env bash
#
# Implanta o Infnet Hub num cluster Kubernetes local (kind) — a produção
# simulada do TP5.
#
#   ./k8s/implantar.sh            cria o cluster (se preciso), constrói as
#                                 imagens, carrega e implanta
#   ./k8s/implantar.sh --sem-build  reaproveita as imagens já construídas
#
# O que acontece, em ordem:
#   1. cluster kind "infnethub" (k8s/kind.yaml), se ainda não existir
#   2. metrics-server, de onde o autoescalonamento lê a CPU
#   3. imagens dos serviços construídas com os mesmos Dockerfiles do Compose
#   4. imagens carregadas nos nós do cluster (kind load): nenhum registro
#      envolvido, nada sai desta máquina
#   5. manifestos aplicados (overlay local) e espera até tudo estar pronto
#
# Compose e cluster usam as mesmas portas no 127.0.0.1: derrube um antes de
# subir o outro (docker compose stop).
#
# No Windows, rode pelo Bash do Git (ver o README).
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER=infnethub
NS=infnethub
CONSTRUIR=1
[ "${1:-}" = "--sem-build" ] && CONSTRUIR=0

passo() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }

for ferramenta in docker kind kubectl; do
  command -v "$ferramenta" >/dev/null || { echo "falta: $ferramenta"; exit 1; }
done

# ── 1. Cluster ──────────────────────────────────────────────────────────────
passo "1/5 · cluster kind '$CLUSTER'"
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "já existe — reaproveitando"
else
  if docker compose -f "$RAIZ/docker-compose.yml" ps -q 2>/dev/null | grep -q .; then
    echo "O ambiente Compose está no ar e usa as mesmas portas. Rode: docker compose stop"
    exit 1
  fi
  kind create cluster --config "$RAIZ/k8s/kind.yaml"
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

# ── 2. metrics-server ───────────────────────────────────────────────────────
passo "2/5 · metrics-server"
kubectl apply -k "$RAIZ/k8s/complementos/metrics-server" >/dev/null
echo "aplicado"

# ── 3. Imagens ──────────────────────────────────────────────────────────────
SERVICOS=(backend boletim notificacao gateway frontend)
if [ "$CONSTRUIR" = 1 ]; then
  passo "3/5 · construindo as imagens (Dockerfiles do Compose)"
  docker compose -f "$RAIZ/docker-compose.yml" build "${SERVICOS[@]}"
else
  passo "3/5 · imagens: reaproveitando as já construídas"
fi

# ── 4. Carga nos nós ────────────────────────────────────────────────────────
# Só as imagens deste projeto: as de terceiros (PostgreSQL, RabbitMQ, Keycloak,
# Grafana…) os nós baixam do registro de origem. Não é só economia: com o
# armazenamento de imagens do containerd, ativo no Docker Desktop, uma imagem
# baixada guarda só a plataforma desta máquina, e o "kind load" de uma imagem
# multiplataforma falha procurando as camadas das outras.
passo "4/5 · carregando as imagens nos nós do cluster"
for s in "${SERVICOS[@]}"; do
  kind load docker-image "infnethub-tp5-$s:latest" --name "$CLUSTER" >/dev/null 2>&1
  echo "  infnethub-tp5-$s:latest"
done

# ── 5. Manifestos ───────────────────────────────────────────────────────────
passo "5/5 · aplicando os manifestos (k8s/overlays/local)"
kubectl kustomize --load-restrictor LoadRestrictionsNone "$RAIZ/k8s/overlays/local" | kubectl apply -f -

echo
echo "Aguardando os bancos, o broker e o Keycloak…"
kubectl -n "$NS" rollout status statefulset/postgres statefulset/postgres-boletim \
  statefulset/postgres-notificacao statefulset/rabbitmq --timeout=300s
kubectl -n "$NS" rollout status deployment/keycloak --timeout=300s
echo "Aguardando os serviços…"
for d in backend boletim notificacao gateway frontend prometheus grafana alloy; do
  kubectl -n "$NS" rollout status "deployment/$d" --timeout=420s
done

kubectl -n "$NS" get pods -o wide
cat <<FIM

Pronto. No navegador:
  aplicação            http://localhost:21000
  Grafana              http://localhost:21300   (conta suporte.ti)
  painel do RabbitMQ   http://localhost:21673   (infnethub / infnethub)
  Keycloak             http://localhost:21180

Acompanhar:   kubectl -n $NS get pods,hpa -w
Remover:      kind delete cluster --name $CLUSTER
FIM
