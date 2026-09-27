#!/bin/sh
#
# Autoridade certificadora de desenvolvimento e o certificado dos serviços.
#
# Roda uma vez, no primeiro `docker compose up`, num contêiner que termina logo
# em seguida; nas subidas seguintes encontra os arquivos e não faz nada. Tudo
# vai para o volume `certificados`, montado somente leitura nos serviços.
#
# Por que gerar aqui, e não guardar os arquivos no repositório: uma chave
# privada versionada é uma chave publicada. Cada ambiente gera a sua.
#
# O que sai daqui:
#   ca.crt                    a autoridade — em quem todos confiam
#   servidor.crt/.key         a identidade dos serviços, com o nome de cada um
#                             como nome alternativo (SAN): a verificação de nome
#                             do TLS precisa bater com o endereço usado na rede
#   confianca.p12             a autoridade num formato que a JVM lê como
#                             truststore
#   postgres/, rabbitmq/,     cópias da chave com o dono e a permissão que cada
#   keycloak/, prometheus/,   servidor exige — o PostgreSQL, por exemplo, recusa
#   loki/, tempo/             subir com uma chave legível por outros usuários
#
set -eu

DESTINO=/certs
SENHA_DA_CONFIANCA="${SENHA_DA_CONFIANCA:?defina SENHA_DA_CONFIANCA}"

# Os nomes pelos quais os serviços se chamam na rede do Compose. Mudou a lista,
# mudou o arquivo: a marca abaixo faz o certificado ser refeito.
NOMES="localhost backend boletim notificacao gateway eureka keycloak \
rabbitmq-1 rabbitmq-2 rabbitmq-3 postgres postgres-boletim postgres-notificacao \
prometheus grafana frontend loki tempo alloy"
MARCA="$(echo "$NOMES" | sha256sum | cut -c1-16)"

if [ -f "$DESTINO/ca.crt" ] && [ "$(cat "$DESTINO/.marca" 2>/dev/null)" = "$MARCA" ]; then
  echo "certificados já existem — nada a fazer"
  exit 0
fi

echo "gerando a autoridade de desenvolvimento e o certificado dos serviços"
cd "$DESTINO"
rm -rf ./* ./.marca

openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out ca.key
openssl req -x509 -new -key ca.key -days 3650 -sha256 -out ca.crt \
  -subj "/CN=Infnet Hub - autoridade de desenvolvimento"

SAN="IP:127.0.0.1"
for nome in $NOMES; do SAN="$SAN,DNS:$nome"; done

openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out servidor.key
openssl req -new -key servidor.key -sha256 -out servidor.csr -subj "/CN=infnethub-servicos"

# serverAuth e clientAuth: os nós do RabbitMQ usam o mesmo certificado nos dois
# papéis quando conversam entre si, com verificação mútua.
cat > extensoes.cnf <<FIM
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = $SAN
FIM

openssl x509 -req -in servidor.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days 825 -sha256 -extfile extensoes.cnf -out servidor.crt 2>emissao.log \
  || { cat emissao.log; exit 1; }
rm -f emissao.log

# A chave da autoridade não sai daqui: com o certificado dos serviços emitido,
# ninguém mais precisa dela, e deixá-la no volume permitiria emitir outros.
rm -f ca.key ca.srl servidor.csr extensoes.cnf

keytool -importcert -noprompt -alias infnethub-ca -file ca.crt \
  -keystore confianca.p12 -storetype PKCS12 -storepass "$SENHA_DA_CONFIANCA"

# Uma cópia da chave para cada servidor que confere dono e permissão.
# Loki e Tempo (TP5) rodam como 10001, sem o grupo 0.
for servidor in postgres:70:70 rabbitmq:100:101 keycloak:1000:0 prometheus:65534:65534 \
                loki:10001:10001 tempo:10001:10001; do
  nome="${servidor%%:*}"; dono="${servidor#*:}"
  mkdir -p "$nome"
  cp servidor.crt servidor.key "$nome/"
  chown -R "$dono" "$nome"
  chmod 600 "$nome/servidor.key"
  chmod 644 "$nome/servidor.crt"
done

# Os serviços Java e o Node leem como usuário comum.
chmod 644 ca.crt servidor.crt confianca.p12
chmod 640 servidor.key
chgrp 0 servidor.key

echo "$MARCA" > .marca
echo "pronto: $(openssl x509 -in servidor.crt -noout -ext subjectAltName | tail -1 | tr -d ' ')"
