param(
    [string]$Database = 'pet_shop_api',
    [string]$Batch = 'faker-v1'
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location $projectDirectory
try {
    $seedPython = Join-Path $projectDirectory 'target/seed-venv/Scripts/python.exe'
    if (-not (Test-Path -LiteralPath $seedPython)) {
        python -m venv target/seed-venv
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the local Python virtual environment.' }
    }
    & $seedPython -m pip install -r scripts/seed-requirements.txt
    if ($LASTEXITCODE -ne 0) { throw 'Could not install seed dependencies.' }
    & $seedPython scripts/seed_demo_data.py --database $Database --batch $Batch
    if ($LASTEXITCODE -ne 0) { throw 'Seed failed; inspect the error above. Business inserts are transactional.' }
} finally {
    Pop-Location
}
