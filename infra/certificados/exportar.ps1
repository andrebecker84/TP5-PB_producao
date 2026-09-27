<#
    Copia a autoridade de desenvolvimento para fora do Docker, para rodar um
    serviço pela IDE contra a infraestrutura do Compose. Equivalente PowerShell
    do exportar.sh — ver as instruções lá.

    Uso:  .\infra\certificados\exportar.ps1
#>
$ErrorActionPreference = 'Stop'
$raiz = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$destino = Join-Path $raiz 'target\certificados'
New-Item -ItemType Directory -Force -Path $destino | Out-Null
Push-Location $raiz
try {
    foreach ($arquivo in 'ca.crt', 'confianca.p12') {
        # Bytes, e não texto: o .p12 é binário, e o redirecionamento do
        # PowerShell 5.1 regravaria o conteúdo em UTF-16.
        $processo = Start-Process docker -ArgumentList @('compose', 'run', '--rm', '--no-deps', '-T',
            '--entrypoint', 'cat', 'certificados', "/certs/$arquivo") `
            -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $destino $arquivo)
        if ($processo.ExitCode -ne 0) { throw "falha ao copiar $arquivo" }
    }
} finally { Pop-Location }
Write-Host 'autoridade de desenvolvimento copiada para target\certificados\'
