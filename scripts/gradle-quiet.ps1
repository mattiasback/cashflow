# Like gradle.ps1, but prints only failures, test results and the outcome (for scripted runs).
$root = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'gradle.ps1') @args | Out-Null
Get-Content (Join-Path $root 'build\gradle-last.log') |
    Where-Object { $_ -notmatch 'WARNING|RemoteException|^> Task|^\s*$|Deprecated Gradle|warning-mode|docs.gradle.org|Run with --|Get more help|Type-safe project|Calculating task graph|Configuration cache entry|Reusing configuration' } |
    Select-Object -Last 80
