# AwanTV-build-jakim.ps1 - Version 1.0.0
# Works out Malaysia's zone times (Awan TV's JAKIM method) from JAKIM's own timetables and writes
# app\src\main\assets\jakim\zones.tsv. Needs the timetables first: tools\AwanTV-fetch-jakim.ps1 (state\jakim\<year>).
# The fit runs tools\jakim\BuildJakim.java with the app's own JakimSun.java, so the app computes the times with the
# very code that fitted them. It takes a while (every zone and prayer is searched); it prints each zone's share of
# minutes matched, on the years fitted and on the earliest year, which is kept out of the fit to check it.
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-build-jakim.ps1

$ErrorActionPreference = "Stop"
$ScriptVersion = "1.0.0"
Write-Host "AwanTV-build-jakim v$ScriptVersion"

$Root = Split-Path -Parent $PSScriptRoot

# A JDK: JAVA_HOME's, Android Studio's own (jbr), or one on the PATH.
function Find-Jdk([string]$tool) {
    $places = @()
    if ($env:JAVA_HOME) { $places += Join-Path $env:JAVA_HOME "bin\$tool.exe" }
    $places += "$env:ProgramFiles\Android\Android Studio\jbr\bin\$tool.exe"
    $places += "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\$tool.exe"
    foreach ($p in $places) { if (Test-Path $p) { return $p } }
    $c = Get-Command $tool -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    return $null
}

$javac = Find-Jdk "javac"
$java = Find-Jdk "java"
if (-not $javac -or -not $java) {
    Write-Host "No JDK found. Install Android Studio (it carries one) or set JAVA_HOME."
    exit 1
}

$tables = Join-Path $Root "state\jakim"
if (-not (Get-ChildItem -Path $tables -Directory -ErrorAction SilentlyContinue)) {
    Write-Host "No timetables in $tables. Run tools\AwanTV-fetch-jakim.ps1 first."
    exit 1
}

$classes = Join-Path $Root "state\jakim\classes"
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = @(
    (Join-Path $Root "tools\jakim\BuildJakim.java"),
    (Join-Path $Root "app\src\main\java\app\awanpro\tv\JakimSun.java")
)
& $javac -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { Write-Host "Compiling failed."; exit 1 }

& $java -cp $classes app.awanpro.tv.BuildJakim $Root
if ($LASTEXITCODE -ne 0) { Write-Host "The fit stopped."; exit 1 }
Write-Host ""
Write-Host "Written: app\src\main\assets\jakim\zones.tsv"
