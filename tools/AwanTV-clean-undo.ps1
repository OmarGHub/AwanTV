# AwanTV-clean-undo.ps1 - Version 1.0.0
# Turns back on, over adb, what AwanTV-clean.ps1 turned off on a TV: every package in its logs in
# state\clean\<TV serial>\ (or only the log given), one a call, each checked; a log fully undone becomes undone-*.txt. Packages that were already off before
# AwanTV-clean ran are not in the logs and are left as they are.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean-undo.ps1 [-Device <serial or part of it>] [-Log <file>] [-Package <name>]
#   -Device   the adb serial or a part of it that stays the same (e.g. 1A2B3C4D5E6F7G)
#   -Log      only the packages of this log (default: all logs of this TV)
#   -Package  only this one package (e.g. com.tcl.gallery), logged or not

param([string]$Device = "", [string]$Log = "", [string]$Package = "")

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.0.0"
$Root = Split-Path -Parent $PSScriptRoot

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", "C:\adb\adb.exe")) {
        if (Test-Path $c) { return $c }
    }
    throw "adb.exe not found. Install Android Studio (it brings platform-tools) or put adb in PATH."
}

# A serial or a part of one; one TV listed twice ("name" and "name (2)") is still one TV.
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
function Shell([string]$cmd) { (Adb shell $cmd | Out-String).Trim() }
function Packages([string]$flag) { @(Adb shell "pm list packages $flag" | ForEach-Object { "$_".Trim() -replace "^package:", "" } | Where-Object { $_ }) }

Write-Host "AwanTV-clean-undo v$ScriptVersion  (adb: $AdbExe)"
$model = Shell "getprop ro.product.model"
if (-not $model) { throw "The device did not answer ($Serial)." }
Write-Host "[1/3] Device: $model ($Serial)"

# --- what to turn back on
if ($Package) {
    $todo = @($Package)
    Write-Host "[2/3] Package: $Package"
} else {
    $id = ((Shell "getprop ro.serialno") -replace "[^A-Za-z0-9._-]", "_")
    if (-not $id) { $id = $Serial -replace "[^A-Za-z0-9._-]", "_" }
    if ($Log) { $logs = @(Get-Item $Log) }
    else { $logs = @(Get-ChildItem (Join-Path $Root "state\clean\$id") -Filter "clean-*.txt" -ErrorAction SilentlyContinue) }
    if ($logs.Count -eq 0) { throw "No AwanTV-clean log for this TV in state\clean\$id\. Pass -Log <file> or -Package <name>." }
    $todo = @($logs | ForEach-Object { Get-Content $_.FullName -Encoding UTF8 } | ForEach-Object { "$_".Trim() } |
        Where-Object { $_ -and -not $_.StartsWith("#") } | Select-Object -Unique)
    Write-Host "[2/3] From $($logs.Count) log(s): $($todo.Count) packages"
}

# --- one package a call, each checked
$ok = 0; $failed = @(); $i = 0
foreach ($p in $todo) {
    $i++
    $out = Shell "pm enable --user 0 $p"
    $on = (Packages "-e") -contains $p
    if ($on) { $ok++ } else { $failed += $p }
    Write-Host ("      {0,2}/{1}  {2}  {3}" -f $i, $todo.Count, $(if ($on) { "on  " } else { "FAIL" }), $p)
    if (-not $on -and $out) { Write-Host "            $out" }
}
Write-Host "[3/3] Turned back on $ok of $($todo.Count)"
# Logs fully undone are renamed (clean- to undone-), so a later undo does not turn them on again.
if (-not $Package -and $failed.Count -eq 0) {
    foreach ($l in $logs) { Rename-Item $l.FullName ($l.Name -replace "^clean-", "undone-") }
}
if ($failed.Count -gt 0) { Write-Host "      Not turned on: $($failed -join ', ')" }
Write-Host ""
Write-Host "Done. Restart the TV once."
