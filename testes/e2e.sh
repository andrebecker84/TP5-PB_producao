#!/usr/bin/env bash
#
# Testes de ponta a ponta: a coleção HTTP (http/*.http) contra o sistema no ar —
# no Compose, no cluster kind da máquina, ou no cluster que o pipeline de CD
# cria. Os três publicam o gateway e o Keycloak nas mesmas portas (21080 e
# 21180), e a coleção roda sem uma linha alterada em nenhum deles.
#
# A coleção prova o sistema montado: Keycloak emitindo token, gateway roteando,
# o que um serviço publica no RabbitMQ chegando ao banco de outro, a saga de
# expurgo dando a volta pelos três serviços. Roda pelo HTTP Client da JetBrains
# em contêiner — o mesmo motor da IDE —, e as asserções de cada bloco
# `> {% … %}` são executadas como estão.
#
# Uso:  ./testes/e2e.sh [ambiente]     (padrão: gateway; ver http/http-client.env.json)
#
set -u

AMBIENTE="${1:-gateway}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGEM="jetbrains/intellij-http-client"
RELATORIOS="$RAIZ/target/http-reports"

# No Git Bash do Windows, o MSYS reescreve argumentos que parecem caminho: o
# destino "/http" do -v viraria "C:/Program Files/Git/http". A conversão é
# desligada só na linha do docker. O lado do host vai em formato Windows
# (pwd -W). Em Linux e macOS nada disso tem efeito.
RAIZ_HOST="$(cd "$RAIZ" && { pwd -W 2>/dev/null || pwd; })"

# "localhost" dentro do contêiner do cliente é o próprio contêiner. No Linux (o
# executor do CI), a rede do host resolve: o contêiner enxerga as portas da
# máquina. No Docker Desktop, -D faz o cliente trocar localhost por
# host.docker.internal.
if [ "$(uname -s)" = "Linux" ] && [ -z "${WSL_DISTRO_NAME:-}" ]; then
  REDE=(--network host); TRADUZIR=()
else
  REDE=(); TRADUZIR=(-D)
fi

# Ordem importa: a coleção encadeia identificadores entre requisições. O 00
# obtém os tokens no Keycloak; sem ele, todas as demais respondem 401.
COLECAO=(00-autenticacao.http 01-usuarios.http 02-posts.http 03-comentarios.http
         04-vagas.http 05-historico.http 06-erros.http 07-boletim.http
         08-notificacoes.http 09-avisos.http 10-consulta-boletim.http
         11-expurgo.http)

case "$AMBIENTE" in
  local) SAUDE="http://localhost:21081/actuator/health/readiness" ;;
  *)     SAUDE="http://localhost:21080/actuator/health" ;;
esac

if ! curl -sf -o /dev/null "$SAUDE"; then
  echo "O ambiente não está no ar ($SAUDE não respondeu)."
  echo "Suba com:  docker compose up -d   ou   ./k8s/implantar.sh"
  exit 1
fi

mkdir -p "$RELATORIOS"

rodar() {
  MSYS_NO_PATHCONV=1 docker run --rm "${REDE[@]}" \
    -v "$RAIZ_HOST/http:/http" \
    -v "$RAIZ_HOST/target/http-reports:/reports" \
    -w /http \
    "$IMAGEM" \
      "${TRADUZIR[@]}" \
      -e "$AMBIENTE" \
      -v http-client.env.json \
      --no-progress \
      -r /reports \
      "$@"
}

rodar "${COLECAO[@]}"
RESULTADO=$?

# Os desfechos assíncronos — a saga de expurgo e os avisos —, numa segunda
# chamada e depois de uma pausa: a saga dá a volta por core → broker → boletim
# → broker → core, os avisos passam pela caixa de saída e pelo broker, e o
# cliente HTTP não sabe esperar. O 00 é repetido porque cada chamada do cliente
# começa sem as variáveis da anterior.
#
# Esta chamada só lê, e por isso pode ser repetida: num cluster recém-criado,
# com os serviços ainda frios, a primeira volta às vezes demora mais que a
# pausa. São até seis tentativas, uma a cada 5 s; o que não chegar em 30 s é
# falha de verdade.
if [ $RESULTADO -eq 0 ]; then
  echo
  echo "Aguardando os desfechos assíncronos (saga de expurgo e avisos)…"
  for tentativa in 1 2 3 4 5 6; do
    sleep 5
    rodar 00-autenticacao.http 12-expurgo-desfecho.http 13-avisos-desfecho.http
    RESULTADO=$?
    [ $RESULTADO -eq 0 ] && break
    echo "Tentativa $tentativa: ainda não chegou tudo."
  done
fi

echo
if [ $RESULTADO -eq 0 ]; then
  echo "Coleção HTTP: OK — relatório JUnit em target/http-reports"
else
  echo "Coleção HTTP: FALHOU — detalhes em target/http-reports"
fi
exit $RESULTADO
