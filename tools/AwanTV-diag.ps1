# AwanTV-diag.ps1 - Version 1.0.0
# Collects what is needed to see why the TV or Awan TV is slow, into state\diag\<date-time>\ (ignored by git, never
# packaged): Awan TV's frame statistics, skipped frames, the busiest processes, memory, and Awan TV's own logs.
#
#   1. powershell -ExecutionPolicy Bypass -File tools\AwanTV-diag.ps1 -Reset   (clears the counters and the log)
#   2. use the TV for about 20 seconds (move around Home, Settings ...)
#   3. powershell -ExecutionPolicy Bypass -File tools\AwanTV-diag.ps1          (saves the files)
# When the TV is slow, run step 3 before restarting it: a restart clears what shows the cause.
#
# Usage: AwanTV-diag.ps1 [-Reset] [-Device <serial>]

param([switch]$Reset, [string]$Device = "")

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.0.0"
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
Write-Host "AwanTV-diag v$ScriptVersion  (device: $Serial)"

if ($Reset) {
    Adb shell "dumpsys gfxinfo $Pkg reset" | Out-Null
    Adb logcat -c | Out-Null
    Write-Host "Counters and log cleared. Use the TV for about 20 seconds, then run this again without -Reset."
    exit 0
}

$dir = Join-Path $Root ("state\diag\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $dir | Out-Null
function Save([string]$name, [string[]]$cmd) {
    $out = (Adb @cmd | Out-String)
    [System.IO.File]::WriteAllText((Join-Path $dir $name), $out, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  $name"
}
Save "uptime.txt" @("shell", "uptime")
Save "gfx.txt" @("shell", "dumpsys gfxinfo $Pkg")
Save "choreo.txt" @("logcat", "-d", "-s", "Choreographer")
Save "top.txt" @("shell", "top -b -n 1 -m 20")
Save "meminfo.txt" @("shell", "dumpsys meminfo")
Save "awantv-log.txt" @("logcat", "-d", "-s", "AwanGuard", "AwanLibrary")
Write-Host "Saved in $dir"
