# AwanTV-clean.ps1 - Version 1.0.0
# Turns off a TV's ads, tracking and preloads over adb from a list in tools\clean\ (one package a line), for the
# TV's user only (pm disable-user --user 0): nothing is uninstalled, and the apps keep their data. Each package is
# checked after it, and the TV must still answer and still open its Home app at the end. What this run turned off is
# logged in state\clean\<TV serial>\clean-<date-time>.txt, which AwanTV-clean-undo.ps1 reads to turn it back on.
# Run AwanTV-tv-backup.ps1 first.
#
# Never touched, whatever a list says: the TV's Home app, the TV/HDMI inputs, Wi-Fi setup, the remote and its mic,
# the keyboards, Google Play and Play Services, Android's core, and what Awan TV runs over (the stock launcher, the
# screensaver it puts back, setup, accessibility and speech).
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean.ps1 [-Device <serial or part of it>] [-List <file>] [-DryRun]
#   -Device  the adb serial or a part of it that stays the same (e.g. 1A2B3C4D5E6F7G)
#   -List    the list in tools\clean\ (default: chosen by the TV's model: "Smart TV Pro" -> tcl-smart-tv-pro.txt,
#            "Google TV Streamer" -> google-tv-streamer.txt, "BeyondTV" on Android 11 -> tcl-beyondtv-android11.txt,
#            "Chromecast" / "Chromecast HD" (with Google TV) -> google-chromecast-gtv.txt)
#   -DryRun  show what would be turned off, change nothing

param([string]$Device = "", [string]$List = "", [switch]$DryRun)

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.0.0"
$Root = Split-Path -Parent $PSScriptRoot
# By model, or by "model/Android SDK" where one model name runs several firmwares (BeyondTV: Android 9 and 11).
$Lists = @{
    "Smart TV Pro" = "tcl-smart-tv-pro.txt"; "Google TV Streamer" = "google-tv-streamer.txt";
    "BeyondTV/30" = "tcl-beyondtv-android11.txt"; "Chromecast" = "google-chromecast-gtv.txt"; "Chromecast HD" = "google-chromecast-gtv.txt"
}

# Never turned off: a TV left without these may lose its picture, Wi-Fi, remote or boot (CleanYourTV PROMPT.md and
# its TCL lists), or Awan TV loses what it runs over.
$Never = @(
    "android", "com.android.systemui", "com.android.shell", "com.android.phone", "com.android.settings",
    "com.android.tv.settings", "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
    "com.android.permissioncontroller", "com.google.android.permissioncontroller",
    "com.android.packageinstaller", "com.google.android.packageinstaller",
    "com.google.android.ext.services", "com.google.android.ext.shared", "com.google.android.modulemetadata",
    "com.android.bluetooth", "com.android.inputdevices", "com.android.providers.settings",
    "com.android.providers.tv", "com.android.providers.media", "com.android.providers.media.module",
    "com.android.providers.downloads", "com.android.networkstack", "com.android.wifi.resources",
    "com.tcl.tv", "com.tcl.tvinput", "com.tcl.dashboard", "com.tcl.suspension", "com.tcl.autopair",
    "com.tcl.systemserver", "com.tcl.providers.config", "com.tcl.initsetup", "com.tcl.useragreement",
    "com.tcl.globalkeyoverlay", "com.tcl.systemui.plugin", "com.tcl.factory.view", "com.tcl.eva", "com.tcl.hearaid",
    "com.tcl.t_solo", "com.tcl.inputmethod.international", "tv.mopa.ginga",
    "com.google.android.inputmethod.latin", "com.google.android.katniss", "com.google.android.tv.remote.service",
    "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.apps.tv.dreamx",
    "com.google.android.tungsten.setupwraith", "com.google.android.marvin.talkback", "com.google.android.tts",
    "com.google.android.webview", "app.awanpro.tv",
    # Google TV Streamer: the remote's firmware updates, its pairing and audio, the Ambient screensaver, Bluetooth.
    "com.google.android.tv.dfuservice", "com.google.android.tv.overlay.dfuservice", "com.google.android.tv.axel",
    "com.google.android.backdrop", "com.google.android.bluetooth", "com.android.bluetooth.chromecast",
    "com.google.android.networkstack", "com.google.android.wifi.resources", "com.google.android.audioproxy",
    # Older TCL (BeyondTV, Realtek, Android TV 9 and 11): the source menu, HDMI, remote keys and its Bluetooth, picture
    # and sound, device sign-in, set-up data, Netflix's device id, the Wi-Fi country and the firmware updater.
    "com.tcl.sourcemananger", "com.tcl.tvpassthrough", "com.tcl.keycustomfunctionservice", "com.tcl.framework.custom",
    "com.tcl.tcl_bt_rcu_service", "com.tcl.settings", "com.tcl.showmode", "com.tcl.xian.StartandroidService",
    "com.tcl.appstatecontroller", "com.tcl.copydatatotv", "com.realtek.netflix.setting",
    "com.android.WifiCountryService", "com.tcl.versionUpdateApp", "com.tcl.rc.ota"
)
$NeverPrefix = @("com.google.android.overlay.modules", "com.mediatek.android.tv", "com.dolby.")

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
function HomeApp { (@(Adb shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME") | Where-Object { "$_".Trim() } | Select-Object -Last 1) }

Write-Host "AwanTV-clean v$ScriptVersion  (adb: $AdbExe)"
$model = Shell "getprop ro.product.model"
if (-not $model) { throw "The device did not answer ($Serial)." }
Write-Host "[1/5] Device: $model, Android $(Shell 'getprop ro.build.version.release') ($Serial)"

# --- the list
$sdk = Shell "getprop ro.build.version.sdk"
if (-not $List) {
    if ($Lists.ContainsKey("$model/$sdk")) { $List = $Lists["$model/$sdk"] }
    elseif ($Lists.ContainsKey($model)) { $List = $Lists[$model] }
    else { throw "No list for '$model' (Android SDK $sdk) in tools\clean\. Pass -List <file>." }
}
$listPath = $List
if (-not (Test-Path $listPath)) { $listPath = Join-Path $PSScriptRoot "clean\$List" }
if (-not (Test-Path $listPath)) { throw "List not found: $List" }
$wanted = @(Get-Content $listPath -Encoding UTF8 | ForEach-Object { ($_ -replace "#.*$", "").Trim() } | Where-Object { $_ } | Select-Object -Unique)
Write-Host "[2/5] List: $(Split-Path $listPath -Leaf), $($wanted.Count) packages"

# --- what is on the TV, and what the TV opens as Home
$installed = Packages ""
$disabled = Packages "-d"
$homeBefore = HomeApp
$homePkg = ($homeBefore -split "/")[0]
if (-not $homePkg -or $homePkg -notlike "*.*") { throw "Could not read the TV's Home app ($homeBefore); nothing was changed." }
Write-Host "[3/5] Home app: $homeBefore"

$todo = @(); $skipped = @()
foreach ($p in $wanted) {
    $guarded = ($Never -contains $p) -or ($p -eq $homePkg) -or (@($NeverPrefix | Where-Object { $p.StartsWith($_) }).Count -gt 0)
    if ($guarded) { $skipped += "$p (protected)" }
    elseif ($installed -notcontains $p) { $skipped += "$p (not on this TV)" }
    elseif ($disabled -contains $p) { $skipped += "$p (already off)" }
    else { $todo += $p }
}
foreach ($s in $skipped) { Write-Host "      skip  $s" }

if ($DryRun) {
    Write-Host "[4/5] Dry run: would turn off $($todo.Count):"
    $i = 0
    foreach ($p in $todo) { $i++; Write-Host ("      {0,2}. {1}" -f $i, $p) }
    Write-Host "[5/5] Nothing changed."
    return
}
if ($todo.Count -eq 0) { Write-Host "[4/5] Nothing to turn off."; Write-Host "[5/5] Nothing changed."; return }

# --- one package a call (chained pm commands can stop after the first), each checked, each logged at once
$id = ((Shell "getprop ro.serialno") -replace "[^A-Za-z0-9._-]", "_")
if (-not $id) { $id = $Serial -replace "[^A-Za-z0-9._-]", "_" }
$logDir = Join-Path $Root "state\clean\$id"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir ("clean-" + (Get-Date -Format "yyyy-MM-dd_HHmmss") + ".txt")
"# AwanTV-clean v$ScriptVersion, $model ($Serial), list $(Split-Path $listPath -Leaf); turned off by this run:" | Out-File -Encoding utf8 $log
Write-Host "[4/5] Turning off $($todo.Count) (log: $log)"
$ok = 0; $failed = @(); $i = 0
foreach ($p in $todo) {
    $i++
    $out = Shell "pm disable-user --user 0 $p"
    $nowOff = (Packages "-d") -contains $p
    if ($nowOff) { $ok++; $p | Out-File -Encoding utf8 -Append $log } else { $failed += $p }
    Write-Host ("      {0,2}/{1}  {2}  {3}" -f $i, $todo.Count, $(if ($nowOff) { "off " } else { "FAIL" }), $p)
    if (-not $nowOff -and $out) { Write-Host "            $out" }
}

# --- the TV must still answer and still have its Home app
$homeAfter = HomeApp
$alive = (Shell "echo ok") -eq "ok"
Write-Host "[5/5] Turned off $ok of $($todo.Count); TV answers: $(if ($alive) { 'yes' } else { 'NO' }); Home app: $homeAfter"
if ($failed.Count -gt 0) { Write-Host "      Not turned off: $($failed -join ', ')" }
if (-not $alive -or $homeAfter -ne $homeBefore) {
    Write-Host ""
    Write-Host "WARNING: the TV changed in a way it should not have. Undo now:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean-undo.ps1 -Device $Device"
    exit 1
}
Write-Host ""
Write-Host "Done. Restart the TV once and check: picture, HDMI inputs, Wi-Fi, the remote's mic, Home, Awan TV."
Write-Host "Undo: powershell -ExecutionPolicy Bypass -File tools\AwanTV-clean-undo.ps1 -Device <serial or part of it>"
