#!/usr/bin/env bash
#
# Verificação completa: testes automatizados + coleção HTTP contra o ambiente
# em execução.
#
# São duas naturezas de teste, e é de propósito que estejam no mesmo comando.
# Os testes de unidade, de fatia e de integração provam as regras, as consultas
# e a mensageria com o sistema desmontado em pedaços; a coleção HTTP prova o
# sistema inteiro montado — Keycloak emitindo token, gateway roteando, e o que
# um serviço publica no RabbitMQ chegando ao banco de outro. Um conjunto passa
# sem o outro, e nenhum dos dois sozinho diz que a entrega funciona.
#
# O ambiente pode ser o Compose ou o cluster kind (k8s/implantar.sh): os dois
# publicam o gateway e o Keycloak nas mesmas portas.
#
# Uso:  ./verificar.sh [ambiente]     (padrão: gateway)
#       ./verificar.sh local          serviços rodando pela IDE, sem gateway
#
set -u

AMBIENTE="${1:-gateway}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "== 1/2 · Testes automatizados =========================================="
"$RAIZ/mvnw" -q test
MAVEN=$?
if [ $MAVEN -ne 0 ]; then
  echo "Testes automatizados falharam. A coleção HTTP não foi executada."
  exit $MAVEN
fi
echo "Testes automatizados: OK"
echo

echo "== 2/2 · Coleção HTTP (ambiente: $AMBIENTE) ============================"
"$RAIZ/testes/e2e.sh" "$AMBIENTE"
