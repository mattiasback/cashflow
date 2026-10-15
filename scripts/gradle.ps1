# Runs the Gradle wrapper and keeps a log of the last run in build/gradle-last.log.
# Usage: .\scripts\gradle.ps1 <tasks and options>
#
# Gradle needs a JDK that runs at normal (medium) integrity. Executables on a drive whose
# root carries a Low integrity label (as X:\ does on the main dev PC) start as Low-integrity
# processes and cannot write to the user profile, so a copy of Android Studio's JBR on C:
# is preferred when present.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$localJbr = Join-Path $env:LOCALAPPDATA 'CashFlowDev\jbr'
if (Test-Path (Join-Path $localJbr 'bin\java.exe')) {
    $env:JAVA_HOME = $localJbr
} elseif (-not $env:JAVA_HOME -and (Test-Path 'X:\AndroidStudio\jbr')) {
    $env:JAVA_HOME = 'X:\AndroidStudio\jbr'
}
$log = Join-Path $root 'build\gradle-last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Push-Location $root
try {
    & .\gradlew.bat @args --console=plain *>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $log
    "EXIT CODE: $LASTEXITCODE" | Tee-Object -FilePath $log -Append
} finally {
    Pop-Location
}
