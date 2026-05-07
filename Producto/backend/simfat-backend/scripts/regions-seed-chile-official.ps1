param(
    [string]$BackendBaseUrl = "http://localhost:8081",
    [string]$MappingFile = ".\\scripts\\chile-regions-aoi-official.json",
    [switch]$PruneLegacyRegions
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $MappingFile)) {
    throw "No existe archivo de mapeo oficial: $MappingFile"
}

$mappingRoot = Get-Content -Path $MappingFile -Raw | ConvertFrom-Json
if (-not $mappingRoot.regions -or $mappingRoot.regions.Count -eq 0) {
    throw "El archivo de mapeo no contiene regiones."
}

$regionsMap = @{}
foreach ($item in $mappingRoot.regions) {
    $code = [string]$item.codigo
    if (-not $code) {
        continue
    }
    $regionsMap[$code.ToUpperInvariant()] = $item
}

$currentResponse = Invoke-RestMethod -Method Get -Uri "$BackendBaseUrl/api/regions"
$currentRegions = @($currentResponse.data)
$currentByCode = @{}
foreach ($region in $currentRegions) {
    if ($region.codigo) {
        $currentByCode[$region.codigo.ToUpperInvariant()] = $region
    }
}

$created = 0
$updated = 0

foreach ($pair in $regionsMap.GetEnumerator()) {
    $code = $pair.Key
    $target = $pair.Value

    $existing = $null
    if ($currentByCode.ContainsKey($code)) {
        $existing = $currentByCode[$code]
    }

    $hectareas = 100000
    if ($existing -and $existing.hectareasBosqueReferencia -gt 0) {
        $hectareas = [double]$existing.hectareasBosqueReferencia
    }

    $payload = @{
        nombre = [string]$target.nombre
        codigo = [string]$target.codigo
        zona = [string]$target.zona
        hectareasBosqueReferencia = [double]$hectareas
        aoiBbox = @(
            [double]$target.aoiBbox[0],
            [double]$target.aoiBbox[1],
            [double]$target.aoiBbox[2],
            [double]$target.aoiBbox[3]
        )
    } | ConvertTo-Json -Depth 10

    if ($existing) {
        Invoke-RestMethod -Method Put -Uri "$BackendBaseUrl/api/regions/$($existing.id)" -ContentType "application/json" -Body $payload | Out-Null
        Write-Host "UPDATE codigo=$code id=$($existing.id)"
        $updated++
    } else {
        $createdResponse = Invoke-RestMethod -Method Post -Uri "$BackendBaseUrl/api/regions" -ContentType "application/json" -Body $payload
        Write-Host "CREATE codigo=$code id=$($createdResponse.data.id)"
        $created++
    }
}

$deleted = 0
if ($PruneLegacyRegions) {
    foreach ($region in $currentRegions) {
        $code = [string]$region.codigo
        if (-not $code) {
            continue
        }

        if (-not $regionsMap.ContainsKey($code.ToUpperInvariant())) {
            Invoke-RestMethod -Method Delete -Uri "$BackendBaseUrl/api/regions/$($region.id)" | Out-Null
            Write-Host "DELETE legacy codigo=$code id=$($region.id)"
            $deleted++
        }
    }
}

Write-Host ""
Write-Host "Resumen seed oficial regiones Chile:"
Write-Host "  creadas=$created"
Write-Host "  actualizadas=$updated"
Write-Host "  eliminadas_legacy=$deleted"
Write-Host ""
Write-Host "Fuente: $($mappingRoot.source.name)"
Write-Host "Descarga: $($mappingRoot.source.download_url)"
Write-Host "Metodo: $($mappingRoot.source.method)"
Write-Host ""
Write-Host "Verifica cobertura AOI:"
Write-Host "  curl.exe $BackendBaseUrl/api/regions/aoi/coverage"
