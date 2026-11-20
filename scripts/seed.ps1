param(
    [string]$ApiBaseUrl = 'http://localhost:8080/api',
    [switch]$SkipUpdate
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
$fixtureDir = Join-Path $rootDir 'scripts/seed'

function Invoke-SeedUpload {
    param([string]$FileName)

    $fixture = Join-Path $fixtureDir $FileName
    if (-not (Test-Path -LiteralPath $fixture)) {
        throw "Seed fixture not found: $fixture"
    }

    $bodyFile = [System.IO.Path]::GetTempFileName()
    try {
        $statusText = & curl.exe --silent --show-error --output $bodyFile --write-out '%{http_code}' `
            --form "file=@$fixture;type=text/csv" "$ApiBaseUrl/imports"
        if ($LASTEXITCODE -ne 0) {
            throw "curl failed with exit code $LASTEXITCODE while uploading $FileName"
        }
        $status = [int]$statusText
        $body = Get-Content -LiteralPath $bodyFile -Raw
    }
    finally {
        Remove-Item -LiteralPath $bodyFile -Force -ErrorAction SilentlyContinue
    }

    switch ($status) {
        201 { Write-Host "  $FileName -> 201 new import" }
        200 { Write-Host "  $FileName -> 200 deduplicated (digest already COMPLETED); no rows written" }
        409 { Write-Host "  $FileName -> 409 BATCH_PROCESSING (an identical digest is active); no rows written" -ForegroundColor Yellow }
        default { throw "Unexpected HTTP $status for $FileName. Response: $body" }
    }

    if ($body) {
        try {
            $result = $body | ConvertFrom-Json
            Write-Host ("    rowsRead={0} imported={1} (new={2} changed={3} unchanged={4}) rejected={5} problems={6}" -f `
                $result.rowsRead, $result.imported, $result.newCount, $result.changedCount, `
                $result.unchangedCount, $result.rejected, $result.errorCount)
        }
        catch {
            Write-Host "    Response was not JSON; see the API logs for details."
        }
    }
    return $status
}

Write-Host "Seeding ClearGrade through $ApiBaseUrl/imports (real HTTP API)."
$first = Invoke-SeedUpload -FileName 'initial.csv'
if (-not $SkipUpdate) {
    $second = Invoke-SeedUpload -FileName 'update.csv'
}

Write-Host ''
Write-Host 'Seed complete. All rows were written by the application import pipeline.'
Write-Host 'Re-running this script is deterministic: identical digests return 200 deduplicated (or 409 while active), never a new import.'
Write-Host 'To reset: docker compose down -v; then scripts/start.ps1 and re-run this seed.'
if ($first -eq 409 -or ($second -and $second -eq 409)) {
    exit 2
}
exit 0
