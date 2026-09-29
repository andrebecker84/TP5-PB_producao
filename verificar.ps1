<#
    Verificação completa: testes automatizados + coleção HTTP contra o ambiente
    em execução. Equivalente PowerShell do verificar.sh.

    São duas naturezas de teste, e é de propósito que estejam no mesmo comando.
    Os testes de unidade e de fatia provam as regras e as consultas com o
    sistema desmontado em pedaços; a coleção HTTP prova o sistema inteiro
    montado — Keycloak emitindo token, gateway roteando, Eureka resolvendo, e
    o que um serviço publica no RabbitMQ chegando ao banco de outro. Um
    conjunto passa sem o outro, e nenhum dos dois sozinho diz que a entrega
    funciona.

    A coleção roda pelo HTTP Client da JetBrains em contêiner, o mesmo motor que
    a IDE usa: as asserções dos blocos `> {% … %}` nos arquivos .http são
    executadas sem alteração, e não há um segundo conjunto de verificações para
    manter.

    Uso:  .\verificar.ps1              (ambiente gateway, o padrão)
          .\verificar.ps1 local        serviços rodando pela IDE, sem gateway
#>
param(
    [string]$Ambiente = "gateway"
)

$ErrorActionPreference = "Continue"

# O Windows PowerShell 5.1 escreve no console usando a página de código ANSI, e
# os acentos das mensagens saem trocados. Duas providências são necessárias e
# nenhuma substitui a outra: esta linha corrige a SAÍDA, e o BOM UTF-8 no início
# deste arquivo corrige a LEITURA — sem o BOM, o interpretador lê o texto como
# ANSI, e um acento dentro de uma string chega a quebrar a análise do script.
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$Raiz        = $PSScriptRoot
$Imagem      = "jetbrains/intellij-http-client"
$Relatorios  = Join-Path $Raiz "target\http-reports"

# Ordem importa: a coleção encadeia identificadores entre requisições, e os
# arquivos são numerados na ordem em que devem rodar.
$Colecao = @(
    # O 00 vem primeiro por necessidade, não por ordem estética: é ele que
    # obtém os tokens no Keycloak e os guarda em variáveis globais. Sem ele,
    # todas as demais requisições respondem 401.
    "00-autenticacao.http",
    "01-usuarios.http", "02-posts.http", "03-comentarios.http", "04-vagas.http",
    "05-historico.http", "06-erros.http", "07-boletim.http", "08-notificacoes.http",
    "09-avisos.http", "10-consulta-boletim.http", "11-expurgo.http"
)

# `readiness`, e não a saúde agregada: esta é a verificação de "o ambiente está
# no ar para atender requisições". A agregada inclui o estado do RabbitMQ e
# responde DOWN quando ele está desligado — condição em que a aplicação segue
# funcionando, porque a caixa de saída guarda os eventos.
$Saude = if ($Ambiente -eq "local") {
    "http://localhost:21081/actuator/health/readiness"
} else {
    "http://localhost:21080/actuator/health"
}

Write-Host "== 1/2 · Testes automatizados =========================================="
& (Join-Path $Raiz "mvnw.cmd") -q test
if ($LASTEXITCODE -ne 0) {
    Write-Host "Testes automatizados falharam. A coleção HTTP não foi executada."
    exit $LASTEXITCODE
}
Write-Host "Testes automatizados: OK"
Write-Host ""

Write-Host "== 2/2 · Coleção HTTP (ambiente: $Ambiente) ============================"
try {
    Invoke-WebRequest -Uri $Saude -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
    Write-Host "O ambiente não está no ar ($Saude não respondeu)."
    Write-Host "Suba com:  docker compose up -d"
    exit 1
}

New-Item -ItemType Directory -Force -Path $Relatorios | Out-Null

# -D faz o cliente tratar "localhost" nas requisições como host.docker.internal:
# sem isso, rodando dentro de um contêiner, localhost seria o próprio contêiner
# do cliente. É o que permite manter os arquivos .http idênticos aos que rodam
# na IDE, sem um ambiente separado só para a linha de comando.
function Invoke-Colecao([string[]]$Arquivos) {
    docker run --rm `
        -v "${Raiz}\http:/http" `
        -v "${Relatorios}:/reports" `
        -w /http `
        $Imagem `
            -D `
            -e $Ambiente `
            -v http-client.env.json `
            --no-progress `
            -r /reports `
            @Arquivos | Out-Host
    # Sem o Out-Host, a saída do contêiner viraria parte do valor de retorno da
    # função — em PowerShell tudo o que não é consumido é devolvido — e o código
    # de saída chegaria misturado com centenas de linhas de texto.
    return $LASTEXITCODE
}

$http = Invoke-Colecao $Colecao

# O desfecho da saga de expurgo, numa segunda chamada e depois de uma pausa.
#
# A saga leva perto de um segundo para dar a volta completa — core → broker →
# boletim → broker → core, com dois relays de 500 ms pelo caminho. O cliente
# HTTP não sabe esperar, e encher o arquivo de requisições até dar tempo produz
# um teste que passa quase sempre: foi tentado, e falhou duas vezes em três
# execuções.
#
# A espera pertence a quem executa a coleção, e é aqui que ela fica — explícita,
# com o motivo ao lado. O 00 é repetido porque cada chamada do cliente começa
# sem as variáveis da anterior, tokens inclusive.
#
# Os avisos (13) seguem a mesma regra, e esta chamada só lê: pode ser repetida.
# Num ambiente recém-criado, a primeira volta às vezes demora mais que a pausa;
# são até seis tentativas, e o que não chegar em 30 s é falha de verdade.
if ($http -eq 0) {
    Write-Host ""
    Write-Host "Aguardando os desfechos assíncronos (saga de expurgo e avisos)…"
    foreach ($tentativa in 1..6) {
        Start-Sleep -Seconds 5
        $http = Invoke-Colecao @("00-autenticacao.http", "12-expurgo-desfecho.http", "13-avisos-desfecho.http")
        if ($http -eq 0) { break }
        Write-Host "Tentativa ${tentativa}: ainda não chegou tudo."
    }
}

Write-Host ""
if ($http -eq 0) {
    Write-Host "Coleção HTTP: OK — relatório JUnit em target\http-reports"
} else {
    Write-Host "Coleção HTTP: FALHOU — detalhes em target\http-reports"
}
exit $http
