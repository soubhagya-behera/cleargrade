$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'JDK 21 is required for the backend build. Install JDK 21 and set JAVA_HOME.'
}
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = ((& java -version 2>&1 | Out-String) -split "`n")[0]
$ErrorActionPreference = $previousPreference
if ($javaVersion -notmatch 'version\s+"21\.') {
    throw "JDK 21 is required; found: $javaVersion. Set JAVA_HOME to a JDK 21 installation."
}

Push-Location $root
try {
    Write-Host '== Backend: Maven verify =='
    Push-Location (Join-Path $root 'backend')
    try { & .\mvnw.cmd -B verify; if ($LASTEXITCODE -ne 0) { throw "Backend verify failed ($LASTEXITCODE)" } }
    finally { Pop-Location }

    Write-Host '== Frontend: tests =='
    Push-Location (Join-Path $root 'frontend')
    try {
        & npm test; if ($LASTEXITCODE -ne 0) { throw "Frontend tests failed ($LASTEXITCODE)" }
        & npm run typecheck; if ($LASTEXITCODE -ne 0) { throw "Typecheck failed ($LASTEXITCODE)" }
        & npm run build; if ($LASTEXITCODE -ne 0) { throw "Production build failed ($LASTEXITCODE)" }
    }
    finally { Pop-Location }

    Write-Host '== Docker Compose configuration =='
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw "docker compose config failed ($LASTEXITCODE)" }

    Write-Host '== Whitespace check =='
    & git diff --check
    if ($LASTEXITCODE -ne 0) { throw "git diff --check failed ($LASTEXITCODE)" }

    Write-Host 'All release checks passed.'
}
finally { Pop-Location }
