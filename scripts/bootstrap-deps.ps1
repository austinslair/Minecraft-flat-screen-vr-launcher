$ErrorActionPreference = "Stop"

Write-Host "Syncing VoxyQuest submodules..."
git submodule sync --recursive
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

git submodule update --init --recursive
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Pinned dependencies:"
git submodule status --recursive
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Dependency bootstrap complete."
