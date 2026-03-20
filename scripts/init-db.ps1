$ErrorActionPreference = "Stop"

$dbName = $env:DECKGO_DB_NAME
if ([string]::IsNullOrWhiteSpace($dbName)) {
    $dbName = "deckgo"
}

$adminDb = $env:DECKGO_ADMIN_DB
if ([string]::IsNullOrWhiteSpace($adminDb)) {
    $adminDb = "postgres"
}

$dbUser = $env:DECKGO_DB_USERNAME
if ([string]::IsNullOrWhiteSpace($dbUser)) {
    $dbUser = "postgres"
}

$dbPassword = $env:DECKGO_DB_PASSWORD
if (-not [string]::IsNullOrWhiteSpace($dbPassword)) {
    $env:PGPASSWORD = $dbPassword
}

$exists = psql -U $dbUser -d $adminDb -tAc "SELECT 1 FROM pg_database WHERE datname = '$dbName'"
if ($exists -eq "1") {
    Write-Host "Database '$dbName' already exists."
    exit 0
}

psql -U $dbUser -d $adminDb -c "CREATE DATABASE $dbName"
Write-Host "Database '$dbName' created."
