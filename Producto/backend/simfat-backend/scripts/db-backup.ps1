param(
    [string]$MongoUri = "mongodb://localhost:27017",
    [string]$Database = "simfat",
    [string]$BackupRoot = ".\backups\mongo"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command mongodump -ErrorAction SilentlyContinue)) {
    throw "No se encontro 'mongodump' en PATH. Instala MongoDB Database Tools para continuar."
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = Join-Path $BackupRoot "$Database-$timestamp"
New-Item -ItemType Directory -Path $backupPath -Force | Out-Null

Write-Host "Creando backup en: $backupPath"
& mongodump --uri="$MongoUri" --db="$Database" --out="$backupPath"

if ($LASTEXITCODE -ne 0) {
    throw "mongodump fallo con codigo $LASTEXITCODE"
}

$dbDumpPath = Join-Path $backupPath $Database
Write-Host "Backup OK"
Write-Host "Ruta para rollback: $dbDumpPath"
