# AwanTV-shot.ps1 - Version 1.0.1
# Takes a screenshot of the TV into state\shots\<installed Awan TV version>\<name>.png, so screenshots stay out of
# the source folders (state\ is ignored by git and never packaged). Nothing is left on the TV.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-shot.ps1 [-Name <name>] [-Device <serial>]
#   -Name    file name without .png (default: the time, HHmmss); an existing name gets -2, -3 ... added
#   -Device  the adb serial or a part of it that stays the same (e.g. 9Z8Y7X6W5V), when more than one device
#            is connected (see: adb devices)

param([string]$Name = "", [string]$Device = "")

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.0.1"
$Pkg = "app.awanpro.tv"
$Root = Split-Path -Parent $PSScriptRoot

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", "C:\adb\adb.exe")) {
        if (Test-Path $c) { return $c }
    }
    throw "adb.exe not found. Install Android Studio (it brings platform-tools) or put adb in PATH."
}

# One TV can be listed twice over wireless debugging ("name" and "name (2)"): that is still one device.
function Select-Device([string]$adb, [string]$wanted) {
    $serials = @(& $adb devices | Select-String -Pattern "^(\S.*?)\s+device$" | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($serials.Count -eq 0) { throw "No device connected. Pair and connect first (Developer options > Wireless debugging)." }
    $match = $serials
    if ($wanted) {
        # The same TV comes back as "name (2)" after adb or the TV restarts: compared without that number.
        $w = $wanted -replace " \(\d+\)", ""
        $match = @($serials | Where-Object { ($_ -replace " \(\d+\)", "").Contains($w) })
        if ($match.Count -eq 0) { throw "No connected device matches '$wanted'. Connected: $($serials -join ', ')" }
    }
    $bases = @($match | ForEach-Object { $_ -replace " \(\d+\)", "" } | Sort-Object -Unique)
    if ($bases.Count -gt 1) { throw "More than one device matches. Pass -Device <serial or part of it>. Connected: $($match -join ', ')" }
    $plain = @($match | Where-Object { $_ -notmatch " \(\d+\)" })
    if ($plain.Count -gt 0) { return $plain[0] }
    return $match[0]
}

# The adb server keeps open the folder it was first started from, so Windows refuses to move, rename or delete
# that folder until adb stops. Started from the temp folder, it never holds the project.
function Start-AdbServer([string]$adb) {
    Push-Location $env:TEMP
    try { & $adb start-server 2>&1 | Out-Null } finally { Pop-Location }
}

$AdbExe = Find-Adb
Start-AdbServer $AdbExe
$Serial = Select-Device $AdbExe $Device
function Adb { & $AdbExe -s $Serial @args }

$version = ((Adb shell "dumpsys package $Pkg" | Select-String -Pattern "versionName=(\S+)" | Select-Object -First 1).Matches.Groups[1].Value)
if (-not $version) { $version = "not-installed" }
$dir = Join-Path $Root "state\shots\$version"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

if (-not $Name) { $Name = Get-Date -Format "HHmmss" }
$file = Join-Path $dir "$Name.png"
$n = 2
while (Test-Path $file) { $file = Join-Path $dir "$Name-$n.png"; $n++ }

Adb shell screencap -p /sdcard/awantv-shot.png | Out-Null
Adb pull /sdcard/awantv-shot.png $file | Out-Null
Adb shell rm -f /sdcard/awantv-shot.png | Out-Null
if (-not (Test-Path $file)) { throw "Screenshot failed (is the TV awake and connected?)" }
# A TV can give an empty file without a word (seen on a TCL BeyondTV that took screenshots before; cause not found
# yet): say so, keep nothing.
if ((Get-Item $file).Length -eq 0) {
    Remove-Item -Force $file
    throw "The TV gave an empty screenshot (screencap wrote nothing). Nothing was saved."
}
Write-Host "AwanTV-shot v$ScriptVersion  ->  $file"
