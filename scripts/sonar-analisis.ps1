<#
  Levanta SonarQube (compose.yaml), ejecuta pruebas + JaCoCo y envia el analisis.
  Uso:  powershell -File scripts/sonar-analisis.ps1 [-Puerto 9001] [-Detener]
  Reportes: http://localhost:9001  (admin / SONAR_ADMIN_PASSWORD)
#>
param(
  [int]$Puerto = 9001,
  [switch]$Detener
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:SONAR_PORT = $Puerto
$base = "http://localhost:$Puerto"
$adminPass = if ($env:SONAR_ADMIN_PASSWORD) { $env:SONAR_ADMIN_PASSWORD } else { 'Gestopago#Sonar2026' }
$cred = "admin:$adminPass"

docker compose up -d sonarqube
if ($LASTEXITCODE) { throw 'No se pudo levantar SonarQube' }

Write-Host 'Esperando a SonarQube...'
for ($i = 0; $i -lt 90; $i++) {
  try { if ((Invoke-RestMethod "$base/api/system/status").status -eq 'UP') { break } } catch {}
  Start-Sleep 5
}
if ((Invoke-RestMethod "$base/api/system/status").status -ne 'UP') { throw 'SonarQube no arranco' }

# Primer arranque: cambia la contrasena por defecto (admin/admin)
$h = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($cred)) }
try { $ok = (Invoke-RestMethod "$base/api/authentication/validate" -Headers $h).valid } catch { $ok = $false }
if (-not $ok) {
  $d = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin')) }
  Invoke-RestMethod "$base/api/users/change_password" -Method Post -Headers $d `
    -Body @{ login = 'admin'; previousPassword = 'admin'; password = $adminPass } | Out-Null
}

# Token de analisis (se regenera en cada corrida)
try { Invoke-RestMethod "$base/api/user_tokens/revoke" -Method Post -Headers $h -Body @{ name = 'gradle' } | Out-Null } catch {}
$token = (Invoke-RestMethod "$base/api/user_tokens/generate" -Method Post -Headers $h -Body @{ name = 'gradle' }).token

Push-Location backend
try {
  ./gradlew.bat clean test jacocoTestReport sonar "-Dsonar.host.url=$base" "-Dsonar.token=$token"
  if ($LASTEXITCODE) { throw 'Fallo el analisis' }
} finally { Pop-Location }
Write-Host "Listo: $base/dashboard?id=gestopago-api"
if ($Detener) { docker compose stop sonarqube }
