param(
    [string]$EnvFile = ".env.remote",
    [switch]$DisableOpenEoSync
)

$ErrorActionPreference = "Stop"

function Test-CommandExists {
    param([string]$CommandName)
    return [bool](Get-Command $CommandName -ErrorAction SilentlyContinue)
}

function Set-EnvFromFile {
    param([string]$FilePath)

    if (-not (Test-Path $FilePath)) {
        return $false
    }

    Get-Content $FilePath | ForEach-Object {
        $line = $_.Trim()

        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            return
        }

        $idx = $line.IndexOf("=")
        if ($idx -lt 1) {
            return
        }

        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }

    return $true
}

function Test-TcpPortOpen {
    param(
        [string]$TargetHost = "127.0.0.1",
        [int]$Port
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($TargetHost, $Port, $null, $null)
        $connected = $iar.AsyncWaitHandle.WaitOne(400)
        if (-not $connected) {
            return $false
        }
        $client.EndConnect($iar)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot

if (-not (Set-EnvFromFile $EnvFile)) {
    Write-Host "No se encontro $EnvFile. Cargando variables desde .env.example" -ForegroundColor Yellow
    $loadedFromExample = Set-EnvFromFile ".env.example"
    if (-not $loadedFromExample) {
        throw "No existe .env ni .env.example en la raiz del proyecto."
    }
}

if ($DisableOpenEoSync) {
    [Environment]::SetEnvironmentVariable("OPENEO_SYNC_ENABLED", "false", "Process")
}

if (-not (Test-CommandExists "java")) {
    throw "No se encontro Java en PATH. Instala Java 17+ y vuelve a intentar."
}

if (-not (Test-CommandExists "mvn")) {
    throw "No se encontro Maven en PATH. Instala Maven y vuelve a intentar."
}

$serverPort = 8082
if ($env:SERVER_PORT -and $env:SERVER_PORT.Trim() -ne "") {
    [void][int]::TryParse($env:SERVER_PORT, [ref]$serverPort)
}

if (Test-TcpPortOpen -Port $serverPort) {
    Write-Host "Advertencia: el puerto $serverPort parece estar en uso." -ForegroundColor Yellow
    Write-Host "Tip: define SERVER_PORT=8081 (u otro) en tu .env para evitar conflicto." -ForegroundColor Yellow
}

$mongoUri = $env:MONGODB_URI
if (-not $mongoUri) {
    $mongoUri = "mongodb://localhost:27017/simfat"
}

if ($mongoUri.StartsWith("mongodb://localhost") -or $mongoUri.StartsWith("mongodb://127.0.0.1")) {
    if (-not (Test-TcpPortOpen -Port 27017)) {
        Write-Host "No detecto MongoDB local en 27017. Si usas servicio local, inicia MongoDB antes de correr la app." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Levantando SIMFAT Backend..." -ForegroundColor Cyan
Write-Host "SERVER_PORT=$serverPort"
Write-Host "MONGODB_URI=$mongoUri"
Write-Host "OPENEO_SYNC_ENABLED=$($env:OPENEO_SYNC_ENABLED)"
Write-Host ""

mvn spring-boot:run
