# AwanTV-tv-backup.ps1 - Version 1.0.0
# Saves what can be saved from an Android TV / Google TV over adb, before cleaning it (or any other change), into
# state\backup\<TV serial>\<date-time>\ (state\ is ignored by git and never packaged). Nothing on the TV is changed.
#   packages-*.txt   every package: all with its APK path and version, system, user-installed, enabled, disabled
#   home.txt         the HOME app the TV opens, accessibility.txt the services on, screensaver.txt the screensaver
#   settings-*.txt   the system, secure and global settings, as they are now
#   props.txt        the TV's properties (model, Android version, build)
#   apks\<package>\  the APK files of every user-installed app, each checked against its size on the TV
# App data and sign-ins cannot be saved without root (Android 12+ has no adb backup for apps); disabling an app
# keeps its data on the TV, so it comes back as it was when enabled again.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-tv-backup.ps1 [-Device <serial or part of it>] [-NoApks]
#   -Device  the adb serial or a part of it that stays the same (e.g. 1A2B3C4D5E6F7G), when more than one device
#            is connected; wireless names change ("name", "name (2)") when adb restarts, the part does not
#   -NoApks  only the lists and settings, without pulling the APK files
# Reinstall an app from the backup: adb install-multiple (Get-ChildItem <folder>\apks\<package>\*.apk).FullName

param([string]$Device = "", [switch]$NoApks)

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
function Shell([string]$cmd) { @(Adb shell $cmd) }
function Save([string]$name, $lines) {
    # Blank lines from the shell are left out, so an empty list counts 0.
    $lines = @($lines | Where-Object { "$_".Trim() })
    $lines | Out-File -Encoding utf8 -FilePath (Join-Path $Dir $name)
    return $lines.Count
}

Write-Host "AwanTV-tv-backup v$ScriptVersion  (adb: $AdbExe)"
$model = (Shell "getprop ro.product.model" | Out-String).Trim()
$android = (Shell "getprop ro.build.version.release" | Out-String).Trim()
if (-not $model) { throw "The device did not answer ($Serial)." }
$id = ((Shell "getprop ro.serialno" | Out-String).Trim()) -replace "[^A-Za-z0-9._-]", "_"
if (-not $id) { $id = $Serial -replace "[^A-Za-z0-9._-]", "_" }
$Dir = Join-Path $Root ("state\backup\$id\" + (Get-Date -Format "yyyy-MM-dd_HHmmss"))
New-Item -ItemType Directory -Force -Path $Dir | Out-Null
Write-Host "[1/5] Device: $model, Android $android ($Serial)"
Write-Host "      Folder: $Dir"

# --- package lists
$all = Save "packages-all.txt" (Shell "pm list packages -f --show-versioncode" | Sort-Object)
$sys = Save "packages-system.txt" (Shell "pm list packages -s" | Sort-Object)
$userLines = @(Shell "pm list packages -3" | Sort-Object)
$usr = Save "packages-user.txt" $userLines
$on = Save "packages-enabled.txt" (Shell "pm list packages -e" | Sort-Object)
$off = Save "packages-disabled.txt" (Shell "pm list packages -d" | Sort-Object)
if ($all -lt 10) { throw "The package list came back short ($all lines); nothing more saved." }
Write-Host "[2/5] Packages: $all in all, $sys system, $usr user-installed, $on enabled, $off disabled"

# --- what the TV opens and runs
Save "home.txt" (Shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME") | Out-Null
Save "accessibility.txt" (Shell "settings get secure enabled_accessibility_services") | Out-Null
Save "screensaver.txt" (Shell "settings get secure screensaver_components") | Out-Null
$homeApp = (Get-Content (Join-Path $Dir "home.txt") | Select-Object -Last 1)
Write-Host "[3/5] Home app: $homeApp"

# --- settings and properties
$s1 = Save "settings-system.txt" (Shell "settings list system" | Sort-Object)
$s2 = Save "settings-secure.txt" (Shell "settings list secure" | Sort-Object)
$s3 = Save "settings-global.txt" (Shell "settings list global" | Sort-Object)
Save "props.txt" (Shell "getprop") | Out-Null
Write-Host "[4/5] Settings: $s1 system, $s2 secure, $s3 global; properties saved"

# --- APK files of the user-installed apps, each checked against its size on the TV
if ($NoApks) {
    Write-Host "[5/5] APK files: skipped (-NoApks)"
} else {
    $pkgs = @($userLines | ForEach-Object { $_ -replace "^package:", "" } | Where-Object { $_ })
    $okApps = 0; $failed = @()
    $i = 0
    foreach ($p in $pkgs) {
        $i++
        $target = Join-Path $Dir "apks\$p"
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        $paths = @(Shell "pm path $p" | ForEach-Object { $_ -replace "^package:", "" } | Where-Object { $_ -like "/*.apk" })
        $good = $paths.Count -gt 0
        foreach ($remote in $paths) {
            $local = Join-Path $target (Split-Path $remote -Leaf)
            Adb pull $remote $local 2>&1 | Out-Null
            $size = (Shell "stat -c %s '$remote'" | Out-String).Trim()
            if (-not (Test-Path $local) -or (Get-Item $local).Length -ne [long]$size) { $good = $false }
        }
        if ($good) { $okApps++ } else { $failed += $p }
        Write-Host ("      {0,2}/{1}  {2}  {3}" -f $i, $pkgs.Count, $(if ($good) { "ok  " } else { "FAIL" }), $p)
    }
    Write-Host "[5/5] APK files: $okApps of $($pkgs.Count) apps saved and checked"
    if ($failed.Count -gt 0) { Write-Host "      Not saved: $($failed -join ', ')" }
}

Write-Host ""
Write-Host "Done. Backup in: $Dir"
Write-Host "Reinstall an app from it: adb install-multiple (Get-ChildItem `"$Dir\apks\<package>\*.apk`").FullName"
