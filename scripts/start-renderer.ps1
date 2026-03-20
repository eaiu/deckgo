$ErrorActionPreference = "Stop"

Push-Location "$PSScriptRoot\..\frontend"
try {
    if (-not (Test-Path "node_modules")) {
        npm install
    }
    npm run dev:renderer
} finally {
    Pop-Location
}
