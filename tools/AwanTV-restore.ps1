# AwanTV-restore.ps1 - Version 1.2.0
# Undoes AwanTV-install.ps1: turns the Awan TV service off (the TV's own home screen is back at once), puts back
# the screensaver saved at install, gives the remote's buttons back to a TCL TV's own remapping, and with -Uninstall
# removes the app. Nothing else on the TV is touched.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-restore.ps1 [-Uninstall] [-RemoveRecitations] [-Device <serial or part of it>]
#   -RemoveRecitations  also deletes the saved recitations (Music/Awan TV), which otherwise stay for a later install
#   or double-click AwanTV-restore.cmd (in the Windows package, AwanTV-Windows.zip, too)

param([switch]$Uninstall, [switch]$RemoveRecitations, [string]$Device = "")

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.2.0"
$Pkg = "app.awanpro.tv"
$Service = "$Pkg/app.awanpro.tv.RemoteKeys"
# In the Windows package (AwanTV.apk or state\ beside this script) everything is in this folder;
# in the repository (tools\) the release build and state\ are in the repository root.
$Packaged = (Test-Path (Join-Path $PSScriptRoot "AwanTV.apk")) -or (Test-Path (Join-Path $PSScriptRoot "state"))
$Root = if ($Packaged) { $PSScriptRoot } else { Split-Path -Parent $PSScriptRoot }

$GoogleTerms = "https://developer.android.com/tools/releases/platform-tools"
$GoogleZip = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

# adb: Google's platform-tools in this folder or beside it (where its zip unzips), on PATH, Android Studio's, C:\adb.
# Not found in the Windows package: offered from Google, after its terms, into this folder's platform-tools.
function Find-Adb {
    $near = @((Join-Path $PSScriptRoot "platform-tools\adb.exe"),
        (Join-Path (Split-Path -Parent $PSScriptRoot) "platform-tools\adb.exe"))
    foreach ($c in $near) { if (Test-Path $c) { return $c } }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", "C:\adb\adb.exe")) {
        if (Test-Path $c) { return $c }
    }
    if (-not $Packaged) { throw "adb.exe not found. Install Android Studio (it brings platform-tools) or put adb in PATH." }
    Write-Host "adb was not found. Download Android SDK Platform-Tools from Google?"
    Write-Host "By downloading you agree to Google's terms: $GoogleTerms"
    if ((Read-Host "Download now? [Y/N]") -notmatch '^[Yy]') {
        throw "adb is needed: download Platform-Tools from $GoogleTerms, unzip it beside the AwanTV folder, then run again."
    }
    Write-Host "Downloading Platform-Tools..."
    $zip = Join-Path $env:TEMP "awantv-platform-tools.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = "SilentlyContinue"  # Windows PowerShell's progress bar makes the download many times slower
    try {
        Invoke-WebRequest -Uri $GoogleZip -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $PSScriptRoot -Force  # Google's zip holds platform-tools\
    } catch {
        throw "Could not download Platform-Tools ($($_.Exception.Message)). Download it from $GoogleTerms, unzip it beside the AwanTV folder, then run again."
    } finally {
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
    }
    $adb = Join-Path $PSScriptRoot "platform-tools\adb.exe"
    if (-not (Test-Path $adb)) { throw "The download has no adb.exe. Download Platform-Tools from $GoogleTerms instead." }
    return $adb
}

# The adb server keeps open the folder it was first started from, so Windows refuses to move, rename or delete
# that folder until adb stops. Started from the temp folder, it never holds the project.
function Start-AdbServer([string]$adb) {
    Push-Location $env:TEMP
    try { & $adb start-server 2>&1 | Out-Null } finally { Pop-Location }
}

$script:AdbExe = Find-Adb
Start-AdbServer $script:AdbExe
# A serial or a part of one that stays the same (e.g. 9Z8Y7X6W5V): wireless names change ("name", "name (2)",
# an address and port) whenever adb or the TV restarts. One TV listed twice is still one TV.
function Connected([string]$adb) {
    @(& $adb devices | Select-String -Pattern "^(\S.*?)\s+device$" | ForEach-Object { $_.Matches[0].Groups[1].Value })
}

# No TV connected yet: asks for its address and connects, pairing first when the TV uses Wireless debugging
# (Android 11+), then waits for "Allow USB debugging?" on the TV. The same steps as the phone installer.
function Connect-Tv([string]$adb) {
    Write-Host ""
    Write-Host "No TV is connected to this computer yet."
    Write-Host "Type the TV's address, from Settings > Network > your Wi-Fi (e.g. 192.168.1.20)."
    Write-Host "With Wireless debugging, type the address and port it shows there (e.g. 192.168.1.20:41234)."
    $address = (Read-Host "TV address").Trim()
    if (-not $address) { throw "No address typed." }
    if ($address -notmatch ":\d+$") { $address += ":5555" }
    $out = (& $adb connect $address 2>&1 | Out-String).Trim()
    if ($out -notmatch "^(connected|already connected)") {
        Write-Host "Could not connect ($out)."
        Write-Host "With Wireless debugging, pair first. On the TV: Developer options > Wireless debugging >"
        Write-Host "Pair device with pairing code. Type the address and port shown in that window, then its code."
        $pairAt = (Read-Host "Pairing address and port (e.g. 192.168.1.20:37123), or Enter to stop").Trim()
        if (-not $pairAt) { throw "Not connected. Check that debugging is on and the TV is on the same Wi-Fi, then run again." }
        $code = (Read-Host "Six-digit pairing code").Trim()
        $out = (& $adb pair $pairAt $code 2>&1 | Out-String).Trim()
        if ($out -notmatch "Successfully paired") { throw "Pairing did not work ($out). Open the pairing window on the TV again: it shows a new code each time." }
        $out = (& $adb connect $address 2>&1 | Out-String).Trim()
        if ($out -notmatch "^(connected|already connected)") { throw "Paired, but could not connect to $address ($out)." }
    }
    $told = $false
    for ($i = 0; $i -lt 60; $i++) {
        $line = (& $adb devices | Select-String -SimpleMatch $address | Out-String).Trim()
        if ($line -match "\sdevice$") { Write-Host "Connected to $address"; return }
        if (-not $told -and $line -match "unauthorized") {
            Write-Host "On the TV: tick 'Always allow from this computer', then choose Allow."
            $told = $true
        }
        Start-Sleep -Seconds 1
    }
    throw "The TV did not allow this computer. Run again and choose Allow on the TV."
}

function Select-Device([string]$adb, [string]$wanted) {
    $serials = @(Connected $adb)
    if ($serials.Count -eq 0) {
        Connect-Tv $adb
        $serials = @(Connected $adb)
        if ($serials.Count -eq 0) { throw "No device connected." }
    }
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

$script:Sel = @("-s", (Select-Device $script:AdbExe $Device))
function Adb { & $script:AdbExe @script:Sel @args }
function Shell([string]$cmd) { (Adb shell $cmd | Out-String).Trim() }

# The file that keeps the screensaver replaced, by the TV's own serial (ro.serialno), which no reconnection changes;
# one written by an older version under adb's name for the TV is taken over.
function Saved-File([string]$dir) {
    $id = (Shell "getprop ro.serialno") -replace "[^A-Za-z0-9._-]", "_"
    $old = Join-Path $dir ("screensaver-before-" + (((Adb get-serialno | Out-String).Trim()) -replace "[^A-Za-z0-9._-]", "_") + ".txt")
    if (-not $id) { return $old }
    $file = Join-Path $dir "screensaver-before-$id.txt"
    if (-not (Test-Path $file) -and (Test-Path $old)) {
        $text = (Get-Content $old -Raw -ErrorAction SilentlyContinue | Out-String).Trim()
        if ($text -and $text -ne "null") { Copy-Item $old $file }
    }
    return $file
}

Write-Host "AwanTV-restore v$ScriptVersion  (adb: $script:AdbExe)"
$saved = Saved-File (Join-Path $Root "state")

# --- the service, keeping any other service that is on
$list = Shell "settings get secure enabled_accessibility_services"
$rest = @($list -split ":" | Where-Object { $_ -and $_ -ne "null" -and $_ -notlike "$Pkg/*" })
if ($rest.Count -eq 0) { Shell "settings delete secure enabled_accessibility_services" | Out-Null }
else { Shell ("settings put secure enabled_accessibility_services " + ($rest -join ":")) | Out-Null }
Write-Host "[1/4] Awan TV service off"

# --- screensaver
if (Test-Path $saved) {
    $before = (Get-Content $saved -Raw).Trim()
    if ($before -and $before -ne "null") {
        Shell "settings put secure screensaver_components $before" | Out-Null
        Write-Host "[2/4] Screensaver back to: $before"
    } else {
        Shell "settings delete secure screensaver_components" | Out-Null
        Write-Host "[2/4] Screensaver back to the TV's default"
    }
} else {
    Write-Host "[2/4] No saved screensaver for this TV ($saved); left as it is"
}

# --- TCL's remapping: every button Awan TV wrote carries its full mark (only those: another app's mark may end the
# same way). The entry it replaced (TCL's own, or another app's) waits in AwanSavedKey_<code> and goes back; else
# the entry is emptied and the button is the TV's own again. (Before 1.6.0 that entry was kept in the app.)
$mark = "app.awanpro.tv.KEY_TARGET_MISSING"
$keyList = Shell "settings get global CustomKeyList"
$freed = 0
if ($keyList -and $keyList -ne "null") {
    # PS 5.1 hands a JSON array on as one object: the ForEach-Object takes it apart.
    try { $codes = @(($keyList | ConvertFrom-Json) | ForEach-Object { $_ }) } catch { $codes = @() }
    foreach ($code in $codes) {
        if ("$code" -notmatch "^\d+$") { continue }
        if ((Shell "settings get global CustomKey_$code") -like "*$mark*") {
            $saved = Shell "settings get global AwanSavedKey_$code"
            if ($saved -and $saved -ne "null") {
                # Copied on the TV itself: the entry holds double quotes, which PS 5.1 drops from adb's arguments.
                # No globbing (set -f) and no word splitting (IFS empty), so it goes as it was.
                Shell "set -f; IFS=; settings put global CustomKey_$code `$(settings get global AwanSavedKey_$code)" | Out-Null
            } else {
                Shell "settings put global CustomKey_$code ''" | Out-Null
            }
            $freed++
        }
        Shell "settings delete global AwanSavedKey_$code" | Out-Null
    }
}
if ($freed -gt 0) { Write-Host "[3/4] Remote buttons back to the TV's own: $freed" }
else { Write-Host "[3/4] No remote buttons of Awan TV on this TV" }

# --- app
if ($Uninstall) {
    $out = (Adb uninstall $Pkg | Out-String).Trim()
    Write-Host "[4/4] Uninstall: $out"
    # The saved recitations are in the shared Music folder, so they outlast the app unless asked otherwise.
    if ($RemoveRecitations) {
        Shell "rm -rf '/sdcard/Music/Awan TV'" | Out-Null
        Write-Host "      Saved recitations deleted (Music/Awan TV)"
    } elseif ((Shell "[ -d '/sdcard/Music/Awan TV' ] && echo yes") -eq "yes") {
        Write-Host "      Saved recitations kept in Music/Awan TV (add -RemoveRecitations to delete them)"
    }
} else {
    Write-Host "[4/4] App kept (add -Uninstall to remove it)"
}
Write-Host "Done."
