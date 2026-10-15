# Runs the Gradle wrapper with Android Studio's bundled JDK and keeps a log of the last run
# in build/gradle-last.log.
# Usage: .\scripts\gradle.ps1 <tasks and options>
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path 'X:\AndroidStudio\jbr')) { $env:JAVA_HOME = 'X:\AndroidStudio\jbr' }
$log = Join-Path $root 'build\gradle-last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Push-Location $root
try {
    & .\gradlew.bat @args --console=plain *>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $log
    "EXIT CODE: $LASTEXITCODE" | Tee-Object -FilePath $log -Append
} finally {
    Pop-Location
}
