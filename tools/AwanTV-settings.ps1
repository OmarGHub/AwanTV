# AwanTV-settings.ps1 - Version 1.0.0
# Awan TV's settings as one commented file (.jsonc) over adb:
#   -Export  saves every setting of the TV to a file on this PC (state\settings\), each with a comment: what it is,
#            its values and its default. Edit it in Notepad or VS Code, or send it to another TV as it is.
#   -Import  sends a file to a TV. The TV checks the whole file first: one mistake and nothing changes (the
#            mistakes are listed). A setting left out of the file stays as it is. Before it, the TV's own settings
#            are saved as a backup (state\settings\awantv-backup-<TV serial>-<date>.jsonc).
#   -Restore brings back the settings the TV had before Settings > About > Reset settings, the same way as -Import,
#            with a backup first. The TV keeps one copy per reset (the last five, none when the settings were
#            already a new install's); the newest is restored and every copy is saved in state\settings
#            (awantv-before-reset-<TV serial>-<time of the reset>.jsonc), so an older one can go back with -Import -File.
# What belongs to one TV is not in the file: the screensaver Awan TV replaced, the radio's saved sleep timer, the
# downloads and saved recitations, the weather read, the reminders already shown, the last adhan.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Export [-Device <serial>] [-File <path>]
#        powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Import [-Device <serial>] [-File <path>]
#        powershell -ExecutionPolicy Bypass -File tools\AwanTV-settings.ps1 -Restore [-Device <serial>]
#   -Device  the adb serial or a part of it that stays the same (e.g. 9Z8Y7X6W5V), when more than one device
#            is connected (see: adb devices)
#   -File    Export: where to save (default: state\settings\awantv-settings-<TV serial>-<date>.jsonc)
#            Import: the file to send (default: this TV's newest awantv-settings-<TV serial>-*.jsonc in
#            state\settings\; another TV's file only with -File, so one TV's settings never reach another by chance)
# Example, one TV to another (or export, edit the file, import it back to the same TV):
#   tools\AwanTV-settings.ps1 -Export -Device 1A2B3C4D5E6F7G
#   tools\AwanTV-settings.ps1 -Import -Device 9Z8Y7X6W5V

param([switch]$Export, [switch]$Import, [switch]$Restore, [string]$Device = "", [string]$File = "")

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.0.0"
$Pkg = "app.awanpro.tv"
$Receiver = "$Pkg/app.awanpro.tv.SettingsTransfer"
$Files = "/sdcard/Android/data/$Pkg/files"
$Remote = "$Files/awantv-settings.jsonc"
$Root = Split-Path -Parent $PSScriptRoot
$Dir = Join-Path (Join-Path $Root "state") "settings"

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", "C:\adb\adb.exe")) {
        if (Test-Path $c) { return $c }
    }
    throw "adb.exe not found. Install Android Studio (it brings platform-tools) or put adb in PATH."
}

# A serial or a part of one that stays the same: wireless names change whenever adb or the TV restarts.
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

Write-Host "AwanTV-settings v$ScriptVersion"
if (@($Export, $Import, $Restore | Where-Object { $_ }).Count -ne 1) { throw "Pass one of -Export, -Import or -Restore." }

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

# Sends the action to the app and returns its answer; on failure prints the app's reasons, numbered, and stops.
function Send([string]$action) {
    # 0x20: FLAG_INCLUDE_STOPPED_PACKAGES, so it arrives even if the app was never opened since a reboot.
    $out = Shell "am broadcast -f 0x20 -n $Receiver -a $action"
    if ($out -match 'result=(-?\d+)(, data="(.*)")?') {
        $code = [int]$Matches[1]
        $data = $Matches[3]
        if ($code -eq 1) { return $data }
        if ($data) {
            $parts = @($data -split " \| ")
            Write-Host "TV: $($parts[0])"
            for ($i = 1; $i -lt $parts.Count; $i++) { Write-Host ("  {0}. {1}" -f $i, $parts[$i]) }
            throw "Nothing was changed on the TV."
        }
    }
    throw "Awan TV did not answer. adb said: $out"
}

# Exports the TV's settings and copies the file to $dest; $say prints the TV's answer before adb's own line.
function Export-To([string]$dest, [bool]$say) {
    $answer = Send "$Pkg.EXPORT"
    if ($say) { Write-Host "TV: $answer" }
    Adb pull $Remote $dest | Out-Null
    Adb shell "rm -f $Remote" | Out-Null
    if (-not (Test-Path $dest)) { throw "Could not copy $Remote from the TV." }
    return $answer
}

function Tv-Id {
    $id = (Shell "getprop ro.serialno") -replace "[^A-Za-z0-9._-]", "_"
    if (-not $id) { $id = "tv" }
    return $id
}

$model = Shell "getprop ro.product.model"
$version = ((Shell "dumpsys package $Pkg") -split "`n" | Select-String -Pattern "versionName=(\S+)" | Select-Object -First 1)
if (-not $version) { throw "Awan TV ($Pkg) is not installed on $model." }
$version = $version.Matches[0].Groups[1].Value
Write-Host "Device: $model, Awan TV $version"

New-Item -ItemType Directory -Force -Path $Dir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

if ($Export) {
    if (-not $File) { $File = Join-Path $Dir ("awantv-settings-$(Tv-Id)-$stamp.jsonc") }
    Export-To $File $true | Out-Null
    Write-Host "Saved: $File"
    Write-Host "Edit it (Notepad or VS Code), then: tools\AwanTV-settings.ps1 -Import -File `"$File`" -Device <serial>"
} else {
    if ($Restore) {
        # Every copy the TV kept, newest first: ordinal, as the names sort by time (the culture's sort may skip the
        # hyphens).
        $listed = Shell "ls -1 $Files 2>/dev/null"
        $all = @($listed -split "`n" | ForEach-Object { $_.Trim() })
        [string[]]$names = @($all | Where-Object { $_ -match "^awantv-settings-before-reset-\d{8}-\d{6}\.jsonc$" })
        [Array]::Sort($names, [StringComparer]::Ordinal)
        [Array]::Reverse($names)
        if ($names.Count -eq 0) { throw "This TV has no settings kept from before a reset." }
        $copies = @()
        foreach ($n in $names) {
            $when = if ($n -match "-(\d{8}-\d{6})\.jsonc$") { $Matches[1] } else { "copy" }
            $local = Join-Path $Dir ("awantv-before-reset-$(Tv-Id)-$when.jsonc")
            Adb pull "$Files/$n" $local | Out-Null
            if (-not (Test-Path $local)) { throw "Could not copy $Files/$n from the TV." }
            $copies += $local
        }
        $File = $copies[0]
        if ($copies.Count -gt 1) {
            Write-Host "Copies kept by the TV, newest first (the first is restored now; another: -Import -File <path>):"
            for ($i = 0; $i -lt $copies.Count; $i++) { Write-Host ("  {0}. {1}" -f ($i + 1), $copies[$i]) }
        }
    } elseif (-not $File) {
        # This TV's own newest file: another TV's settings go only by -File, never by chance.
        $newest = Get-ChildItem -Path $Dir -Filter "awantv-settings-$(Tv-Id)-*.jsonc" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $newest) { throw "No saved settings of this TV ($(Tv-Id)) in $Dir. Export from it first, or pass -File <path> (another TV's file too)." }
        $File = $newest.FullName
    }
    if (-not (Test-Path $File)) { throw "File not found: $File" }
    Write-Host "File: $File"
    # The TV's own settings first, so this can be undone: -Import -File <the backup>.
    $backup = Join-Path $Dir ("awantv-backup-$(Tv-Id)-$stamp.jsonc")
    Export-To $backup $false | Out-Null
    Write-Host "Backup of the TV's settings: $backup"
    # The app's files folder exists once the app has run; mkdir makes sure.
    Adb shell "mkdir -p $Files" | Out-Null
    Adb push $File $Remote | Out-Null
    if ((Shell "test -f $Remote && echo yes") -ne "yes") { throw "Could not copy the file to the TV ($Remote)." }
    try {
        $answer = Send "$Pkg.IMPORT"
    } finally {
        Adb shell "rm -f $Remote" | Out-Null
    }
    Write-Host "TV: $answer"
    Write-Host "Done. To undo: tools\AwanTV-settings.ps1 -Import -File `"$backup`" -Device <serial>"
}
