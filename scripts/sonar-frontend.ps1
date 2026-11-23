<#
  Analiza el frontend Flutter con SonarQube (plugin sonar-flutter + hallazgos de dart analyze + cobertura).
  Uso:  powershell -File scripts/sonar-frontend.ps1 [-Puerto 9001]
  Reporte: http://localhost:9001/dashboard?id=gestopago-frontend
#>
param([int]$Puerto = 9001)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$base = "http://localhost:$Puerto"
$adminPass = if ($env:SONAR_ADMIN_PASSWORD) { $env:SONAR_ADMIN_PASSWORD } else { 'Gestopago#Sonar2026' }
$h = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$adminPass")) }

# 1) SonarQube arriba (con el plugin montado desde compose.yaml)
Set-Location $raiz
$env:SONAR_PORT = $Puerto
docker compose up -d sonarqube
if ($LASTEXITCODE) { throw 'No se pudo levantar SonarQube' }
for ($i = 0; $i -lt 90; $i++) {
  try { if ((Invoke-RestMethod "$base/api/system/status").status -eq 'UP') { break } } catch {}
  Start-Sleep 5
}

# 2) Pruebas con cobertura + analisis estatico de Dart
Set-Location (Join-Path $raiz 'frontend')
New-Item -ItemType Directory -Force reports | Out-Null
Set-Content reports/analyzer-vacio.txt '' -Encoding utf8
flutter pub get
flutter test --coverage
$lineas = dart analyze --format=machine 2>&1 | Where-Object { $_ -match '^(ERROR|WARNING|INFO)\|' }

# 3) dart analyze -> formato generico de issues externos de Sonar
$sev = @{ ERROR = 'HIGH'; WARNING = 'MEDIUM'; INFO = 'LOW' }
$reglas = @{}; $issues = @()
foreach ($l in $lineas) {
  $p = $l -split '\|', 8
  $id = $p[2].ToLower()
  $ruta = $p[3].Replace('\', '/').Replace('//', '/')
  $raizFront = $PWD.Path.Replace('\', '/').TrimEnd('/') + '/'
  if ($ruta.StartsWith($raizFront, [StringComparison]::OrdinalIgnoreCase)) { $ruta = $ruta.Substring($raizFront.Length) }  # relativa al proyecto
  if (-not $reglas.ContainsKey($id)) {
    $reglas[$id] = @{ id = $id; name = $id; description = "dart analyze: $id"; engineId = 'dartanalyzer'
      cleanCodeAttribute = 'CONVENTIONAL'; impacts = @(@{ softwareQuality = 'MAINTAINABILITY'; severity = $sev[$p[0]] }) }
  }
  $issues += @{ ruleId = $id; engineId = 'dartanalyzer'
    primaryLocation = @{ message = $p[7]; filePath = $ruta
      textRange = @{ startLine = [int]$p[4]; endLine = [int]$p[4] } } }
}
@{ rules = @($reglas.Values); issues = $issues } | ConvertTo-Json -Depth 8 | Set-Content reports/dart-analyze-sonar.json -Encoding utf8

# 4) Scanner
$token = (Invoke-RestMethod "$base/api/user_tokens/generate" -Method Post -Headers $h -Body @{ name = "flutter-$(Get-Random)" }).token
$scanner = Get-ChildItem (Join-Path $raiz 'tools') -Directory -Filter 'sonar-scanner-*' | Select-Object -First 1
& (Join-Path $scanner.FullName 'bin/sonar-scanner.bat') "-Dsonar.token=$token" "-Dsonar.host.url=$base"
if ($LASTEXITCODE) { throw 'Fallo el analisis' }
Write-Host "Listo: $base/dashboard?id=gestopago-frontend"
