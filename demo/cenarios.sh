#!/usr/bin/env bash
#
# Roteiro de demonstração do TP4 — a arquitetura orientada a eventos vista de
# fora, em cenários que se pode reproduzir.
#
# Cada cenário responde a uma pergunta que a apresentação precisa responder, e
# nenhum deles é uma simulação: os serviços são derrubados de verdade, as
# mensagens ficam de verdade nas filas, e o que o script mostra é o que o
# RabbitMQ e os bancos respondem.
#
# Uso:  ./demo/cenarios.sh            lista os cenários
#       ./demo/cenarios.sh 1          roda um cenário
#       ./demo/cenarios.sh todos      roda todos, na ordem
#
# Pré-requisito: o ambiente no ar (`docker compose up -d`). Os cenários mexem
# nos contêineres e deixam tudo de pé ao final, inclusive quando falham.
#
set -u

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# O JSON das respostas é lido com Python. O nome do executável muda conforme o
# ambiente — "python" no Windows, "python3" no Linux e no WSL, "py" pelo
# lançador do Windows —, então se usa o primeiro que existir. Exportado porque
# várias esperas rodam em "bash -c", num processo filho.
if ! command -v python >/dev/null 2>&1; then
  if command -v python3 >/dev/null 2>&1; then
    python() { python3 "$@"; }
  elif command -v py >/dev/null 2>&1; then
    python() { py -3 "$@"; }
  else
    echo "Os cenários precisam do Python 3 (python, python3 ou py) no PATH." >&2
    exit 1
  fi
  export -f python
fi
# No Windows, o Python lê a entrada e escreve a saída na página de código do
# console, e os acentos das respostas saíam trocados ("competÃªncia"). Tudo em UTF-8.
export PYTHONUTF8=1 PYTHONIOENCODING=utf-8
cd "$RAIZ" || exit 1

# Tudo pelo gateway, como o navegador: os serviços não têm porta no host. Os
# três nomes continuam separados porque dizem a quem cada chamada se dirige —
# o gateway é que decide, pelo caminho, para onde ela vai.
GATEWAY="http://localhost:21080"
CORE="$GATEWAY"
BOLETIM="$GATEWAY"
NOTIFICACAO="$GATEWAY"
KEYCLOAK="http://localhost:21180"
PAINEL="http://localhost:21673"
PAINEL_USUARIO="${RABBIT_USER:-infnethub}"
PAINEL_SENHA="${RABBIT_PASSWORD:-infnethub}"

# ── Apoio ────────────────────────────────────────────────────────────────────

titulo()  { printf '\n\033[1m== %s %s\033[0m\n' "$1" "$(printf '=%.0s' $(seq 1 $((68 - ${#1}))))"; }
passo()   { printf '\n\033[36m→ %s\033[0m\n' "$1"; }
ok()      { printf '  \033[32m✓\033[0m %s\n' "$1"; }
erro()    { printf '  \033[31m✗\033[0m %s\n' "$1"; }
nota()    { printf '    %s\n' "$1"; }

# Envia um corpo JSON lido da entrada padrão.
#
# Por que da entrada padrão, e não em `-d '{"texto":"ação"}'`: no Git Bash do
# Windows, o MSYS reescreve os argumentos do comando para a página de código do
# sistema antes de o curl os receber. Um "ç" sai daqui em UTF-8 e chega ao
# servidor em CP1252, que não é UTF-8 válido — e a API responde 400 a um JSON
# que parece perfeito no editor. O corpo lido da entrada padrão não passa por
# essa conversão, e o mesmo script funciona no Windows, no Linux e no macOS.
#
# Pela mesma razão, os textos procurados com grep mais abaixo são trechos sem
# acento: o padrão de busca também é argumento.
postar() {
  curl -s -X POST "$1" \
    -H "Authorization: Bearer $2" \
    -H 'Content-Type: application/json' \
    --data-binary @-
}

# Publica direto no broker, pela API de gestão — usado para simular o que um
# produtor defeituoso ou uma reentrega fariam.
publicar() {
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" -X POST \
    "$PAINEL/api/exchanges/%2F/$1/publish" \
    -H 'Content-Type: application/json' \
    --data-binary @-
}

# Token pela concessão por senha, do cliente de linha de comando do realm.
# Exclusivo de desenvolvimento — ver http/00-autenticacao.http.
token() {
  curl -s -X POST "$KEYCLOAK/realms/infnethub/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "client_id=infnethub-dev-cli&grant_type=password&username=$1&password=infnet" \
  | python -c 'import sys,json; print(json.load(sys.stdin)["access_token"])'
}

# Quantas mensagens estão paradas numa fila, direto do painel de gestão.
#
# Atenção ao ler estes números numa demonstração: o painel não os calcula a
# cada pedido, ele os recolhe periodicamente (poucos segundos). Uma leitura
# logo depois de publicar pode mostrar zero para uma fila que já tem a
# mensagem. É por isso que os cenários esperam a condição virar verdadeira em
# vez de ler uma vez e concluir.
fila() {
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" "$PAINEL/api/queues/%2F/$1" \
  | python -c 'import sys,json; d=json.load(sys.stdin); print(d.get("messages",0))' 2>/dev/null || echo "?"
}

consumidores() {
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" "$PAINEL/api/queues/%2F/$1" \
  | python -c 'import sys,json; d=json.load(sys.stdin); print(d.get("consumers",0))' 2>/dev/null || echo "?"
}

esperar_saudavel() {
  local servico="$1" limite="${2:-60}" i=0
  while [ $i -lt "$limite" ]; do
    if docker compose ps --format '{{.Service}} {{.Status}}' | grep -q "^$servico .*(healthy)"; then
      return 0
    fi
    sleep 2; i=$((i + 2))
  done
  erro "$servico não ficou saudável em ${limite}s"
  return 1
}

# Os três nós do broker. Parar "o RabbitMQ" é parar os três.
NOS_DO_BROKER="rabbitmq-1 rabbitmq-2 rabbitmq-3"

subir_broker() {
  docker compose start $NOS_DO_BROKER >/dev/null 2>&1
  for no in $NOS_DO_BROKER; do esperar_saudavel "$no" 120 || return 1; done
}

# Espera uma condição virar verdadeira, em vez de dormir um tempo arbitrário.
# Um `sleep 5` esconde tanto o sistema rápido quanto o quebrado.
esperar_ate() {
  # O tempo mostrado é o do relógio, e não o número de voltas: cada volta
  # também gasta o tempo da própria verificação, e contar voltas faria um
  # prazo de 60 s parecer ter vencido em 43.
  local descricao="$1" limite="$2"; shift 2
  local inicio; inicio=$(date +%s)
  while [ $(( $(date +%s) - inicio )) -lt "$limite" ]; do
    if "$@" >/dev/null 2>&1; then
      ok "$descricao (em $(( $(date +%s) - inicio ))s)"
      return 0
    fi
    sleep 1
  done
  erro "$descricao — não aconteceu em ${limite}s"
  return 1
}

# O nome de um aluno na réplica do boletim, lido do banco dele.
#
# Não há endpoint que exponha a réplica, e é assim de propósito: ela é detalhe
# interno do boletim, não um recurso da API. Para a demonstração, ler o banco é
# a prova mais direta de que o dado atravessou a fronteira entre os serviços.
replica_nome() {
  "$RAIZ/demo/consultar.sh" boletim "SELECT nome FROM aluno_replica WHERE id = $1"
}

# A identificação do aluno no topo do boletim — a parte que no TP3 vinha do
# core por uma chamada Feign, e hoje vem da réplica local.
cabecalho_do_boletim() {
  curl -s -H "Authorization: Bearer $2" "$BOLETIM/api/v1/boletim/$1" \
  | python -c 'import sys,json
try:
    a = json.load(sys.stdin)["aluno"]
    print("nome: %s | bloco: %s | indisponivel: %s" % (a["nome"], a["ultimoBloco"], a["indisponivel"]))
except Exception:
    print("(o boletim nao respondeu)")' 2>/dev/null
}

# Consulta a caixa de saída do core. É onde a garantia do outbox aparece: a
# linha existe desde o commit e só ganha `publicado_em` depois do ack do broker.
outbox() {
  "$RAIZ/demo/consultar.sh" core "$1"
}

ambiente_no_ar() {
  # `readiness`, e não a saúde agregada: os cenários 3 e 8 derrubam o broker e o
  # boletim de propósito, e a agregada responde DOWN nesses momentos. O que
  # interessa aqui é se o gateway atende requisições.
  if ! curl -sf -o /dev/null "$GATEWAY/actuator/health/readiness"; then
    erro "O ambiente não está no ar. Suba com: docker compose up -d"
    exit 1
  fi
}

# Cria um aluno e devolve o id. E-mail único para poder rodar o roteiro de novo.
criar_aluno() {
  local nome="$1" tk="$2"
  postar "$CORE/api/v1/usuarios" "$tk" <<FIM | python -c 'import sys,json; print(json.load(sys.stdin).get("id",""))'
{"nome":"$nome","email":"$(date +%s%N)@infnet.edu.br","papel":"ALUNO",
 "escola":"Escola Superior de Tecnologia","ultimoBloco":"Bloco 4","classe":"26E2"}
FIM
}

# ── 1 · Resiliência temporal ─────────────────────────────────────────────────

cenario_1() {
  titulo "1 · O consumidor pode estar fora do ar"
  nota "No TP3, cadastrar um aluno com o boletim fora do ar deixava os dois"
  nota "bancos em desacordo para sempre. Agora o evento espera na fila."

  local tk; tk=$(token atendimento)

  passo "Derrubando o boletim-service"
  docker compose stop boletim >/dev/null 2>&1
  # `docker compose stop` volta assim que o contêiner para, mas o desligamento
  # do Spring Boot é gracioso: o consumidor continua drenando a fila por alguns
  # segundos. Sem esperar por isto, o evento seria consumido na saída e a
  # demonstração não mostraria nada.
  esperar_ate "o consumidor soltou a fila" 30 \
    bash -c "[ \"\$($RAIZ/demo/contar.sh consumers boletim.usuarios)\" = 0 ]"

  passo "Cadastrando um aluno no core — com o consumidor desligado"
  local id; id=$(criar_aluno "Marina Duarte" "$tk")
  if [ -n "$id" ]; then
    ok "aluno $id criado; o core respondeu normalmente"
    nota "Nenhuma chamada foi feita ao boletim. Ele nem sabe que existe um aluno novo."
  else
    erro "o cadastro falhou"
    return 1
  fi

  esperar_ate "o evento ficou parado na fila, esperando quem o leia" 30 \
    bash -c "[ \"\$($RAIZ/demo/contar.sh messages boletim.usuarios)\" -ge 1 ]"
  nota "Veja em $PAINEL/#/queues/%2F/boletim.usuarios"

  passo "Subindo o boletim de volta"
  docker compose start boletim >/dev/null 2>&1
  esperar_saudavel boletim 90

  esperar_ate "a fila esvaziou sozinha" 60 \
    bash -c "[ \"\$($RAIZ/demo/contar.sh messages boletim.usuarios)\" = 0 ]"

  passo "A réplica local do boletim já conhece o aluno $id"
  # Olhando direto o banco do boletim — outro banco, outro contêiner. É a
  # evidência mais direta do que o TP4 faz: o nome que o boletim mostra não vem
  # mais de uma chamada ao core, vem daqui.
  local nome; nome=$(replica_nome "$id")
  if [ "$nome" = "Marina Duarte" ]; then
    ok "banco do boletim, tabela aluno_replica: \"$nome\""
    nota "Ninguém copiou este registro à mão, e o core nunca foi consultado."
    nota "Ele chegou pelo evento que estava parado na fila há pouco."
  else
    erro "a réplica respondeu '$nome'"
  fi

  nota ""
  nota "Repare no que a tela do boletim ainda NÃO mostra: $id não tem matrícula,"
  nota "então /api/v1/boletim/$id responde 404. A identidade replicou; o"
  nota "histórico acadêmico é outro assunto, e é do boletim."
}

# ── 2 · O acoplamento desfeito ───────────────────────────────────────────────

cenario_2() {
  titulo "2 · O boletim funciona com o core fora do ar"
  nota "Este é o antes/depois do TP4. No TP3 esta tela dizia 'indisponível',"
  nota "porque o nome do aluno vinha de uma chamada Feign ao core."

  local tk; tk=$(token carlos.oliveira)

  passo "Com tudo no ar, consultando o boletim do aluno 1"
  local antes; antes=$(cabecalho_do_boletim 1 "$tk")
  ok "$antes"

  passo "Derrubando o infnethub-core"
  docker compose stop backend >/dev/null 2>&1
  ok "core fora do ar"

  passo "Consultando o mesmo boletim — sem o core"
  local depois; depois=$(cabecalho_do_boletim 1 "$tk")

  if [ "$antes" = "$depois" ] && [ "${depois#?}" != "$depois" ]; then
    ok "$depois"
    nota "Igual, e com 'indisponivel: False'. Esse campo é herança do TP3: ele"
    nota "existia para a tela avisar que o nome não pôde ser buscado. Hoje ele"
    nota "não tem como ficar verdadeiro — não há busca a falhar."
    nota "O dado está no banco do próprio boletim, posto lá por eventos, antes"
    nota "de alguém precisar dele."
  else
    erro "antes: '$antes' | depois: '$depois'"
  fi

  passo "Subindo o core de volta"
  docker compose start backend >/dev/null 2>&1
  esperar_saudavel backend 90
}

# ── 3 · Caixa de saída ───────────────────────────────────────────────────────

cenario_3() {
  titulo "3 · O broker pode estar fora do ar"
  nota "O cenário 1 mostrou o consumidor fora. Este é pior: o RabbitMQ inteiro,"
  nota "os três nós."
  nota "Sem a caixa de saída, o evento seria perdido — o usuário existiria e"
  nota "ninguém jamais ficaria sabendo."

  local tk; tk=$(token atendimento)

  passo "Derrubando o RabbitMQ — os três nós"
  docker compose stop $NOS_DO_BROKER >/dev/null 2>&1
  ok "broker fora do ar"
  nota "Um nó só não bastaria: com dois de três no ar, as filas quorum seguem"
  nota "funcionando — é o cenário 10. Aqui o broker inteiro some."

  passo "Cadastrando um aluno"
  local id; id=$(criar_aluno "Rafael Pimentel" "$tk")
  if [ -n "$id" ]; then
    ok "aluno $id criado — o cadastro não depende do broker"
    nota "O evento foi gravado na tabela outbox_mensagem, na mesma transação."
  else
    erro "o cadastro falhou — não deveria"
  fi

  # A chave do evento é o id do agregado, e ela não é única entre tipos: um
  # post de id 13 e um usuário de id 13 têm a mesma chave. Por isso o tipo
  # entra na consulta, e a linha procurada é a mais recente.
  local onde="WHERE chave = '$id' AND tipo = 'UsuarioCadastradoV1' ORDER BY id DESC LIMIT 1"

  passo "A linha está na caixa de saída, pendente, e o relay está tentando"
  esperar_ate "o relay tentou publicar, falhou, e a linha continua pendente" 30 \
    bash -c "[ \"\$($RAIZ/demo/consultar.sh \"SELECT tentativas > 0 AND publicado_em IS NULL FROM outbox_mensagem $onde\")\" = t ]"

  nota "tentativas: $(outbox "SELECT tentativas FROM outbox_mensagem $onde")"
  nota "último erro: $(outbox "SELECT left(ultimo_erro, 70) FROM outbox_mensagem $onde")"
  nota "publicado_em continua nulo — e é isso que faz o relay tentar de novo."

  passo "Subindo o RabbitMQ de volta"
  subir_broker

  esperar_ate "o evento do aluno $id saiu sozinho, sem ninguém reenviar" 90 \
    bash -c "[ \"\$($RAIZ/demo/consultar.sh \"SELECT publicado_em IS NOT NULL FROM outbox_mensagem $onde\")\" = t ]"

  nota "Ninguém clicou em 'tentar de novo'. O relay volta a cada 500 ms e"
  nota "encontra a linha ainda pendente."

  esperar_ate "e chegou à réplica do boletim" 60 \
    bash -c "[ -n \"\$($RAIZ/demo/consultar.sh boletim \"SELECT nome FROM aluno_replica WHERE id = $id\")\" ]"
}

# ── 4 · Mensagem venenosa ────────────────────────────────────────────────────

cenario_4() {
  titulo "4 · A mensagem que não dá para processar"
  nota "Sem fila de mensagens mortas, uma mensagem defeituosa é reentregue para"
  nota "sempre e trava o consumidor — as boas ficam atrás dela."

  local antes; antes=$(fila notificacao.comandos.dlq)
  nota "notificacao.comandos.dlq antes: $antes mensagem(ns)"

  passo "Publicando um comando que o serviço não sabe executar"
  # Tipo que não é contrato nenhum: o conversor entrega um objeto que nenhum
  # @RabbitHandler aceita, e o executor recusa sem devolver à fila.
  publicar infnethub.comandos >/dev/null <<'FIM'
{"properties":{"headers":{"__TypeId__":"java.util.LinkedHashMap"},
 "content_type":"application/json","message_id":"demo-veneno"},
 "routing_key":"notificacao.enviar",
 "payload":"{\"nada\":\"disto faz sentido\"}","payload_encoding":"string"}
FIM
  ok "publicado em infnethub.comandos → notificacao.enviar"

  esperar_ate "a mensagem foi parar na fila de mensagens mortas" 30 \
    bash -c "[ \"\$($RAIZ/demo/contar.sh messages notificacao.comandos.dlq)\" -gt $antes ]"

  nota "notificacao.comandos.dlq agora: $(fila notificacao.comandos.dlq) mensagem(ns)"
  nota "A fila de trabalho seguiu vazia: $(fila notificacao.comandos) — as outras"
  nota "mensagens nunca pararam. Veja o conteúdo em"
  nota "$PAINEL/#/queues/%2F/notificacao.comandos.dlq"
}

# ── 5 · Duplicata ────────────────────────────────────────────────────────────

cenario_5() {
  titulo "5 · A mesma mensagem entregue duas vezes"
  nota "A entrega é 'pelo menos uma vez', por escolha: é o que garante que nada"
  nota "se perca. O preço é a repetição, e quem paga é o consumidor."

  local tk; tk=$(token lucas.mendonca)
  local sk; sk=$(token atendimento)

  local marca="idempotencia-$(date +%s)"

  passo "Enviando um aviso para o Lucas"
  local msg; msg=$(postar "$CORE/api/v1/avisos" "$sk" <<FIM | python -c 'import sys,json; print(json.load(sys.stdin)["mensagemId"])'
{"destinatarios":[1],"texto":"Prova de $marca"}
FIM
)
  ok "aviso $msg aceito"

  esperar_ate "a notificação chegou" 30 \
    bash -c "curl -s -H 'Authorization: Bearer $tk' $NOTIFICACAO/api/v1/notificacoes | grep -q '$marca'"

  local antes; antes=$(curl -s -H "Authorization: Bearer $tk" "$NOTIFICACAO/api/v1/notificacoes" \
    | python -c "import sys,json; print(sum(1 for n in json.load(sys.stdin) if '$marca' in n['texto']))")
  nota "notificações com este texto: $antes"

  passo "Republicando o MESMO comando, com o mesmo message_id"
  # É o que o broker faria sozinho se o serviço caísse entre processar e
  # confirmar: a mensagem volta, idêntica, com o mesmo identificador.
  publicar infnethub.comandos >/dev/null <<FIM
{"properties":{"headers":{"__TypeId__":"com.andre.infnethub.contratos.comando.EnviarAvisoV1"},
 "content_type":"application/json","message_id":"$msg"},
 "routing_key":"notificacao.enviar",
 "payload":"{\"mensagemId\":\"$msg\",\"ocorridoEm\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"destinatarios\":[1],\"texto\":\"Prova de $marca\",\"link\":null,\"solicitadoPorId\":3}",
 "payload_encoding":"string"}
FIM
  ok "republicado"

  sleep 5
  local depois; depois=$(curl -s -H "Authorization: Bearer $tk" "$NOTIFICACAO/api/v1/notificacoes" \
    | python -c "import sys,json; print(sum(1 for n in json.load(sys.stdin) if '$marca' in n['texto']))")

  if [ "$antes" = "$depois" ]; then
    ok "continuam $depois notificação(ões) — a repetição não teve efeito"
    nota "A tabela mensagem_processada tem o messageId como chave primária, e o"
    nota "registro é gravado na MESMA transação do efeito. Ou os dois, ou nenhum."
  else
    erro "eram $antes e agora são $depois — a duplicata teve efeito"
  fi
}

# ── 6 · Escala horizontal ────────────────────────────────────────────────────

cenario_6() {
  titulo "6 · Três instâncias dividindo o trabalho"
  nota "Consumidores concorrentes: uma fila, várias instâncias, e o RabbitMQ"
  nota "reparte. Escalar é subir mais uma — não há configuração a mudar."

  passo "Antes: $(consumidores notificacao.eventos) consumidor(es) em notificacao.eventos"

  passo "Subindo o serviço de notificação para 3 instâncias"
  docker compose up -d --scale notificacao=3 --no-recreate notificacao >/dev/null 2>&1
  esperar_ate "os consumidores das três instâncias apareceram na fila" 90 \
    bash -c "[ \"\$($RAIZ/demo/contar.sh consumers notificacao.eventos)\" -ge 9 ]"

  nota "notificacao.eventos: $(consumidores notificacao.eventos) consumidores"
  nota "São 3 instâncias × a concorrência de cada uma. A fila é a mesma."

  passo "Filas de difusão ao vivo — uma por instância"
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" "$PAINEL/api/queues" \
    | python -c '
import sys,json
qs=[q["name"] for q in json.load(sys.stdin) if q["name"].startswith("notificacao.aovivo")]
for q in qs: print("    " + q)
print("    -> %d fila(s) exclusiva(s), uma por instancia" % len(qs))'
  nota "Aqui a mensagem vai para TODAS, e não para uma: só a instância que tem"
  nota "a conexão SSE aberta sabe para quem entregar."

  passo "Voltando a uma instância"
  docker compose up -d --scale notificacao=1 --no-recreate notificacao >/dev/null 2>&1
  ok "reduzido"
}

# ── 7 · Comando, e comando com hora marcada ──────────────────────────────────

cenario_7() {
  titulo "7 · Pedido para agora e pedido para depois"
  nota "Evento diz 'aconteceu'; comando diz 'faça'. E um comando — só um"
  nota "comando — pode ser marcado para depois."

  local sk; sk=$(token atendimento)
  local tk; tk=$(token lucas.mendonca)

  local marca="demo-$(date +%s)"

  passo "Aviso imediato"
  postar "$CORE/api/v1/avisos" "$sk" >/dev/null <<FIM
{"destinatarios":[1],"texto":"Aviso imediato $marca"}
FIM
  esperar_ate "chegou ao serviço de notificação" 30 \
    bash -c "curl -s -H 'Authorization: Bearer $tk' $NOTIFICACAO/api/v1/notificacoes | grep -q 'Aviso imediato $marca'"

  passo "Aviso agendado para daqui a 13 segundos"
  local espera; espera=$(postar "$CORE/api/v1/avisos" "$sk" <<FIM | python -c 'import sys,json; print(json.load(sys.stdin)["entrega"])'
{"destinatarios":[1],"texto":"Aviso agendado $marca","atrasoSegundos":13}
FIM
)
  ok "aceito; entrega prevista para $espera"

  sleep 2
  nota "13 s = 8 + 4 + 1: o aviso vai passar pelas filas dos níveis 3, 2 e 0 da"
  nota "sala de espera, e pular a do nível 1. Onde ele está agora:"
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" "$PAINEL/api/queues/%2F?columns=name,messages,consumers" \
    | python -c '
import sys,json
for q in sorted(json.load(sys.stdin), key=lambda q: q["name"]):
    if q["name"].startswith("espera.") and q.get("messages"):
        print("      %s  %d mensagem(ns), %d consumidor(es)" % (q["name"], q["messages"], q["consumers"]))'
  nota "Zero consumidores — ninguém lê essas filas. Quem tira a mensagem de"
  nota "cada uma é o vencimento do prazo DA FILA, e ele a leva ao nível seguinte."

  passo "Conferindo que ainda NÃO chegou"
  if curl -s -H "Authorization: Bearer $tk" "$NOTIFICACAO/api/v1/notificacoes" | grep -q "Aviso agendado $marca"; then
    erro "chegou antes da hora"
  else
    ok "ainda não chegou — está atravessando a sala de espera, no broker"
  fi

  esperar_ate "venceu o prazo e foi entregue sozinho" 45 \
    bash -c "curl -s -H 'Authorization: Bearer $tk' $NOTIFICACAO/api/v1/notificacoes | grep -q 'Aviso agendado $marca'"

  nota "Nenhum agendador rodou em serviço nenhum. O prazo estava na chave da"
  nota "mensagem, em binário, e quem o contou foi o RabbitMQ."

  # ── O problema que a cascata resolve ─────────────────────────────────────
  passo "Um aviso curto atrás de um longo"
  nota "Na forma simples do padrão — uma fila, prazo em cada mensagem —, o"
  nota "broker só olha a mensagem da frente: um aviso de 5 s atrás de um de 1 hora"
  nota "esperaria a hora inteira. Vamos agendar exatamente isso."

  postar "$CORE/api/v1/avisos" "$sk" >/dev/null <<FIM
{"destinatarios":[1],"texto":"Aviso longo $marca","atrasoSegundos":3600}
FIM
  sleep 1
  local inicio; inicio=$(date +%s)
  postar "$CORE/api/v1/avisos" "$sk" >/dev/null <<FIM
{"destinatarios":[1],"texto":"Aviso curto $marca","atrasoSegundos":5}
FIM
  ok "os dois aceitos: um para daqui a 1 h, depois outro para daqui a 5 s"

  if esperar_ate "o de 5 segundos chegou no prazo dele" 30 \
      bash -c "curl -s -H 'Authorization: Bearer $tk' $NOTIFICACAO/api/v1/notificacoes | grep -q 'Aviso curto $marca'"; then
    nota "$(( $(date +%s) - inicio )) s depois de pedido, com o aviso de uma hora na frente."
    nota "Cada nível da cascata tem UM prazo, o da fila: quem entra antes vence"
    nota "antes, e ninguém bloqueia ninguém. Ver SalaDeEspera, no módulo contratos."
  fi
}

# ── 8 · Request/Reply ────────────────────────────────────────────────────────

cenario_8() {
  titulo "8 · A pergunta que espera resposta"
  nota "A exceção do TP4, documentada como exceção: a secretaria precisa saber"
  nota "AGORA se o aluno tem matrícula, antes de excluí-lo."

  local sk; sk=$(token atendimento)

  passo "Com o boletim no ar"
  curl -s -H "Authorization: Bearer $sk" "$CORE/api/v1/usuarios/1/situacao-academica" \
    | python -c '
import sys,json
d=json.load(sys.stdin)
print("    consultado: %s | matriculas: %s | avaliacoes: %s | pode excluir: %s"
      % (d["consultado"], d["matriculas"], d["avaliacoes"], d["podeSerExcluido"]))'

  passo "Derrubando o boletim e perguntando de novo"
  docker compose stop boletim >/dev/null 2>&1
  local inicio; inicio=$(date +%s)
  curl -s -H "Authorization: Bearer $sk" "$CORE/api/v1/usuarios/1/situacao-academica" \
    | python -c '
import sys,json
d=json.load(sys.stdin)
print("    consultado: %s | pode excluir: %s" % (d["consultado"], d["podeSerExcluido"]))'
  local fim; fim=$(date +%s)

  ok "respondeu em $((fim - inicio))s — desistiu no prazo, não travou"
  nota "E, o mais importante: 'consultado: false'. A resposta admite que não"
  nota "conseguiu perguntar, em vez de dizer que nada consta. Confundir os dois"
  nota "faria a secretaria excluir alguém com matrícula ativa."

  passo "Subindo o boletim de volta"
  docker compose start boletim >/dev/null 2>&1
  esperar_saudavel boletim 90
}

# ── 9 · Saga de expurgo (LGPD) ───────────────────────────────────────────────

# O processo mais recente, numa linha legível.
resumo_do_expurgo() {
  curl -s -H "Authorization: Bearer $2" "$CORE/api/v1/usuarios/$1/expurgo" \
  | python -c '
import sys,json
e=json.load(sys.stdin)[0]
r=e["registrosMantidos"]
print("    estado: %s | pedido por: %s | registros mantidos: %s" % (e["estado"], e["solicitadoPor"], "-" if r is None else r))
if e["motivo"]: print("    motivo: %s" % e["motivo"])' 2>/dev/null
}

# O id do aviso de remoção não concluída mais recente da secretaria (0 se não
# houver). Compara-se o id, e não se procura o texto, porque execuções
# anteriores deste cenário deixaram avisos iguais — encontrar um deles não
# provaria nada sobre esta. Nem a contagem serve: a listagem tem limite, e uma
# contagem pode empacar no teto.
PY_ULTIMO_AVISO='import sys,json; print(max([n["id"] for n in json.load(sys.stdin) if "ser conclu" in n["texto"]] or [0]))'
ultimo_aviso_de_reversao() {
  curl -s -H "Authorization: Bearer $1" "$NOTIFICACAO/api/v1/notificacoes" | python -c "$PY_ULTIMO_AVISO" 2>/dev/null
}

remover() {
  curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $2" "$CORE/api/v1/usuarios/$1"
}

status_de() {
  curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $2" "$CORE/api/v1/usuarios/$1"
}

cenario_9() {
  titulo "9 · Remover uma pessoa de três bancos"
  nota "Não existe transação que alcance o core, o boletim e a notificação ao"
  nota "mesmo tempo. A remoção vira um processo: o core bloqueia, o boletim"
  nota "decide, e o core conclui — ou desfaz o que fez."

  local sk; sk=$(token atendimento)

  # ── A · o caminho feliz ─────────────────────────────────────────────────
  passo "A · Um cadastro sem vida acadêmica"
  local id; id=$(criar_aluno "Cadastro Por Engano" "$sk")
  ok "criado com id $id"
  esperar_ate "réplica do boletim recebeu o cadastro" 30 \
    bash -c "[ -n \"\$('$RAIZ/demo/consultar.sh' boletim 'SELECT id FROM aluno_replica WHERE id = $id')\" ]"

  passo "A · A secretaria pede a remoção"
  ok "DELETE respondeu $(remover "$id" "$sk") — aceito, ainda não feito"
  nota "GET logo depois: $(status_de "$id" "$sk") — bloqueado. O registro continua no"
  nota "banco; só deixou de ser alcançável. É o que permite desfazer."

  esperar_ate "o boletim anonimizou e o core concluiu" 30 \
    bash -c "[ \"\$(curl -s -H 'Authorization: Bearer $sk' $CORE/api/v1/usuarios/$id/expurgo | python -c 'import sys,json; print(json.load(sys.stdin)[0][\"estado\"])')\" = CONCLUIDO ]"
  resumo_do_expurgo "$id" "$sk"

  passo "A · O que sobrou em cada banco"
  nota "core:    $(outbox "SELECT nome || ' | ' || email || ' | removido=' || removido FROM usuarios WHERE id = $id")"
  local rep; rep=$(replica_nome "$id")
  nota "boletim: ${rep:-(nenhuma linha na réplica)}"
  nota "O cadastro não foi apagado, foi anonimizado: as chaves estrangeiras de"
  nota "posts e comentários continuam válidas, e nada identifica mais a pessoa."
  nota "É o que eliminou o 409 da exclusão do TP3."

  # ── B · a compensação ───────────────────────────────────────────────────
  passo "B · Agora o aluno 1, que tem competências em avaliação"
  nota "Antes: GET /usuarios/1 → $(status_de 1 "$sk") | réplica: $(replica_nome 1)"
  local aviso; aviso=$(ultimo_aviso_de_reversao "$sk")
  ok "DELETE respondeu $(remover 1 "$sk")"
  nota "GET logo depois: $(status_de 1 "$sk") — bloqueado, como no caminho A"

  esperar_ate "o boletim recusou e o core desfez o bloqueio" 30 \
    bash -c "[ \"\$(curl -s -H 'Authorization: Bearer $sk' $CORE/api/v1/usuarios/1/expurgo | python -c 'import sys,json; print(json.load(sys.stdin)[0][\"estado\"])')\" = REVERTIDO ]"
  resumo_do_expurgo 1 "$sk"

  passo "B · Tudo como estava"
  nota "GET /usuarios/1 → $(status_de 1 "$sk") | réplica: $(replica_nome 1)"
  esperar_ate "a secretaria foi avisada de que a remoção não foi adiante" 30 \
    bash -c "[ \"\$(curl -s -H 'Authorization: Bearer $sk' $NOTIFICACAO/api/v1/notificacoes | python -c '$PY_ULTIMO_AVISO')\" -gt $aviso ]"
  nota "A compensação é um passo do processo, não um rollback: o core executou"
  nota "outra ação que desfaz a primeira, e contou a quem pediu. Nenhum banco"
  nota "ficou em estado intermediário — nem durante, nem depois."
  nota ""
  nota "Fila da resposta: $PAINEL/#/queues/%2F/core.expurgo"

  # ── C · o prazo ─────────────────────────────────────────────────────────
  passo "C · E se o boletim nunca responder?"
  nota "Uma saga sem prazo reteria a pessoa bloqueada para sempre. Aqui o prazo"
  nota "é de um minuto — encurtado no docker-compose.yml para a demonstração;"
  nota "o padrão da aplicação é um dia."
  local id2; id2=$(criar_aluno "Cadastro Sem Resposta" "$sk")
  esperar_ate "réplica do boletim recebeu o cadastro $id2" 30 \
    bash -c "[ -n \"\$('$RAIZ/demo/consultar.sh' boletim 'SELECT id FROM aluno_replica WHERE id = $id2')\" ]"

  docker compose stop boletim >/dev/null 2>&1
  esperar_ate "boletim fora do ar, e sem consumidor na fila" 30 \
    bash -c "[ \"\$($RAIZ/demo/contar.sh consumers boletim.usuarios)\" = 0 ]"
  ok "DELETE respondeu $(remover "$id2" "$sk") — o pedido ficou na fila do boletim"

  esperar_ate "venceu o prazo e o core desfez o bloqueio sozinho" 100 \
    bash -c "[ \"\$(curl -s -H 'Authorization: Bearer $sk' $CORE/api/v1/usuarios/$id2/expurgo | python -c 'import sys,json; print(json.load(sys.stdin)[0][\"estado\"])')\" = REVERTIDO ]"
  resumo_do_expurgo "$id2" "$sk"
  nota "GET /usuarios/$id2 → $(status_de "$id2" "$sk") — a pessoa voltou a existir."

  passo "C · O boletim volta — e processa o pedido que estava esperando"
  docker compose start boletim >/dev/null 2>&1
  esperar_saudavel boletim 90
  nota "O pedido antigo chega agora: o boletim, que não sabe do prazo, anonimiza"
  nota "e confirma. O core recebe a confirmação de um processo já revertido."
  esperar_ate "o core reenviou o estado da pessoa, e a réplica foi refeita" 60 \
    bash -c "[ -n \"\$('$RAIZ/demo/consultar.sh' boletim 'SELECT id FROM aluno_replica WHERE id = $id2')\" ]"
  nota "réplica: $(replica_nome "$id2")"
  nota "Ignorar a confirmação tardia deixaria o boletim sem a pessoa para"
  nota "sempre. Responder a ela com o estado atual é a compensação da"
  nota "compensação — e é o que torna seguro ter prazo."
}

# ── 10 · Um nó do broker cai ────────────────────────────────────────────────

# O nó que lidera uma fila quorum: é ele que recebe as publicações e decide a
# ordem; os outros dois replicam.
lider_da_fila() {
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" "$PAINEL/api/queues/%2F/$1" \
    | python -c 'import sys,json; print(json.load(sys.stdin).get("leader","").replace("rabbit@",""))'
}

membros_no_ar() {
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" "$PAINEL/api/queues/%2F/$1" \
    | python -c 'import sys,json; q=json.load(sys.stdin); print("%d de %d" % (len(q.get("online",[])), len(q.get("members",[]))))'
}

cenario_10() {
  titulo "10 · Um nó do broker cai"
  nota "O cenário 3 derrubou o broker inteiro, e a caixa de saída segurou os"
  nota "eventos. Aqui cai um nó só — e não é preciso segurar nada: as filas"
  nota "quorum estão replicadas nos três, e dois de três são maioria."

  local sk; sk=$(token atendimento)
  local lider; lider=$(lider_da_fila boletim.usuarios)

  passo "Antes"
  nota "boletim.usuarios: réplicas no ar $(membros_no_ar boletim.usuarios), líder em $lider"
  nota "Conexões dos serviços por nó:"
  curl -s -u "$PAINEL_USUARIO:$PAINEL_SENHA" "$PAINEL/api/connections" \
    | python -c '
import sys,json,collections
c=collections.Counter(x["node"].replace("rabbit@","") for x in json.load(sys.stdin))
for no,n in sorted(c.items()): print("      %s  %d conexao(oes), todas TLS" % (no, n))'

  passo "Derrubando $lider — justamente o líder da fila do boletim"
  docker compose stop "$lider" >/dev/null 2>&1
  ok "$lider fora do ar"

  esperar_ate "a fila elegeu outro líder entre os dois que sobraram" 60 \
    bash -c "l=\$(curl -s -u '$PAINEL_USUARIO:$PAINEL_SENHA' '$PAINEL/api/queues/%2F/boletim.usuarios' 2>/dev/null | python -c 'import sys,json; print(json.load(sys.stdin).get(\"leader\",\"\"))' 2>/dev/null); [ -n \"\$l\" ] && [ \"\$l\" != 'rabbit@$lider' ]"
  nota "boletim.usuarios: réplicas no ar $(membros_no_ar boletim.usuarios), líder agora em $(lider_da_fila boletim.usuarios)"

  passo "Com um nó a menos, o sistema segue"
  local id; id=$(criar_aluno "Cadastro Com Um No A Menos" "$sk")
  ok "aluno $id criado"
  esperar_ate "o evento chegou à réplica do boletim, por dois nós" 60 \
    bash -c "[ -n \"\$('$RAIZ/demo/consultar.sh' boletim 'SELECT id FROM aluno_replica WHERE id = $id')\" ]"
  nota "Quem estava conectado ao nó que caiu reconectou sozinho em outro — a"
  nota "lista dos três endereços está na configuração de cada serviço."

  passo "Subindo $lider de volta"
  docker compose start "$lider" >/dev/null 2>&1
  esperar_saudavel "$lider" 120
  esperar_ate "o nó voltou a ser réplica, com as mensagens que perdeu" 60 \
    bash -c "[ \"\$(curl -s -u '$PAINEL_USUARIO:$PAINEL_SENHA' '$PAINEL/api/queues/%2F/boletim.usuarios' | python -c 'import sys,json; print(len(json.load(sys.stdin).get(\"online\",[])))')\" = 3 ]"
  nota "boletim.usuarios: réplicas no ar $(membros_no_ar boletim.usuarios)"
  nota "Ele não perdeu nada: ao voltar, recebe do líder o que foi gravado"
  nota "enquanto estava fora. Acompanhe no Grafana: http://localhost:21300"
}

# ── Despacho ─────────────────────────────────────────────────────────────────

listar() {
  cat <<'FIM'
Cenários da demonstração — TP4

  1  O consumidor pode estar fora do ar   fila acumula e é aplicada depois
  2  O boletim funciona sem o core        o acoplamento do TP3, desfeito
  3  O broker pode estar fora do ar       caixa de saída transacional
  4  A mensagem que não dá para processar recusa e fila de mensagens mortas
  5  A mesma mensagem duas vezes          consumidor idempotente
  6  Três instâncias dividindo trabalho   competing consumers e fanout
  7  Pedido para agora e para depois      comando direct e atraso por TTL+DLX
  8  A pergunta que espera resposta       request/reply, a exceção síncrona
  9  Remover uma pessoa de três bancos    saga coreografada, compensação e prazo
 10  Um nó do broker cai                  cluster de três nós, filas quorum

Uso:  ./demo/cenarios.sh 3
      ./demo/cenarios.sh todos

Painel do RabbitMQ: http://localhost:21673  (infnethub / infnethub)
Painel de operação: http://localhost:21300  (Grafana)
FIM
}

ambiente_no_ar

case "${1:-}" in
  ""|ajuda|-h|--help) listar ;;
  todos)
    for n in 1 2 3 4 5 6 7 8 9 10; do "cenario_$n"; done
    titulo "Fim"
    ;;
  [1-9]|10) "cenario_$1" ;;
  *) erro "cenário desconhecido: $1"; listar; exit 1 ;;
esac
