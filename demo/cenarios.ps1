<#
.SYNOPSIS
    Roteiro de demonstração do TP4 — a arquitetura orientada a eventos vista de fora.

.DESCRIPTION
    Equivalente PowerShell de demo/cenarios.sh, para quem não usa Git Bash.

    Cada cenário responde a uma pergunta que a apresentação precisa responder, e nenhum
    deles é simulação: os contêineres são derrubados de verdade, as mensagens ficam de
    verdade nas filas, e o que o roteiro mostra é o que o RabbitMQ e os bancos respondem.

    Esta versão é mais curta que a de bash por uma razão de ferramenta, não de conteúdo:
    Invoke-RestMethod já devolve objetos, então não é preciso um interpretador auxiliar
    para ler JSON — e o corpo das requisições não passa pela conversão de página de código
    que obriga o script bash a enviar tudo pela entrada padrão.

.PARAMETER Cenario
    Número de 1 a 10, ou "todos". Sem argumento, lista os cenários.

.EXAMPLE
    .\demo\cenarios.ps1
    .\demo\cenarios.ps1 3
    .\demo\cenarios.ps1 todos
#>

[CmdletBinding()]
param(
    [string]$Cenario = ''
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

# Sem isto, o console do Windows escreve "MendonAa" no lugar de "Mendonça".
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

# Tudo pelo gateway, como o navegador: os serviços não têm porta no host. Os três nomes
# continuam separados porque dizem a quem cada chamada se dirige.
$Gateway     = 'http://localhost:21080'
$Core        = $Gateway
$Boletim     = $Gateway
$Notificacao = $Gateway
$Keycloak    = 'http://localhost:21180'
$Painel    = 'http://localhost:21673'
# Autenticacao basica montada a mao, e nao com -Authentication/-Credential: aqueles
# parametros so existem no PowerShell 6+, e o Windows traz o 5.1 de fabrica. O cabecalho
# funciona nos dois.
$painelUsuario = if ($env:RABBIT_USER)     { $env:RABBIT_USER }     else { 'infnethub' }
$painelSenha   = if ($env:RABBIT_PASSWORD) { $env:RABBIT_PASSWORD } else { 'infnethub' }
$PainelAuth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String(
    [Text.Encoding]::ASCII.GetBytes("${painelUsuario}:${painelSenha}")) }

# Chama o docker sem deixar que a saida de diagnostico vire excecao.
#
# O docker escreve o andamento ("Container ... Stopping") na saida de ERRO, nao na padrao.
# Com $ErrorActionPreference = 'Stop', o PowerShell 5.1 transforma cada uma dessas linhas
# num erro fatal e o roteiro morre no primeiro `docker compose stop` — ainda que o comando
# tenha funcionado. Relaxar a preferencia so em volta da chamada resolve, e mantem o
# tratamento rigoroso para o resto do script.
function Invoke-Docker {
    param([switch]$Silencioso)
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $saida = & docker @args 2>&1 | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] }
        if (-not $Silencioso) { $saida }
    } finally { $ErrorActionPreference = $anterior }
}

# Os três nós do broker. Parar "o RabbitMQ" é parar os três.
$NosDoBroker = @('rabbitmq-1', 'rabbitmq-2', 'rabbitmq-3')

# ── Apoio ────────────────────────────────────────────────────────────────────

function Titulo($t) { Write-Host "`n== $t " -NoNewline -ForegroundColor White; Write-Host ('=' * [Math]::Max(0, 68 - $t.Length)) -ForegroundColor White }
function Passo($t)  { Write-Host "`n-> $t" -ForegroundColor Cyan }
function Ok($t)     { Write-Host '  [ok] ' -NoNewline -ForegroundColor Green; Write-Host $t }
function Erro($t)   { Write-Host '  [!!] ' -NoNewline -ForegroundColor Red;   Write-Host $t }
function Nota($t)   { Write-Host "    $t" -ForegroundColor DarkGray }

# Token pela concessão por senha, do cliente de linha de comando do realm.
# Exclusivo de desenvolvimento — ver http/00-autenticacao.http.
function Get-Token($usuario) {
    $corpo = @{
        client_id  = 'infnethub-dev-cli'
        grant_type = 'password'
        username   = $usuario
        password   = 'infnet'
    }
    (Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/infnethub/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' -Body $corpo).access_token
}

function Cabecalho($token) { @{ Authorization = "Bearer $token" } }

# Um número do painel de gestão do RabbitMQ.
#
# Atenção ao lê-lo numa demonstração: o painel não o calcula a cada pedido, ele o recolhe
# periodicamente. Uma leitura logo depois de publicar pode mostrar zero para uma fila que
# já tem a mensagem — por isso os cenários esperam condições, em vez de ler uma vez.
function Get-Fila($fila, $campo = 'messages') {
    try {
        $q = Invoke-RestMethod -Uri "$Painel/api/queues/%2F/$fila" -Headers $PainelAuth
        [int]$q.$campo
    } catch { 0 }
}

function Publicar($exchange, $mensagem) {
    Invoke-RestMethod -Method Post -Uri "$Painel/api/exchanges/%2F/$exchange/publish" `
        -Headers $PainelAuth `
        -ContentType 'application/json' -Body ($mensagem | ConvertTo-Json -Depth 6 -Compress) | Out-Null
}

# Lê um único valor de um dos bancos. Olhar o banco numa demonstração não é trapaça: é a
# única evidência que não depende de uma API intermediária.
function Get-Banco($alvo, $sql) {
    $cfg = switch ($alvo) {
        'boletim'     { @{ s = 'postgres-boletim';     u = 'boletim';     d = 'boletim' } }
        'notificacao' { @{ s = 'postgres-notificacao'; u = 'notificacao'; d = 'notificacao' } }
        default       { @{ s = 'postgres';             u = 'infnethub';   d = 'infnethub' } }
    }
    $linha = Invoke-Docker compose exec -T $cfg.s psql -U $cfg.u -d $cfg.d -tAc $sql | Select-Object -First 1
    if ($null -eq $linha) { '' } else { "$linha".Trim() }
}

# Espera uma condição virar verdadeira, em vez de dormir um tempo arbitrário.
# Um "sleep 5" esconde tanto o sistema rápido quanto o quebrado.
function Esperar($descricao, $limite, [scriptblock]$condicao) {
    # O tempo mostrado é o do relógio, e não o número de voltas: cada volta
    # também gasta o tempo da própria verificação.
    $relogio = [Diagnostics.Stopwatch]::StartNew()
    while ($relogio.Elapsed.TotalSeconds -lt $limite) {
        try { if (& $condicao) { Ok "$descricao (em $([int]$relogio.Elapsed.TotalSeconds)s)"; return $true } } catch { }
        Start-Sleep -Seconds 1
    }
    Erro "$descricao - nao aconteceu em ${limite}s"
    return $false
}

function Esperar-Saudavel($servico, $limite = 90) {
    Esperar "$servico voltou saudavel" $limite {
        (Invoke-Docker compose ps --format '{{.Service}} {{.Status}}') -match "^$servico .*\(healthy\)"
    } | Out-Null
}

function Novo-Aluno($nome, $token) {
    $corpo = @{
        nome = $nome; email = "$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@infnet.edu.br"
        papel = 'ALUNO'; escola = 'Escola Superior de Tecnologia'
        ultimoBloco = 'Bloco 4'; classe = '26E2'
    }
    (Invoke-RestMethod -Method Post -Uri "$Core/api/v1/usuarios" -Headers (Cabecalho $token) `
        -ContentType 'application/json; charset=utf-8' -Body ($corpo | ConvertTo-Json -Compress)).id
}

function Enviar-Aviso($token, $corpo) {
    Invoke-RestMethod -Method Post -Uri "$Core/api/v1/avisos" -Headers (Cabecalho $token) `
        -ContentType 'application/json; charset=utf-8' -Body ($corpo | ConvertTo-Json -Compress)
}

# Le uma resposta JSON decodificando-a como UTF-8, explicitamente.
#
# O Windows PowerShell 5.1 decodifica o corpo como ISO-8859-1 quando o
# Content-Type nao traz charset — e "Mendonça" chega como "MendonAa". O
# PowerShell 7 assume UTF-8 e acerta. Ler os bytes e decodifica-los aqui faz o
# roteiro mostrar a mesma coisa nas duas versoes.
function Get-Json($uri, $headers) {
    $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing
    $texto = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($texto)) { $null } else { $texto | ConvertFrom-Json }
}

function Minhas-Notificacoes($token) {
    Get-Json "$Notificacao/api/v1/notificacoes" (Cabecalho $token)
}

# ── 1 · Resiliência temporal ─────────────────────────────────────────────────

function Cenario1 {
    Titulo '1 - O consumidor pode estar fora do ar'
    Nota "No TP3, cadastrar um aluno com o boletim fora do ar deixava os dois"
    Nota "bancos em desacordo para sempre. Agora o evento espera na fila."

    $tk = Get-Token 'atendimento'

    Passo 'Derrubando o boletim-service'
    Invoke-Docker -Silencioso compose stop boletim
    # `docker compose stop` volta assim que o contêiner para, mas o desligamento do Spring
    # Boot é gracioso: o consumidor continua drenando a fila por alguns segundos. Sem
    # esperar por isto, o evento seria consumido na saída e nada apareceria.
    Esperar 'o consumidor soltou a fila' 30 { (Get-Fila 'boletim.usuarios' 'consumers') -eq 0 } | Out-Null

    Passo 'Cadastrando um aluno no core - com o consumidor desligado'
    $id = Novo-Aluno 'Marina Duarte' $tk
    Ok "aluno $id criado; o core respondeu normalmente"
    Nota 'Nenhuma chamada foi feita ao boletim. Ele nem sabe que existe um aluno novo.'

    Esperar 'o evento ficou parado na fila, esperando quem o leia' 30 { (Get-Fila 'boletim.usuarios') -ge 1 } | Out-Null
    Nota "Veja em $Painel/#/queues/%2F/boletim.usuarios"

    Passo 'Subindo o boletim de volta'
    Invoke-Docker -Silencioso compose start boletim
    Esperar-Saudavel 'boletim'
    Esperar 'a fila esvaziou sozinha' 60 { (Get-Fila 'boletim.usuarios') -eq 0 } | Out-Null

    Passo "A replica local do boletim ja conhece o aluno $id"
    $nome = Get-Banco 'boletim' "SELECT nome FROM aluno_replica WHERE id = $id"
    if ($nome -eq 'Marina Duarte') {
        Ok "banco do boletim, tabela aluno_replica: `"$nome`""
        Nota 'Ninguem copiou este registro a mao, e o core nunca foi consultado.'
    } else {
        Erro "a replica respondeu '$nome'"
    }
}

# ── 2 · O acoplamento desfeito ───────────────────────────────────────────────

function Cenario2 {
    Titulo '2 - O boletim funciona com o core fora do ar'
    Nota "Este e o antes/depois do TP4. No TP3 esta tela dizia 'indisponivel',"
    Nota 'porque o nome do aluno vinha de uma chamada Feign ao core.'

    $tk = Get-Token 'carlos.oliveira'
    function Cabecalho-Boletim {
        try {
            $a = (Get-Json "$Boletim/api/v1/boletim/1" (Cabecalho $tk)).aluno
            "nome: $($a.nome) | bloco: $($a.ultimoBloco) | indisponivel: $($a.indisponivel)"
        } catch { '(o boletim nao respondeu)' }
    }

    Passo 'Com tudo no ar, consultando o boletim do aluno 1'
    $antes = Cabecalho-Boletim
    Ok $antes

    Passo 'Derrubando o infnethub-core'
    Invoke-Docker -Silencioso compose stop backend
    Ok 'core fora do ar'

    Passo 'Consultando o mesmo boletim - sem o core'
    $depois = Cabecalho-Boletim
    if ($antes -eq $depois -and $depois -notlike '*nao respondeu*') {
        Ok $depois
        Nota "Igual, e com 'indisponivel: False'. Esse campo e heranca do TP3: existia para"
        Nota 'a tela avisar que o nome nao pode ser buscado. Hoje nao ha busca a falhar.'
    } else {
        Erro "antes: '$antes' | depois: '$depois'"
    }

    Passo 'Subindo o core de volta'
    Invoke-Docker -Silencioso compose start backend
    Esperar-Saudavel 'backend'
}

# ── 3 · Caixa de saída ───────────────────────────────────────────────────────

function Cenario3 {
    Titulo '3 - O broker pode estar fora do ar'
    Nota 'O cenario 1 mostrou o consumidor fora. Este e pior: o RabbitMQ inteiro,'
    Nota 'os tres nos.'
    Nota 'Sem a caixa de saida, o evento seria perdido - o usuario existiria e'
    Nota 'ninguem jamais ficaria sabendo.'

    $tk = Get-Token 'atendimento'

    Passo 'Derrubando o RabbitMQ - os tres nos'
    Invoke-Docker -Silencioso compose stop @NosDoBroker
    Ok 'broker fora do ar'
    Nota 'Um no so nao bastaria: com dois de tres no ar, as filas quorum seguem'
    Nota 'funcionando - e o cenario 10. Aqui o broker inteiro some.'

    Passo 'Cadastrando um aluno'
    $id = Novo-Aluno 'Rafael Pimentel' $tk
    Ok "aluno $id criado - o cadastro nao depende do broker"
    Nota 'O evento foi gravado na tabela outbox_mensagem, na mesma transacao.'

    # A chave do evento e o id do agregado, e ela nao e unica entre tipos: um post de id 13
    # e um usuario de id 13 tem a mesma chave. Por isso o tipo entra na consulta.
    $onde = "WHERE chave = '$id' AND tipo = 'UsuarioCadastradoV1' ORDER BY id DESC LIMIT 1"

    Passo 'A linha esta na caixa de saida, pendente, e o relay esta tentando'
    Esperar 'o relay tentou publicar, falhou, e a linha continua pendente' 30 {
        (Get-Banco 'core' "SELECT tentativas > 0 AND publicado_em IS NULL FROM outbox_mensagem $onde") -eq 't'
    } | Out-Null
    Nota "tentativas: $(Get-Banco 'core' "SELECT tentativas FROM outbox_mensagem $onde")"
    Nota "ultimo erro: $(Get-Banco 'core' "SELECT left(ultimo_erro, 70) FROM outbox_mensagem $onde")"
    Nota 'publicado_em continua nulo - e e isso que faz o relay tentar de novo.'

    Passo 'Subindo o RabbitMQ de volta'
    Invoke-Docker -Silencioso compose start @NosDoBroker
    foreach ($no in $NosDoBroker) { Esperar-Saudavel $no 120 }

    Esperar "o evento do aluno $id saiu sozinho, sem ninguem reenviar" 90 {
        (Get-Banco 'core' "SELECT publicado_em IS NOT NULL FROM outbox_mensagem $onde") -eq 't'
    } | Out-Null
    Nota "Ninguem clicou em 'tentar de novo'. O relay volta a cada 500 ms."

    Esperar 'e chegou a replica do boletim' 60 {
        (Get-Banco 'boletim' "SELECT nome FROM aluno_replica WHERE id = $id") -ne ''
    } | Out-Null
}

# ── 4 · Mensagem venenosa ────────────────────────────────────────────────────

function Cenario4 {
    Titulo '4 - A mensagem que nao da para processar'
    Nota 'Sem fila de mensagens mortas, uma mensagem defeituosa e reentregue para'
    Nota 'sempre e trava o consumidor - as boas ficam atras dela.'

    $antes = Get-Fila 'notificacao.comandos.dlq'
    Nota "notificacao.comandos.dlq antes: $antes mensagem(ns)"

    Passo 'Publicando um comando que o servico nao sabe executar'
    # Tipo que nao e contrato nenhum: o conversor entrega um objeto que nenhum
    # @RabbitHandler aceita, e o executor recusa sem devolver a fila.
    Publicar 'infnethub.comandos' @{
        properties = @{
            headers      = @{ '__TypeId__' = 'java.util.LinkedHashMap' }
            content_type = 'application/json'
            message_id   = 'demo-veneno'
        }
        routing_key      = 'notificacao.enviar'
        payload          = '{"nada":"disto faz sentido"}'
        payload_encoding = 'string'
    }
    Ok 'publicado em infnethub.comandos -> notificacao.enviar'

    Esperar 'a mensagem foi parar na fila de mensagens mortas' 45 {
        (Get-Fila 'notificacao.comandos.dlq') -gt $antes
    } | Out-Null

    Nota "notificacao.comandos.dlq agora: $(Get-Fila 'notificacao.comandos.dlq') mensagem(ns)"
    Nota "A fila de trabalho seguiu vazia: $(Get-Fila 'notificacao.comandos')"
    Nota "Veja o conteudo em $Painel/#/queues/%2F/notificacao.comandos.dlq"
}

# ── 5 · Duplicata ────────────────────────────────────────────────────────────

function Cenario5 {
    Titulo '5 - A mesma mensagem entregue duas vezes'
    Nota "A entrega e 'pelo menos uma vez', por escolha: e o que garante que nada"
    Nota 'se perca. O preco e a repeticao, e quem paga e o consumidor.'

    $tk = Get-Token 'lucas.mendonca'
    $sk = Get-Token 'atendimento'
    $marca = "idempotencia-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"

    Passo 'Enviando um aviso para o Lucas'
    $recibo = Enviar-Aviso $sk @{ destinatarios = @(1); texto = "Prova de $marca" }
    Ok "aviso $($recibo.mensagemId) aceito"

    Esperar 'a notificacao chegou' 30 { (Minhas-Notificacoes $tk | Where-Object { $_.texto -like "*$marca*" }).Count -ge 1 } | Out-Null
    $antes = @(Minhas-Notificacoes $tk | Where-Object { $_.texto -like "*$marca*" }).Count
    Nota "notificacoes com este texto: $antes"

    Passo 'Republicando o MESMO comando, com o mesmo message_id'
    # E o que o broker faria sozinho se o servico caisse entre processar e confirmar.
    $payload = @{
        mensagemId = $recibo.mensagemId; ocorridoEm = [DateTime]::UtcNow.ToString('o')
        destinatarios = @(1); texto = "Prova de $marca"; link = $null; solicitadoPorId = 3
    } | ConvertTo-Json -Compress
    Publicar 'infnethub.comandos' @{
        properties = @{
            headers      = @{ '__TypeId__' = 'com.andre.infnethub.contratos.comando.EnviarAvisoV1' }
            content_type = 'application/json'
            message_id   = $recibo.mensagemId
        }
        routing_key = 'notificacao.enviar'; payload = $payload; payload_encoding = 'string'
    }
    Ok 'republicado'

    Start-Sleep -Seconds 5
    $depois = @(Minhas-Notificacoes $tk | Where-Object { $_.texto -like "*$marca*" }).Count
    if ($antes -eq $depois) {
        Ok "continuam $depois notificacao(oes) - a repeticao nao teve efeito"
        Nota 'A tabela mensagem_processada tem o messageId como chave primaria, e o'
        Nota 'registro e gravado na MESMA transacao do efeito. Ou os dois, ou nenhum.'
    } else {
        Erro "eram $antes e agora sao $depois - a duplicata teve efeito"
    }
}

# ── 6 · Escala horizontal ────────────────────────────────────────────────────

function Cenario6 {
    Titulo '6 - Tres instancias dividindo o trabalho'
    Nota 'Consumidores concorrentes: uma fila, varias instancias, e o RabbitMQ'
    Nota 'reparte. Escalar e subir mais uma - nao ha configuracao a mudar.'

    Passo "Antes: $(Get-Fila 'notificacao.eventos' 'consumers') consumidor(es) em notificacao.eventos"

    Passo 'Subindo o servico de notificacao para 3 instancias'
    Invoke-Docker -Silencioso compose up -d --scale notificacao=3 --no-recreate notificacao
    Esperar 'os consumidores das tres instancias apareceram na fila' 120 {
        (Get-Fila 'notificacao.eventos' 'consumers') -ge 9
    } | Out-Null
    Nota "notificacao.eventos: $(Get-Fila 'notificacao.eventos' 'consumers') consumidores"
    Nota 'Sao 3 instancias x a concorrencia de cada uma. A fila e a mesma.'

    Passo 'Filas de difusao ao vivo - uma por instancia'
    $filas = (Invoke-RestMethod -Uri "$Painel/api/queues" -Headers $PainelAuth |
              Where-Object { $_.name -like 'notificacao.aovivo*' })
    $filas | ForEach-Object { Nota $_.name }
    Nota "-> $($filas.Count) fila(s) exclusiva(s), uma por instancia"
    Nota 'Aqui a mensagem vai para TODAS, e nao para uma: so a instancia que tem'
    Nota 'a conexao SSE aberta sabe para quem entregar.'

    Passo 'Voltando a uma instancia'
    Invoke-Docker -Silencioso compose up -d --scale notificacao=1 --no-recreate notificacao
    Ok 'reduzido'
}

# ── 7 · Comando, e comando com hora marcada ──────────────────────────────────

function Cenario7 {
    Titulo '7 - Pedido para agora e pedido para depois'
    Nota "Evento diz 'aconteceu'; comando diz 'faca'. E um comando - so um"
    Nota 'comando - pode ser marcado para depois.'

    $sk = Get-Token 'atendimento'
    $tk = Get-Token 'lucas.mendonca'
    $marca = "demo-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"

    Passo 'Aviso imediato'
    Enviar-Aviso $sk @{ destinatarios = @(1); texto = "Aviso imediato $marca" } | Out-Null
    Esperar 'chegou ao servico de notificacao' 30 {
        (Minhas-Notificacoes $tk | Where-Object { $_.texto -like "*Aviso imediato $marca*" }).Count -ge 1
    } | Out-Null

    Passo 'Aviso agendado para daqui a 13 segundos'
    $recibo = Enviar-Aviso $sk @{ destinatarios = @(1); texto = "Aviso agendado $marca"; atrasoSegundos = 13 }
    Ok "aceito; entrega prevista para $($recibo.entrega)"

    Start-Sleep -Seconds 2
    Nota '13 s = 8 + 4 + 1: o aviso vai passar pelas filas dos niveis 3, 2 e 0 da'
    Nota 'sala de espera, e pular a do nivel 1. Onde ele esta agora:'
    $filas = Invoke-RestMethod -Uri "$Painel/api/queues/%2F?columns=name,messages,consumers" -Headers $PainelAuth
    $filas | Where-Object { $_.name -like 'espera.*' -and $_.messages -gt 0 } | Sort-Object name | ForEach-Object {
        Nota "  $($_.name)  $($_.messages) mensagem(ns), $($_.consumers) consumidor(es)"
    }
    Nota 'Zero consumidores - ninguem le essas filas. Quem tira a mensagem de'
    Nota 'cada uma e o vencimento do prazo DA FILA, e ele a leva ao nivel seguinte.'

    Passo 'Conferindo que ainda NAO chegou'
    if ((Minhas-Notificacoes $tk | Where-Object { $_.texto -like "*Aviso agendado $marca*" }).Count -ge 1) {
        Erro 'chegou antes da hora'
    } else {
        Ok 'ainda nao chegou - esta atravessando a sala de espera, no broker'
    }

    Esperar 'venceu o prazo e foi entregue sozinho' 45 {
        (Minhas-Notificacoes $tk | Where-Object { $_.texto -like "*Aviso agendado $marca*" }).Count -ge 1
    } | Out-Null
    Nota 'Nenhum agendador rodou em servico nenhum. O prazo estava na chave da'
    Nota 'mensagem, em binario, e quem o contou foi o RabbitMQ.'

    Passo 'Um aviso curto atras de um longo'
    Nota 'Na forma simples do padrao - uma fila, prazo em cada mensagem -, o broker'
    Nota 'so olha a mensagem da frente: um aviso de 5 s atras de um de 1 hora'
    Nota 'esperaria a hora inteira. Vamos agendar exatamente isso.'
    Enviar-Aviso $sk @{ destinatarios = @(1); texto = "Aviso longo $marca"; atrasoSegundos = 3600 } | Out-Null
    Start-Sleep -Seconds 1
    $inicio = Get-Date
    Enviar-Aviso $sk @{ destinatarios = @(1); texto = "Aviso curto $marca"; atrasoSegundos = 5 } | Out-Null
    Ok 'os dois aceitos: um para daqui a 1 h, depois outro para daqui a 5 s'

    $chegou = Esperar 'o de 5 segundos chegou no prazo dele' 30 {
        (Minhas-Notificacoes $tk | Where-Object { $_.texto -like "*Aviso curto $marca*" }).Count -ge 1
    }
    if ($chegou) {
        Nota "$([int]((Get-Date) - $inicio).TotalSeconds) s depois de pedido, com o aviso de uma hora na frente."
        Nota 'Cada nivel da cascata tem UM prazo, o da fila: quem entra antes vence'
        Nota 'antes, e ninguem bloqueia ninguem. Ver SalaDeEspera, no modulo contratos.'
    }
}

# ── 8 · Request/Reply ────────────────────────────────────────────────────────

function Cenario8 {
    Titulo '8 - A pergunta que espera resposta'
    Nota 'A excecao do TP4, documentada como excecao: a secretaria precisa saber'
    Nota 'AGORA se o aluno tem matricula, antes de exclui-lo.'

    $sk = Get-Token 'atendimento'

    Passo 'Com o boletim no ar'
    $r = Invoke-RestMethod -Uri "$Core/api/v1/usuarios/1/situacao-academica" -Headers (Cabecalho $sk)
    Nota "consultado: $($r.consultado) | matriculas: $($r.matriculas) | avaliacoes: $($r.avaliacoes) | pode excluir: $($r.podeSerExcluido)"

    Passo 'Derrubando o boletim e perguntando de novo'
    Invoke-Docker -Silencioso compose stop boletim
    $inicio = Get-Date
    $r = Invoke-RestMethod -Uri "$Core/api/v1/usuarios/1/situacao-academica" -Headers (Cabecalho $sk)
    $decorrido = [int]((Get-Date) - $inicio).TotalSeconds
    Nota "consultado: $($r.consultado) | pode excluir: $($r.podeSerExcluido)"
    Ok "respondeu em ${decorrido}s - desistiu no prazo, nao travou"
    Nota "E, o mais importante: 'consultado: False'. A resposta admite que nao"
    Nota 'conseguiu perguntar, em vez de dizer que nada consta. Confundir os dois'
    Nota 'faria a secretaria excluir alguem com matricula ativa.'

    Passo 'Subindo o boletim de volta'
    Invoke-Docker -Silencioso compose start boletim
    Esperar-Saudavel 'boletim'
}

# ── 9 · Saga de expurgo (LGPD) ───────────────────────────────────────────────

# O código HTTP de uma requisição. No PowerShell 5.1, um 404 vira exceção; aqui
# ele é só mais uma resposta a mostrar.
function Get-Status($metodo, $uri, $token) {
    try {
        (Invoke-WebRequest -Method $metodo -Uri $uri -Headers (Cabecalho $token) -UseBasicParsing).StatusCode
    } catch {
        [int]$_.Exception.Response.StatusCode
    }
}

function Ultimo-Expurgo($id, $token) {
    # Mesma armadilha do ConvertFrom-Json no 5.1 (ver Ultimo-Aviso-De-Reversao):
    # sem a atribuição, @(...)[0] devolveria a lista inteira, e `.estado` sobre
    # ela casaria com qualquer processo antigo do histórico.
    $lista = Get-Json "$Core/api/v1/usuarios/$id/expurgo" (Cabecalho $token)
    @($lista)[0]
}

function Resumo-Expurgo($id, $token) {
    $e = Ultimo-Expurgo $id $token
    $mantidos = if ($null -eq $e.registrosMantidos) { '-' } else { $e.registrosMantidos }
    Nota "estado: $($e.estado) | pedido por: $($e.solicitadoPor) | registros mantidos: $mantidos"
    if ($e.motivo) { Nota "motivo: $($e.motivo)" }
}

# O id do aviso de remoção não concluída mais recente (0 se não houver).
# Compara-se o id, e não se procura o texto, porque execuções anteriores deste
# cenário deixaram avisos iguais; nem a contagem serve, porque a listagem tem
# limite e uma contagem pode empacar no teto.
function Ultimo-Aviso-De-Reversao($token) {
    # Atribuir antes de filtrar não é redundância: no PowerShell 5.1 o
    # ConvertFrom-Json entrega a lista inteira como UM objeto no pipeline, e o
    # filtro receberia o vetor em vez de cada notificação.
    $lista = Minhas-Notificacoes $token
    $ids = @($lista | Where-Object { $_.texto -like '*ser conclu*' } | ForEach-Object { [long]$_.id })
    if ($ids.Count -eq 0) { 0 } else { ($ids | Measure-Object -Maximum).Maximum }
}

function Cenario9 {
    Titulo '9 - Remover uma pessoa de tres bancos'
    Nota 'Nao existe transacao que alcance o core, o boletim e a notificacao ao'
    Nota 'mesmo tempo. A remocao vira um processo: o core bloqueia, o boletim'
    Nota 'decide, e o core conclui - ou desfaz o que fez.'

    $sk = Get-Token 'atendimento'

    # ── A · o caminho feliz ─────────────────────────────────────────────────
    Passo 'A - Um cadastro sem vida academica'
    $id = Novo-Aluno 'Cadastro Por Engano' $sk
    Ok "criado com id $id"
    Esperar 'replica do boletim recebeu o cadastro' 30 {
        (Get-Banco boletim "SELECT id FROM aluno_replica WHERE id = $id") -ne ''
    } | Out-Null

    Passo 'A - A secretaria pede a remocao'
    Ok "DELETE respondeu $(Get-Status Delete "$Core/api/v1/usuarios/$id" $sk) - aceito, ainda nao feito"
    Nota "GET logo depois: $(Get-Status Get "$Core/api/v1/usuarios/$id" $sk) - bloqueado. O registro continua no"
    Nota 'banco; so deixou de ser alcancavel. E o que permite desfazer.'

    Esperar 'o boletim anonimizou e o core concluiu' 30 {
        (Ultimo-Expurgo $id $sk).estado -eq 'CONCLUIDO'
    } | Out-Null
    Resumo-Expurgo $id $sk

    Passo 'A - O que sobrou em cada banco'
    Nota "core:    $(Get-Banco core "SELECT nome || ' | ' || email || ' | removido=' || removido FROM usuarios WHERE id = $id")"
    $rep = Get-Banco boletim "SELECT nome FROM aluno_replica WHERE id = $id"
    Nota "boletim: $(if ($rep) { $rep } else { '(nenhuma linha na replica)' })"
    Nota 'O cadastro nao foi apagado, foi anonimizado: as chaves estrangeiras de'
    Nota 'posts e comentarios continuam validas, e nada identifica mais a pessoa.'
    Nota 'E o que eliminou o 409 da exclusao do TP3.'

    # ── B · a compensação ───────────────────────────────────────────────────
    Passo 'B - Agora o aluno 1, que tem competencias em avaliacao'
    Nota "Antes: GET /usuarios/1 -> $(Get-Status Get "$Core/api/v1/usuarios/1" $sk) | replica: $(Get-Banco boletim 'SELECT nome FROM aluno_replica WHERE id = 1')"
    $aviso = Ultimo-Aviso-De-Reversao $sk
    Ok "DELETE respondeu $(Get-Status Delete "$Core/api/v1/usuarios/1" $sk)"
    Nota "GET logo depois: $(Get-Status Get "$Core/api/v1/usuarios/1" $sk) - bloqueado, como no caminho A"

    Esperar 'o boletim recusou e o core desfez o bloqueio' 30 {
        (Ultimo-Expurgo 1 $sk).estado -eq 'REVERTIDO'
    } | Out-Null
    Resumo-Expurgo 1 $sk

    Passo 'B - Tudo como estava'
    Nota "GET /usuarios/1 -> $(Get-Status Get "$Core/api/v1/usuarios/1" $sk) | replica: $(Get-Banco boletim 'SELECT nome FROM aluno_replica WHERE id = 1')"
    Esperar 'a secretaria foi avisada de que a remocao nao foi adiante' 30 {
        (Ultimo-Aviso-De-Reversao $sk) -gt $aviso
    } | Out-Null
    Nota 'A compensacao e um passo do processo, nao um rollback: o core executou'
    Nota 'outra acao que desfaz a primeira, e contou a quem pediu. Nenhum banco'
    Nota 'ficou em estado intermediario - nem durante, nem depois.'
    Nota ''
    Nota "Fila da resposta: $Painel/#/queues/%2F/core.expurgo"

    # ── C · o prazo ─────────────────────────────────────────────────────────
    Passo 'C - E se o boletim nunca responder?'
    Nota 'Uma saga sem prazo reteria a pessoa bloqueada para sempre. Aqui o prazo'
    Nota 'e de um minuto - encurtado no docker-compose.yml para a demonstracao;'
    Nota 'o padrao da aplicacao e um dia.'
    $id2 = Novo-Aluno 'Cadastro Sem Resposta' $sk
    Esperar "replica do boletim recebeu o cadastro $id2" 30 {
        (Get-Banco boletim "SELECT id FROM aluno_replica WHERE id = $id2") -ne ''
    } | Out-Null

    Invoke-Docker -Silencioso compose stop boletim
    Esperar 'boletim fora do ar, e sem consumidor na fila' 30 {
        (Get-Fila 'boletim.usuarios' 'consumers') -eq 0
    } | Out-Null
    Ok "DELETE respondeu $(Get-Status Delete "$Core/api/v1/usuarios/$id2" $sk) - o pedido ficou na fila do boletim"

    Esperar 'venceu o prazo e o core desfez o bloqueio sozinho' 100 {
        (Ultimo-Expurgo $id2 $sk).estado -eq 'REVERTIDO'
    } | Out-Null
    Resumo-Expurgo $id2 $sk
    Nota "GET /usuarios/$id2 -> $(Get-Status Get "$Core/api/v1/usuarios/$id2" $sk) - a pessoa voltou a existir."

    Passo 'C - O boletim volta, e processa o pedido que estava esperando'
    Invoke-Docker -Silencioso compose start boletim
    Esperar-Saudavel 'boletim'
    Nota 'O pedido antigo chega agora: o boletim, que nao sabe do prazo, anonimiza'
    Nota 'e confirma. O core recebe a confirmacao de um processo ja revertido.'
    Esperar 'o core reenviou o estado da pessoa, e a replica foi refeita' 60 {
        (Get-Banco boletim "SELECT id FROM aluno_replica WHERE id = $id2") -ne ''
    } | Out-Null
    Nota "replica: $(Get-Banco boletim "SELECT nome FROM aluno_replica WHERE id = $id2")"
    Nota 'Ignorar a confirmacao tardia deixaria o boletim sem a pessoa para sempre.'
    Nota 'Responder a ela com o estado atual e a compensacao da compensacao - e e'
    Nota 'o que torna seguro ter prazo.'
}

# ── 10 · Um nó do broker cai ────────────────────────────────────────────────

function Get-FilaQuorum($fila) {
    Invoke-RestMethod -Uri "$Painel/api/queues/%2F/$fila" -Headers $PainelAuth
}

function Cenario10 {
    Titulo '10 - Um no do broker cai'
    Nota 'O cenario 3 derrubou o broker inteiro, e a caixa de saida segurou os'
    Nota 'eventos. Aqui cai um no so - e nao e preciso segurar nada: as filas'
    Nota 'quorum estao replicadas nos tres, e dois de tres sao maioria.'

    $sk = Get-Token 'atendimento'
    $q = Get-FilaQuorum 'boletim.usuarios'
    $lider = "$($q.leader)".Replace('rabbit@', '')

    Passo 'Antes'
    Nota "boletim.usuarios: replicas no ar $(@($q.online).Count) de $(@($q.members).Count), lider em $lider"
    Nota 'Conexoes dos servicos por no:'
    Invoke-RestMethod -Uri "$Painel/api/connections" -Headers $PainelAuth |
        Group-Object { "$($_.node)".Replace('rabbit@', '') } | Sort-Object Name | ForEach-Object {
            Nota "  $($_.Name)  $($_.Count) conexao(oes), todas TLS"
        }

    Passo "Derrubando $lider - justamente o lider da fila do boletim"
    Invoke-Docker -Silencioso compose stop $lider
    Ok "$lider fora do ar"

    Esperar 'a fila elegeu outro lider entre os dois que sobraram' 60 {
        $atual = "$((Get-FilaQuorum 'boletim.usuarios').leader)"
        $atual -ne '' -and $atual -ne "rabbit@$lider"
    } | Out-Null
    $q = Get-FilaQuorum 'boletim.usuarios'
    Nota "boletim.usuarios: replicas no ar $(@($q.online).Count) de $(@($q.members).Count), lider agora em $("$($q.leader)".Replace('rabbit@', ''))"

    Passo 'Com um no a menos, o sistema segue'
    $id = Novo-Aluno 'Cadastro Com Um No A Menos' $sk
    Ok "aluno $id criado"
    Esperar 'o evento chegou a replica do boletim, por dois nos' 60 {
        (Get-Banco boletim "SELECT id FROM aluno_replica WHERE id = $id") -ne ''
    } | Out-Null
    Nota 'Quem estava conectado ao no que caiu reconectou sozinho em outro - a'
    Nota 'lista dos tres enderecos esta na configuracao de cada servico.'

    Passo "Subindo $lider de volta"
    Invoke-Docker -Silencioso compose start $lider
    Esperar-Saudavel $lider 120
    Esperar 'o no voltou a ser replica, com as mensagens que perdeu' 60 {
        @((Get-FilaQuorum 'boletim.usuarios').online).Count -eq 3
    } | Out-Null
    Nota 'Ele nao perdeu nada: ao voltar, recebe do lider o que foi gravado'
    Nota 'enquanto estava fora. Acompanhe no Grafana: http://localhost:21300'
}

# ── Despacho ─────────────────────────────────────────────────────────────────

function Listar {
    @"
Cenarios da demonstracao - TP4

  1  O consumidor pode estar fora do ar   fila acumula e e aplicada depois
  2  O boletim funciona sem o core        o acoplamento do TP3, desfeito
  3  O broker pode estar fora do ar       caixa de saida transacional
  4  A mensagem que nao da para processar recusa e fila de mensagens mortas
  5  A mesma mensagem duas vezes          consumidor idempotente
  6  Tres instancias dividindo trabalho   competing consumers e fanout
  7  Pedido para agora e para depois      comando direct e atraso por TTL+DLX
  8  A pergunta que espera resposta       request/reply, a excecao sincrona
  9  Remover uma pessoa de tres bancos    saga coreografada, compensacao e prazo
 10  Um no do broker cai                  cluster de tres nos, filas quorum

Uso:  .\demo\cenarios.ps1 3
      .\demo\cenarios.ps1 todos

Painel do RabbitMQ: $Painel  (infnethub / infnethub)
Painel de operacao: http://localhost:21300  (Grafana)
"@
}

try {
    # `readiness`, e nao a saude agregada: os cenarios 3 e 8 derrubam o broker e o
    # boletim de proposito, e a agregada responde DOWN nesses momentos.
    Invoke-RestMethod -Uri "$Gateway/actuator/health/readiness" -TimeoutSec 5 | Out-Null
} catch {
    Erro 'O ambiente nao esta no ar. Suba com: docker compose up -d'
    exit 1
}

switch -Regex ($Cenario) {
    '^$|^ajuda$' { Listar }
    '^todos$'    { 1..10 | ForEach-Object { & "Cenario$_" }; Titulo 'Fim' }
    '^([1-9]|10)$' { & "Cenario$Cenario" }
    default      { Erro "cenario desconhecido: $Cenario"; Listar; exit 1 }
}
