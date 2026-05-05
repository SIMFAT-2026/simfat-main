param(
    [string]$BackendBaseUrl = "http://localhost:8081",
    [string]$MappingFile = ".\scripts\aoi-bbox.sample.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $MappingFile)) {
    throw "No existe el archivo de mapeo: $MappingFile"
}

$mapping = Get-Content -Path $MappingFile -Raw | ConvertFrom-Json
if (-not $mapping) {
    throw "El archivo de mapeo esta vacio o no es JSON valido."
}

$regionsResponse = Invoke-RestMethod -Method Get -Uri "$BackendBaseUrl/api/regions"
$regions = $regionsResponse.data
if (-not $regions) {
    throw "No se encontraron regiones en backend para actualizar AOI."
}

$regionByCode = @{}
foreach ($region in $regions) {
    if ($region.codigo) {
        $regionByCode[$region.codigo.ToUpperInvariant()] = $region
    }
}

$updated = 0
$missing = 0

foreach ($entry in $mapping) {
    $code = [string]$entry.codigo
    $bbox = $entry.aoiBbox
    if (-not $code -or -not $bbox) {
        Write-Warning "Entrada invalida en mapping, se omite."
        continue
    }

    $lookup = $code.ToUpperInvariant()
    if (-not $regionByCode.ContainsKey($lookup)) {
        Write-Warning "No existe region con codigo $code en backend. Se omite."
        $missing++
        continue
    }

    $regionId = $regionByCode[$lookup].id
    $payload = @{ aoiBbox = $bbox } | ConvertTo-Json -Depth 5

    Invoke-RestMethod `
        -Method Patch `
        -Uri "$BackendBaseUrl/api/regions/$regionId/aoi" `
        -ContentType "application/json" `
        -Body $payload | Out-Null

    Write-Host "AOI actualizado para codigo=$code regionId=$regionId"
    $updated++
}

Write-Host ""
Write-Host "Resumen:"
Write-Host "  actualizados=$updated"
Write-Host "  codigos_sin_match=$missing"
Write-Host ""
Write-Host "Puedes validar cobertura con:"
Write-Host "  curl.exe $BackendBaseUrl/api/regions/aoi/coverage"
