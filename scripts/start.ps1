param([switch]$NoBuild)

$ErrorActionPreference = 'Stop'

function Read-EnvValue {
    param([string]$Name, [string]$Default)
    $path = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'
    if (Test-Path -LiteralPath $path) {
        $match = Select-String -LiteralPath $path -Pattern "^\s*$Name=(.*)$" | Select-Object -First 1
        if ($match) { return $match.Matches[0].Groups[1].Value.Trim() }
    }
    return $Default
}

$root = Split-Path -Parent $PSScriptRoot
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required but was not found on PATH.' }
docker compose version 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is required (docker compose version failed).' }

$envPath = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    $example = Join-Path $root '.env.example'
    if (-not (Test-Path -LiteralPath $example)) { throw 'Missing .env.example; cannot initialise .env.' }
    Copy-Item -LiteralPath $example -Destination $envPath
    $password = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $content = Get-Content -LiteralPath $envPath
    $content = $content | ForEach-Object {
        if ($_ -match '^POSTGRES_PASSWORD=') { "POSTGRES_PASSWORD=$password" } else { $_ }
    }
    Set-Content -LiteralPath $envPath -Value $content
    Write-Host "Created git-ignored .env with a generated local PostgreSQL password."
}

Push-Location $root
try {
    $composeArgs = @('compose', 'up', '-d')
    if (-not $NoBuild) { $composeArgs += '--build' }
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed with exit code $LASTEXITCODE" }

    $appPort = Read-EnvValue -Name 'APP_PORT' -Default '8080'
    $frontPort = Read-EnvValue -Name 'FRONTEND_PORT' -Default '3000'
    $healthUrl = "http://localhost:$appPort/actuator/health"
    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $status = & curl.exe --silent --output NUL --write-out '%{http_code}' $healthUrl
        if ($status -eq '200') { $ready = $true; break }
        Start-Sleep -Seconds 3
    }
    if (-not $ready) {
        & docker compose logs --tail=80
        throw "Backend did not become healthy at $healthUrl within 180 seconds."
    }

    Write-Host ''
    Write-Host 'ClearGrade is ready:'
    Write-Host "  Frontend: http://localhost:$frontPort"
    Write-Host "  Backend health: $healthUrl"
    Write-Host "  API: http://localhost:$appPort/api"
    Write-Host 'Seed sample data (real HTTP API): powershell -File scripts/seed.ps1'
    Write-Host 'Stop: docker compose down   |   Reset data: docker compose down -v'
}
finally {
    Pop-Location
}
