# AwanTV-install.ps1 - Version 1.0.0
# Installs Awan TV on an Android TV / Google TV over adb and turns it on:
#   1. the app (the APK you built in Android Studio)
#   2. its permissions (radio level bars, sleep timer while the radio plays, Wi-Fi name)
#   3. Awan TV as the TV's screensaver (the screensaver it replaces is saved for AwanTV-restore.ps1)
#   4. the Awan TV accessibility service: Home over the TV's own home screen, and the adhan over any app
#   5. the replaced screensaver handed to the app too, so Settings > Awan TV > Remove can put it back without a PC
# Nothing on the TV is disabled. Run AwanTV-restore.ps1 to undo.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-install.ps1 [-Apk <path>] [-Device <serial>]
#   or double-click AwanTV-install.cmd (in the Windows package, AwanTV-Windows.zip, too)
#   -Apk     default: AwanTV.apk beside this script (the Windows package); in the repository the signed release,
#            app\release\AwanTV.apk (Build > Generate Signed App Bundle or APK > release), when there is one, else
#            the debug build, app\build\outputs\apk\debug\app-debug.apk
#   -Device  the adb serial or a part of it that stays the same (e.g. 9Z8Y7X6W5V), when more than one device
#            is connected (see: adb devices)

param([string]$Apk = "", [string]$Device = "")

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.0.0"
$Pkg = "app.awanpro.tv"
$Dream = "$Pkg/app.awanpro.tv.AwanDream"
$Service = "$Pkg/app.awanpro.tv.RemoteKeys"
# In the Windows package (AwanTV.apk or state\ beside this script) everything is in this folder;
# in the repository (tools\) the release build and state\ are in the repository root.
$Packaged = (Test-Path (Join-Path $PSScriptRoot "AwanTV.apk")) -or (Test-Path (Join-Path $PSScriptRoot "state"))
$Root = if ($Packaged) { $PSScriptRoot } else { Split-Path -Parent $PSScriptRoot }
$State = Join-Path $Root "state"

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

# The adb server keeps open the folder it was first started from, so Windows refuses to move, rename or delete
# that folder until adb stops. Started from the temp folder, it never holds the project.
function Start-AdbServer([string]$adb) {
    Push-Location $env:TEMP
    try { & $adb start-server 2>&1 | Out-Null } finally { Pop-Location }
}

$script:AdbExe = Find-Adb
Start-AdbServer $script:AdbExe
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

Write-Host "AwanTV-install v$ScriptVersion  (adb: $script:AdbExe)"

# --- device (chosen above: a part of the serial is enough; nothing is saved before it answers)
Write-Host "[1/7] Device: $(Shell 'getprop ro.product.model'), Android $(Shell 'getprop ro.build.version.release')"

# --- APK
if (-not $Apk) {
    if ($Packaged) {
        $Apk = Join-Path $Root "AwanTV.apk"
    } else {
        # The signed release when there is one (it is what the TVs keep), else the debug build.
        $Apk = Join-Path $Root "app\release\AwanTV.apk"
        if (-not (Test-Path $Apk)) { $Apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk" }
    }
}
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk  (Android Studio: Build > Generate Signed App Bundle or APK > release, or Build > Build App Bundle(s) / APK(s) > Build APK(s))" }

# --- remember the screensaver Awan TV replaces (only the first time, one file per TV)
New-Item -ItemType Directory -Force -Path $State | Out-Null
$saved = Saved-File $State
if (-not $saved -or $saved -like "*screensaver-before-.txt" -or $saved -like "*-unknown.txt") { throw "Could not read the device serial; nothing was changed." }
$current = Shell "settings get secure screensaver_components"
# An empty or "null" file (an earlier run that could not read the setting) counts as nothing saved.
$have = ""
if (Test-Path $saved) { $have = (Get-Content $saved -Raw -ErrorAction SilentlyContinue | Out-String).Trim() }
if ($have -eq "null") { $have = "" }
if (-not $have -and $current -and $current -ne "null" -and $current -notlike "$Pkg/*") {
    Set-Content -Path $saved -Value $current -Encoding ASCII
    Write-Host "[2/7] Saved the current screensaver: $current"
} elseif ($have) {
    Write-Host "[2/7] Screensaver to restore already saved: $have"
} else {
    Write-Host "[2/7] No earlier screensaver to save (now: $current)"
}

# --- install
Write-Host "[3/7] Installing $Apk  (built $((Get-Item $Apk).LastWriteTime.ToString('yyyy-MM-dd HH:mm')))"
$out = (Adb install -r $Apk | Out-String).Trim()
if ($out -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
    # The TV has Awan TV signed with another key (a debug build, or someone else's): Android takes no update over it.
    Write-Host "      The TV has Awan TV signed with another key, so it cannot be updated in place. Once:"
    Write-Host "      1. tools\AwanTV-settings.ps1 -Export -Device <TV>     (keep the settings)"
    Write-Host "      2. tools\AwanTV-restore.ps1 -Uninstall -Device <TV>   (remove the old one)"
    Write-Host "      3. tools\AwanTV-install.ps1 -Device <TV>              (this again)"
    Write-Host "      4. tools\AwanTV-settings.ps1 -Import -File <the file from step 1> -Device <TV>"
    throw "Install failed: signed with another key. Nothing else was changed."
}
if ($out -notmatch "Success") { throw "Install failed: $out" }
# An update force-stops the app, and Android may handle that a moment after the install answers, taking the service
# out of the list (seen on a Google TV Streamer): its setup waits until that is over.
Start-Sleep -Seconds 5

# --- permissions (granted over adb; the app never asks for them)
Write-Host "[4/7] Permissions"
# The saved recitations in Music/Awan TV: audio read on Android 13+, storage read on 11-12, read and write on 10 and older.
$sdk = 0
[int]::TryParse((Shell "getprop ro.build.version.sdk"), [ref]$sdk) | Out-Null
$storage = if ($sdk -ge 33) { @("READ_MEDIA_AUDIO") } elseif ($sdk -ge 30) { @("READ_EXTERNAL_STORAGE") } else { @("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE") }
$perms = @("android.permission.RECORD_AUDIO", "android.permission.WRITE_SECURE_SETTINGS", "android.permission.ACCESS_FINE_LOCATION") + @($storage | ForEach-Object { "android.permission.$_" })
foreach ($p in $perms) {
    $r = Shell "pm grant $Pkg $p"
    if ($r) { Write-Host "      $p : $r" } else { Write-Host "      $p : granted" }
}
# TCL keeps a sideloaded app's services from starting (at boot, and bound by the system) without its own two app ops
# (as Afnon's install.sh grants them); on other TVs they are refused harmlessly. Granted before the service is on.
foreach ($op in @("APP_AUTO_START", "APP_ASSOC_START", "AUTO_START")) { Shell "cmd appops set $Pkg $op allow 2>/dev/null" | Out-Null }

# --- screensaver
Write-Host "[5/7] Screensaver: Awan TV"
Shell "settings put secure screensaver_components $Dream" | Out-Null
Shell "settings put secure screensaver_enabled 1" | Out-Null

# --- accessibility service (added to any already on, never replacing them)
# Another service of this app (an older build's name) is dropped, so only the current one is listed.
# Up to three tries, each checking that the system bound it, not only that it is listed:
# - missing from the list (Android may still take it out after an update): listed again;
# - listed but unbound (a TCL BeyondTV, Android 11, until the list itself changed): the list deleted, then written
#   again a second later (Android reads the list when it hears of a change and may only see the last write).
function Others {
    $l = Shell "settings get secure enabled_accessibility_services"
    @($l -split ":" | Where-Object { $_ -and $_ -ne "null" -and $_ -notlike "$Pkg/*" })
}
function Listed { (Shell "settings get secure enabled_accessibility_services") -match "(^|:)$([regex]::Escape($Pkg))/($([regex]::Escape($Pkg)))?\.RemoteKeys(:|$)" }
function Bound { (Shell "dumpsys accessibility") -match "Bound services:\{[^}]*label=Awan TV" }
$bound = $false
for ($try = 1; $try -le 3 -and -not $bound; $try++) {
    $list = (@(Others) + @($Service)) -join ":"
    if ($try -gt 1 -and (Listed)) {
        Shell "settings delete secure enabled_accessibility_services" | Out-Null
        Start-Sleep -Seconds 1
    }
    if ($try -eq 1 -or -not (Listed)) { Shell "settings put secure enabled_accessibility_services $list" | Out-Null }
    Shell "settings put secure accessibility_enabled 1" | Out-Null
    Start-Sleep -Seconds 3
    $bound = Bound
}
Write-Host "[6/7] Awan TV service on: $(Shell 'settings get secure enabled_accessibility_services')"
if ($bound) { Write-Host "      Running: yes" }
else { Write-Host "      Running: NO - restart the TV, then check: adb shell dumpsys accessibility (Bound services)" }

# --- hand the replaced screensaver to the app (Settings > Awan TV puts it back, with or without a PC)
$before = ""
if (Test-Path $saved) { $before = (Get-Content $saved -Raw).Trim() }
if ($before -and $before -ne "null" -and $before -notlike "$Pkg/*") {
    # A key that does nothing (Shift) around the start: a TV left untouched may be idle or in its screensaver, and
    # "am start" is no user activity, so it would open into the screensaver. Not WAKEUP (HDMI-CEC on source boxes).
    Shell "input keyevent KEYCODE_SHIFT_LEFT" | Out-Null
    Shell "am start -n $Pkg/app.awanpro.tv.MainActivity --es saver_before $before" | Out-Null
    Start-Sleep -Seconds 2
    Shell "input keyevent KEYCODE_SHIFT_LEFT" | Out-Null
    Write-Host "[7/7] Awan TV knows the screensaver to put back: $before"
} else {
    Write-Host "[7/7] No earlier screensaver to hand over; removing Awan TV goes back to the TV's default"
}

Write-Host ""
Write-Host "Done. Press Home on the remote: Awan TV opens over the TV's home screen."
Write-Host "On the first start it asks for your city (Settings > Prayer times > Location)."
Write-Host "Diagnostics: adb logcat -s AwanGuard"
