param(
    [int]$Port = 8001
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$payloadPath = Join-Path $root "tmp_payload_e2e.json"
$logPath = Join-Path $root "e2e-local.log"
$errPath = Join-Path $root "e2e-local.err.log"

@'
{
  "regionId": "region-001",
  "aoi": {
    "type": "bbox",
    "coordinates": [-72.6, -38.8, -72.3, -38.5]
  },
  "periodStart": "2026-04-01",
  "periodEnd": "2026-04-03"
}
'@ | Set-Content -Path $payloadPath -Encoding utf8

if (Test-Path $logPath) { Remove-Item $logPath -Force }
if (Test-Path $errPath) { Remove-Item $errPath -Force }

$proc = Start-Process `
    -FilePath python `
    -ArgumentList @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "$Port") `
    -WorkingDirectory $root `
    -RedirectStandardOutput $logPath `
    -RedirectStandardError $errPath `
    -PassThru

try {
    Start-Sleep -Seconds 4

    Write-Host "=== GET /health ==="
    curl.exe -s -i "http://127.0.0.1:$Port/health"

    Write-Host "`n=== GET /openeo/capabilities ==="
    curl.exe -s -i "http://127.0.0.1:$Port/openeo/capabilities"

    Write-Host "`n=== GET /openeo/collections?limit=5 ==="
    curl.exe -s -i "http://127.0.0.1:$Port/openeo/collections?limit=5"

    Write-Host "`n=== POST /openeo/indicators/latest/NDVI ==="
    curl.exe -s -i -X POST "http://127.0.0.1:$Port/openeo/indicators/latest/NDVI" `
      -H "Content-Type: application/json" `
      --data-binary "@$payloadPath"

    Write-Host "`n=== POST /openeo/indicators/latest/NDMI ==="
    curl.exe -s -i -X POST "http://127.0.0.1:$Port/openeo/indicators/latest/NDMI" `
      -H "Content-Type: application/json" `
      --data-binary "@$payloadPath"
}
finally {
    if (Get-Process -Id $proc.Id -ErrorAction SilentlyContinue) {
        Stop-Process -Id $proc.Id -Force
    }
    if (Test-Path $payloadPath) {
        Remove-Item $payloadPath -Force
    }
}
