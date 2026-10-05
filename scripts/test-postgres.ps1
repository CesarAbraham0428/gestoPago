param(
    [string]$GradlePath,
    [switch]$EsquemaManual
)
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $workspacePath
$dbSettings = @{}
Get-Content -LiteralPath '.env' | ForEach-Object {
    if ($_ -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)\s*=\s*(.*)$') {
        $dbSettings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
if ($dbSettings['DB_URL'] -notmatch '^jdbc:postgresql://([^/:]+)(?::(\d+))?/') {
    throw 'DB_URL debe ser una URL JDBC PostgreSQL local válida.'
}
$dbHostName = $matches[1]
$dbPortNumber = if ($matches[2]) { $matches[2] } else { '5432' }
$testDatabaseName = 'gestopago_backend_test_' + [guid]::NewGuid().ToString('N')
if ($testDatabaseName -notmatch '^gestopago_backend_test_[a-f0-9]{32}$') { throw 'Nombre de BD de pruebas inválido.' }
$oldVariables = @{}
foreach ($key in @('PGPASSWORD','TEST_DB_URL','TEST_DB_USERNAME','TEST_DB_PASSWORD')) {
    $oldVariables[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
$created = $false
try {
    $env:PGPASSWORD = $dbSettings['DB_PASSWORD']
    & psql -h $dbHostName -p $dbPortNumber -U $dbSettings['DB_USERNAME'] -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $testDatabaseName"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la BD aislada de pruebas.' }
    $created = $true
    if ($EsquemaManual) {
        & psql -h $dbHostName -p $dbPortNumber -U $dbSettings['DB_USERNAME'] -d $testDatabaseName -v ON_ERROR_STOP=1 -q -f docs/esquema-clientes.sql
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo cargar el esquema manual.' }
    }
    $env:TEST_DB_URL = "jdbc:postgresql://${dbHostName}:${dbPortNumber}/${testDatabaseName}"
    $env:TEST_DB_USERNAME = $dbSettings['DB_USERNAME']
    $env:TEST_DB_PASSWORD = $dbSettings['DB_PASSWORD']
    if (-not $GradlePath) { $GradlePath = Join-Path $workspacePath 'gradlew.bat' }
    & $GradlePath test --no-daemon --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas del backend.' }
} finally {
    if ($created) {
        & psql -h $dbHostName -p $dbPortNumber -U $dbSettings['DB_USERNAME'] -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE $testDatabaseName WITH (FORCE)"
    }
    foreach ($key in $oldVariables.Keys) {
        [Environment]::SetEnvironmentVariable($key, $oldVariables[$key], 'Process')
    }
}
