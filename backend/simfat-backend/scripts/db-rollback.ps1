param(
    [string]$MongoUri = "mongodb://localhost:27017",
    [string]$Database = "simfat",
    [Parameter(Mandatory = $true)]
    [string]$BackupPath
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command mongorestore -ErrorAction SilentlyContinue)) {
    throw "No se encontro 'mongorestore' en PATH. Instala MongoDB Database Tools para continuar."
}

if (-not (Test-Path $BackupPath)) {
    throw "BackupPath no existe: $BackupPath"
}

Write-Host "Restaurando base '$Database' desde: $BackupPath"
& mongorestore --uri="$MongoUri" --drop --db="$Database" "$BackupPath"

if ($LASTEXITCODE -ne 0) {
    throw "mongorestore fallo con codigo $LASTEXITCODE"
}

Write-Host "Rollback OK"
