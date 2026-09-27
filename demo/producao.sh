#!/usr/bin/env bash
#
# Roteiro de demonstração do TP5 — o Infnet Hub operando num cluster
# Kubernetes (kind), com o que a operação de verdade precisa ver.
#
#   ./demo/producao.sh            lista os cenários
#   ./demo/producao.sh 3          roda um
#   ./demo/producao.sh todos      roda os oito (o 3 leva uns 8 minutos)
#
# Nenhum cenário é encenado: os pods são removidos de verdade, a carga é real
# (k6), e o que o roteiro mostra é o que o cluster, o Prometheus, o Tempo e o
# Loki respondem. Pré-requisito: o cluster no ar (./k8s/implantar.sh).
#
# Os cenários de eventos do TP4 (fila acumulando, caixa de saída, DLQ, saga…)
# continuam em demo/cenarios.sh, para o ambiente Compose.
#
# No Windows, rode pelo Bash do Git (ver o README).
set -u

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ" || exit 1
command -v python >/dev/null 2>&1 || python() { python3 "$@"; }
export PYTHONUTF8=1 PYTHONIOENCODING=utf-8

NS=infnethub
GATEWAY="http://localhost:21080"
KEYCLOAK="http://localhost:21180"
GRAFANA="http://localhost:21300"
# A API do Grafana com a conta de administração local — a mesma dos
# segredos de desenvolvimento do overlay (k8s/overlays/local). É por ela que o
# roteiro consulta o Prometheus, o Tempo e o Loki sem abrir o navegador.
GRAFANA_ADMIN="admin:${GRAFANA_ADMIN_PASS:-admin}"

titulo() { printf '\n\033[1m== %s %s\033[0m\n' "$1" "$(printf '=%.0s' $(seq 1 $((68 - ${#1}))))"; }
passo()  { printf '\n\033[36m→ %s\033[0m\n' "$1"; }
ok()     { printf '  \033[32m✓\033[0m %s\n' "$1"; }
erro()   { printf '  \033[31m✗\033[0m %s\n' "$1"; }
nota()   { printf '    %s\n' "$1"; }

token() {
  curl -s -X POST "$KEYCLOAK/realms/infnethub/protocol/openid-connect/token" \
    -d "client_id=infnethub-dev-cli&grant_type=password&username=$1&password=infnet" \
  | python -c 'import sys,json; print(json.load(sys.stdin)["access_token"])'
}

# Uma consulta instantânea ao Prometheus, pelo Grafana. Imprime o primeiro valor.
promql() {
  curl -s -G -u "$GRAFANA_ADMIN" "$GRAFANA/api/datasources/proxy/uid/prometheus/api/v1/query" \
    --data-urlencode "query=$1" \
  | python -c 'import sys,json
r=json.load(sys.stdin)["data"]["result"]
print(r[0]["value"][1] if r else "0")'
}

# Requisições seguidas ao gateway por N segundos; conta as que não deram 200.
sondar() {
  local segundos="$1" tk="$2" fim=$((SECONDS + $1)) total=0 falhas=0 codigo
  while [ $SECONDS -lt $fim ]; do
    codigo=$(curl -s -o /dev/null -w '%{http_code}' -m 5 -H "Authorization: Bearer $tk" "$GATEWAY/api/v1/notificacoes/nao-lidas")
    total=$((total + 1)); [ "$codigo" = 200 ] || falhas=$((falhas + 1))
    sleep 0.2
  done
  echo "$total $falhas"
}

verificar_cluster() {
  if ! kubectl -n "$NS" get deploy gateway >/dev/null 2>&1; then
    echo "O cluster não está no ar. Suba com: ./k8s/implantar.sh"
    exit 1
  fi
}

# ── 1 ─────────────────────────────────────────────────────────────────────────
cenario_1() {
  titulo "1 · A implantação: o sistema inteiro num cluster"
  passo "Três nós: um de controle e dois de trabalho"
  kubectl get nodes -o wide | cut -c1-110
  passo "Os pods, e em que nó cada um roda — réplicas espalhadas entre os nós"
  kubectl -n "$NS" get pods -o wide --sort-by=.spec.nodeName | awk '{printf "  %-34s %-8s %-10s %s\n", $1, $2, $3, $7}'
  passo "Escalonamento automático configurado"
  kubectl -n "$NS" get hpa
  passo "Orçamentos de interrupção — quantos pods podem faltar numa manutenção"
  kubectl -n "$NS" get pdb
  local prontos total
  prontos=$(kubectl -n "$NS" get pods --no-headers | grep -c ' 1/1 ')
  total=$(kubectl -n "$NS" get pods --no-headers | wc -l)
  [ "$prontos" = "$total" ] && ok "$prontos de $total pods prontos" || erro "$prontos de $total pods prontos"
}

# ── 2 ─────────────────────────────────────────────────────────────────────────
cenario_2() {
  titulo "2 · Autocura: um pod some, o cluster repõe"
  local tk alvo resultado
  tk=$(token lucas.mendonca)
  alvo=$(kubectl -n "$NS" get pods -l app.kubernetes.io/name=notificacao -o jsonpath='{.items[0].metadata.name}')
  passo "Removendo $alvo enquanto as requisições continuam chegando"
  sondar 40 "$tk" > /tmp/infnethub-sonda.$$ &
  sleep 3
  kubectl -n "$NS" delete pod "$alvo" --wait=false
  nota "O ReplicaSet percebe que falta uma réplica e cria outra; o Service deixa"
  nota "de mandar tráfego ao pod que saiu e só manda ao novo quando ele passar"
  nota "na readiness."
  kubectl -n "$NS" rollout status deployment/notificacao --timeout=180s
  wait
  resultado=$(cat /tmp/infnethub-sonda.$$); rm -f /tmp/infnethub-sonda.$$
  kubectl -n "$NS" get pods -l app.kubernetes.io/name=notificacao
  set -- $resultado
  [ "$2" = 0 ] && ok "$1 requisições durante a troca, nenhuma falhou" || erro "$2 de $1 requisições falharam"
}

# ── 3 ─────────────────────────────────────────────────────────────────────────
cenario_3() {
  titulo "3 · Autoescalonamento sob carga (k6, 7 minutos)"
  nota "A carga sobe até 60 usuários simultâneos. Quando a CPU média passa de"
  nota "70% do reservado, o HorizontalPodAutoscaler cria réplicas; quando a carga"
  nota "acaba, as remove, uma por vez. Acompanhe também o painel"
  nota "\"Infnet Hub — serviços\" no Grafana ($GRAFANA)."
  kubectl -n "$NS" get hpa
  passo "Carga em andamento — réplicas a cada 20 s"
  ./testes/carga/rodar.sh escala > /tmp/infnethub-k6.$$ 2>&1 &
  local pid=$! maximo=0 atual
  while kill -0 $pid 2>/dev/null; do
    kubectl -n "$NS" get hpa --no-headers | awk '{printf "  %-12s %-16s réplicas: %s\n", $1, $3" "$4, $7}'
    atual=$(kubectl -n "$NS" get deploy gateway notificacao backend -o jsonpath='{range .items[*]}{.status.replicas}{"\n"}{end}' | paste -sd+ | bc)
    [ "$atual" -gt "$maximo" ] && maximo=$atual
    echo
    sleep 20
  done
  passo "Resultado do k6"
  grep -E "http_req_duration|http_req_failed|checks_succeeded|✓ '|✗ '" /tmp/infnethub-k6.$$ | sed 's/^/  /'
  rm -f /tmp/infnethub-k6.$$
  ok "pico de $maximo réplicas somando gateway, notificação e core (partida: 5)"
}

# ── 4 ─────────────────────────────────────────────────────────────────────────
cenario_4() {
  titulo "4 · Atualização sem interrupção, e volta atrás"
  local tk resultado
  tk=$(token lucas.mendonca)
  passo "Atualização gradual do gateway com requisições chegando"
  nota "maxUnavailable 0: um pod novo sobe e fica pronto antes de um antigo sair."
  nota "Na saída, o preStop dá ao Service o tempo de parar de mandar tráfego, e o"
  nota "Spring termina as requisições em curso (saída graciosa)."
  sondar 60 "$tk" > /tmp/infnethub-sonda.$$ &
  sleep 2
  kubectl -n "$NS" rollout restart deployment/gateway
  kubectl -n "$NS" rollout status deployment/gateway --timeout=240s
  wait
  resultado=$(cat /tmp/infnethub-sonda.$$); rm -f /tmp/infnethub-sonda.$$
  set -- $resultado
  [ "$2" = 0 ] && ok "$1 requisições durante a atualização, nenhuma falhou" || erro "$2 de $1 requisições falharam"
  passo "O histórico de versões do Deployment"
  kubectl -n "$NS" rollout history deployment/gateway | tail -4
  passo "Voltando à versão anterior (rollout undo)"
  kubectl -n "$NS" rollout undo deployment/gateway
  kubectl -n "$NS" rollout status deployment/gateway --timeout=240s
  ok "versão anterior no ar"
}

# ── 5 ─────────────────────────────────────────────────────────────────────────
TRACE_ID=""
cenario_5() {
  titulo "5 · Rastreamento: uma requisição, quatro serviços, um trace"
  local tk resposta
  tk=$(token atendimento)
  passo "Cadastrando um aluno pelo gateway"
  resposta=$(curl -s -i -X POST "$GATEWAY/api/v1/usuarios" -H "Authorization: Bearer $tk" \
    -H 'Content-Type: application/json' \
    -d "{\"nome\":\"Aluna da Demonstracao\",\"email\":\"demo-$RANDOM$RANDOM@infnet.edu.br\",\"escola\":\"Faculdade Infnet\",\"ultimoBloco\":\"Bloco 5\",\"classe\":\"26E2\"}")
  echo "$resposta" | grep -iE '^HTTP|^x-trace-id' | sed 's/^/  /'
  TRACE_ID=$(echo "$resposta" | grep -i '^x-trace-id' | awk '{print $2}' | tr -d '\r')
  nota "O gateway devolve o traceId no cabeçalho X-Trace-Id."
  passo "Aguardando o evento atravessar a caixa de saída e o RabbitMQ"
  # Os spans chegam ao Tempo em lotes, e os de cada serviço no seu ritmo
  # (até alguns segundos depois da resposta). Espera o do consumidor do
  # boletim e o da caixa de saída — os últimos do caminho — por até 60 s.
  local fim=$((SECONDS + 60)) corpo
  while [ $SECONDS -lt $fim ]; do
    corpo=$(curl -s -u "$GRAFANA_ADMIN" "$GRAFANA/api/datasources/proxy/uid/tempo/api/traces/$TRACE_ID")
    case "$corpo" in *"outbox publicar"*"boletim.usuarios receive"*|*"boletim.usuarios receive"*"outbox publicar"*) break ;; esac
    sleep 3
  done
  passo "O trace no Tempo — cada linha é um span, na ordem em que começou"
  curl -s -u "$GRAFANA_ADMIN" "$GRAFANA/api/datasources/proxy/uid/tempo/api/traces/$TRACE_ID" \
  | python -c 'import sys,json
d=json.load(sys.stdin); spans=[]
for b in d.get("batches",[]):
    svc=[a["value"].get("stringValue") for a in b["resource"]["attributes"] if a["key"]=="service.name"][0]
    for ss in b.get("scopeSpans",[]):
        for s in ss["spans"]:
            spans.append((int(s["startTimeUnixNano"]),int(s["endTimeUnixNano"]),svc,s["name"]))
spans.sort(); t0=spans[0][0] if spans else 0
for a,b,svc,n in spans: print(f"  {(a-t0)/1e6:7.0f} ms  {svc:21} {n}")
print(f"\n  {len(spans)} spans em {len(set(s[2] for s in spans))} serviços")'
  nota "No Grafana: Explore → Tempo → cole $TRACE_ID"
}

# ── 6 ─────────────────────────────────────────────────────────────────────────
cenario_6() {
  titulo "6 · Logs agregados: os logs daquela requisição, de todos os pods"
  [ -z "$TRACE_ID" ] && cenario_5
  passo "Loki: {servico=~\".+\"} | traceId=\"$TRACE_ID\""
  curl -s -G -u "$GRAFANA_ADMIN" "$GRAFANA/api/datasources/proxy/uid/loki/loki/api/v1/query_range" \
    --data-urlencode "query={servico=~\".+\"} | traceId=\"$TRACE_ID\"" --data-urlencode "limit=20" \
  | python -c 'import sys,json
d=json.load(sys.stdin)
for r in d["data"]["result"]:
    for _,linha in r["values"]:
        try: m=json.loads(linha)
        except ValueError: m={"message":linha}
        st=r["stream"]
        print("  %-32s %-5s %s" % (st.get("pod",""), st.get("nivel",""), m.get("message","")[:70]))'
  passo "Volume de logs por serviço nos últimos 5 minutos"
  curl -s -G -u "$GRAFANA_ADMIN" "$GRAFANA/api/datasources/proxy/uid/loki/loki/api/v1/query" \
    --data-urlencode 'query=sum by (servico) (count_over_time({servico=~".+"}[5m]))' \
  | python -c 'import sys,json
for r in sorted(json.load(sys.stdin)["data"]["result"], key=lambda r: -float(r["value"][1])):
    print("  %-22s %6d linhas" % (r["metric"].get("servico","?"), int(float(r["value"][1]))))'
}

# ── 7 ─────────────────────────────────────────────────────────────────────────
cenario_7() {
  titulo "7 · Alerta: um serviço fora do ar é detectado"
  nota "Com zero réplicas, o pod some e a série up desaparece — quem dispara é"
  nota "ServicoSemInstancias (infra/prometheus/regras.yml), após 1 minuto."
  passo "Reduzindo o boletim a zero réplicas"
  kubectl -n "$NS" scale deployment/boletim --replicas=0
  passo "Aguardando o Prometheus perceber (até 3 minutos)"
  local fim=$((SECONDS + 180)) disparou=0
  while [ $SECONDS -lt $fim ]; do
    if [ "$(promql 'count(ALERTS{alertname=~"ServicoForaDoAr|ServicoSemInstancias", alertstate="firing"})')" != 0 ]; then
      disparou=1; break
    fi
    sleep 10
  done
  curl -s -u "$GRAFANA_ADMIN" "$GRAFANA/api/datasources/proxy/uid/prometheus/api/v1/alerts" \
  | python -c 'import sys,json
for a in json.load(sys.stdin)["data"]["alerts"]:
    print(f"  {a[\"state\"]:8} {a[\"labels\"][\"alertname\"]:18} {a[\"annotations\"].get(\"resumo\",\"\")}")'
  [ $disparou = 1 ] && ok "alerta disparado" || erro "o alerta não disparou no prazo"
  passo "Religando o boletim"
  kubectl -n "$NS" scale deployment/boletim --replicas=1
  kubectl -n "$NS" rollout status deployment/boletim --timeout=240s
  nota "Enquanto esteve fora, os cadastros continuaram: os eventos esperaram na"
  nota "fila boletim.usuarios e foram aplicados quando ele voltou (TP4)."
}

# ── 8 ─────────────────────────────────────────────────────────────────────────
cenario_8() {
  titulo "8 · Um nó do broker cai, o cluster do RabbitMQ segue"
  local tk antes depois
  tk=$(token atendimento)
  passo "Removendo o pod rabbitmq-1"
  kubectl -n "$NS" delete pod rabbitmq-1 --wait=false
  sleep 5
  passo "Cadastrando um aluno com um nó a menos"
  antes=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GATEWAY/api/v1/usuarios" -H "Authorization: Bearer $tk" \
    -H 'Content-Type: application/json' \
    -d "{\"nome\":\"Aluno Sem Um No\",\"email\":\"no-$RANDOM$RANDOM@infnet.edu.br\",\"escola\":\"Faculdade Infnet\",\"ultimoBloco\":\"Bloco 5\",\"classe\":\"26E2\"}")
  [ "$antes" = 201 ] && ok "cadastro aceito (201) com o broker em dois nós" || erro "cadastro respondeu $antes"
  passo "O StatefulSet repõe o nó com o mesmo nome e o mesmo volume"
  kubectl -n "$NS" rollout status statefulset/rabbitmq --timeout=240s
  kubectl -n "$NS" exec rabbitmq-0 -- rabbitmq-diagnostics -q cluster_status 2>/dev/null \
    | sed -n '/Running Nodes/,/^$/p' | sed 's/^/  /'
  depois=$(promql 'count(up{job="rabbitmq"} == 1)')
  [ "$depois" = 3 ] && ok "três nós no ar de novo" || nota "nós coletados pelo Prometheus: $depois (a coleta leva até 15 s)"
}

TODOS=(1 2 3 4 5 6 7 8)
DESCRICAO=(
  "A implantação: o sistema inteiro num cluster"
  "Autocura: um pod some, o cluster repõe"
  "Autoescalonamento sob carga (k6, 7 minutos)"
  "Atualização sem interrupção, e volta atrás"
  "Rastreamento: uma requisição, quatro serviços, um trace"
  "Logs agregados: os logs daquela requisição, de todos os pods"
  "Alerta: um serviço fora do ar é detectado"
  "Um nó do broker cai, o cluster do RabbitMQ segue"
)

if [ $# -eq 0 ]; then
  echo "Cenários da demonstração — TP5 (Kubernetes)"
  for i in "${!TODOS[@]}"; do printf '  %s  %s\n' "${TODOS[$i]}" "${DESCRICAO[$i]}"; done
  echo
  echo "Uso: ./demo/producao.sh <número>   ou   ./demo/producao.sh todos"
  exit 0
fi

verificar_cluster
if [ "$1" = todos ]; then
  for c in "${TODOS[@]}"; do "cenario_$c"; done
else
  for c in "$@"; do "cenario_$c"; done
fi
