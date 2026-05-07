param(
    [string]$MongoUri = "mongodb://localhost:27017",
    [string]$Database = "simfat",
    [switch]$SkipBackup
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command mongosh -ErrorAction SilentlyContinue)) {
    throw "No se encontro 'mongosh' en PATH. Instala MongoDB Shell para continuar."
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

if (-not $SkipBackup) {
    & (Join-Path $scriptDir "db-backup.ps1") -MongoUri $MongoUri -Database $Database
    if ($LASTEXITCODE -ne 0) {
        throw "El backup previo fallo. Abortando reseed."
    }
}

$mongoJs = @"
const dbName = "$Database";
const target = db.getSiblingDB(dbName);
target.getCollection("forest_loss_records").deleteMany({});
target.getCollection("heat_alert_events").deleteMany({});
target.getCollection("alert_rules").deleteMany({});
target.getCollection("regions").deleteMany({});
print("Colecciones limpiadas en " + dbName);
"@

Write-Host "Limpiando colecciones objetivo..."
& mongosh "$MongoUri/$Database" --quiet --eval $mongoJs

if ($LASTEXITCODE -ne 0) {
    throw "La limpieza de colecciones fallo."
}

Write-Host ""
Write-Host "Base lista para seed."
Write-Host "Siguiente paso: inicia el backend para que DataSeederConfig recargue los datos mapeados."
Write-Host "Comando:"
Write-Host "  mvn spring-boot:run"
Write-Host ""
Write-Host "Tip: si necesitas rollback, usa scripts/db-rollback.ps1 con la ruta del backup generado."
