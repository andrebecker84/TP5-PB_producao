#!/usr/bin/env bash
#
# Copia a autoridade de desenvolvimento para fora do Docker, para rodar um
# serviço pela IDE contra a infraestrutura do Compose.
#
# O RabbitMQ só aceita AMQP por TLS, e o serviço que roda fora do Compose
# precisa confiar no certificado dele como os de dentro confiam. Os arquivos vão
# para target/certificados/ — fora do controle de versão, como devem ficar.
#
# Uso:  ./infra/certificados/exportar.sh
#
# Depois, na configuração de execução do serviço na IDE:
#   variável   SPRING_RABBITMQ_SSL_ENABLED=true
#   opções JVM -Djavax.net.ssl.trustStore=target/certificados/confianca.p12
#              -Djavax.net.ssl.trustStorePassword=infnethub-confianca
#              -Djavax.net.ssl.trustStoreType=PKCS12
#
set -eu

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESTINO="$RAIZ/target/certificados"
mkdir -p "$DESTINO"
cd "$RAIZ"

# O contêiner `certificados` já terminou; `run` sobe outro, com o mesmo volume,
# só para ler os arquivos. `cat` pela saída padrão evita montar pastas do host.
for arquivo in ca.crt confianca.p12; do
  MSYS_NO_PATHCONV=1 docker compose run --rm --no-deps -T --entrypoint cat certificados "/certs/$arquivo" \
    > "$DESTINO/$arquivo"
done

echo "autoridade de desenvolvimento copiada para target/certificados/"
