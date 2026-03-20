$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17\jdk17.0.1"
}

$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if (-not $env:DECKGO_DB_URL) {
    $env:DECKGO_DB_URL = "jdbc:postgresql://localhost:5432/deckgo"
}

Push-Location "$PSScriptRoot\..\backend"
try {
    .\mvnw.cmd -s ".mvn\local-settings.xml" spring-boot:run
} finally {
    Pop-Location
}
