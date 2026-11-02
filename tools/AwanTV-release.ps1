# AwanTV-release.ps1 - Version 1.0.1
# Gathers the files of a GitHub release into release\ (ignored by git). Their names stay the same in every
# version, so the guide's .../releases/latest/download/<name> links keep working:
#   AwanTV.apk           the TV app (app\release\AwanTV.apk)
#   AwanTVInstaller.apk  the phone installer (installer\release\AwanTVInstaller.apk), which carries AwanTV.apk
#   AwanTV-Windows.zip   AwanTV\ with AwanTV.apk, AwanTV-install.cmd/.ps1, AwanTV-restore.cmd/.ps1 and README.txt
# It checks that the installer carries this very AwanTV.apk and that the app carries no spoken reminders (their
# Microsoft voice is not ours to distribute), and prints each file's SHA-256 for the release notes.
# Build both first: Build > Generate Signed App Bundle or APK > APK > app > release, then the same for installer.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-release.ps1

$ErrorActionPreference = "Stop"
$ScriptVersion = "1.0.1"
$Root = Split-Path -Parent $PSScriptRoot
$App = Join-Path $Root "app\release\AwanTV.apk"
$Installer = Join-Path $Root "installer\release\AwanTVInstaller.apk"
$Out = Join-Path $Root "release"
$Package = @("AwanTV-install.cmd", "AwanTV-install.ps1", "AwanTV-restore.cmd", "AwanTV-restore.ps1")

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Write-Host "AwanTV-release v$ScriptVersion"

foreach ($f in @($App, $Installer)) {
    if (-not (Test-Path $f)) { throw "Not found: $f  (Build > Generate Signed App Bundle or APK > APK > release)" }
}
$version = [regex]::Match((Get-Content (Join-Path $Root "gradle.properties") -Raw), "(?m)^awanVersion=(\S+)").Groups[1].Value

# The spoken reminders (tools\AwanTV-fetch-announce.ps1) are for one's own build only: a release never carries them.
$zip = [IO.Compression.ZipFile]::OpenRead($App)
try {
    $voiced = @($zip.Entries | Where-Object { $_.FullName -like "assets/announce/*" }).Count
} finally { $zip.Dispose() }
if ($voiced -gt 0) {
    throw "AwanTV.apk carries $voiced spoken reminder(s), a Microsoft voice not ours to distribute. Delete app\src\main\assets\announce\*.mp3, then build the app and the installer again."
}

# The installer carries the app as it was when the installer was built: a later app build is not in it.
$zip = [IO.Compression.ZipFile]::OpenRead($Installer)
try {
    $entry = $zip.GetEntry("assets/AwanTV.apk")
    if (-not $entry) { throw "The installer carries no assets/AwanTV.apk. Build the installer again." }
    $sha = [Security.Cryptography.SHA256]::Create()
    $s = $entry.Open()
    try { $carried = -join ($sha.ComputeHash($s) | ForEach-Object { $_.ToString("x2") }) } finally { $s.Dispose(); $sha.Dispose() }
} finally { $zip.Dispose() }
if ($carried -ne (Get-FileHash $App -Algorithm SHA256).Hash.ToLower()) {
    throw "The installer carries an older AwanTV.apk than app\release\AwanTV.apk. Build the installer again."
}

if (Test-Path $Out) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Path $Out | Out-Null
Copy-Item $App, $Installer $Out

# AwanTV-Windows.zip: one AwanTV\ folder, so unzipping a new version over an old one keeps state\ and platform-tools\.
$stage = Join-Path $env:TEMP ("awantv-release-" + [guid]::NewGuid().ToString("N"))
$folder = Join-Path $stage "AwanTV"
New-Item -ItemType Directory -Path $folder | Out-Null
try {
    Copy-Item $App $folder
    foreach ($n in $Package) { Copy-Item (Join-Path $PSScriptRoot $n) $folder }
    Copy-Item (Join-Path $PSScriptRoot "AwanTV-Windows-README.txt") (Join-Path $folder "README.txt")
    # Each entry by hand, named "AwanTV/<file>" with "/" as the zip format asks: Windows PowerShell's CreateFromDirectory
    # writes "\", which macOS and Linux tools take as part of a file name.
    $zip = [IO.Compression.ZipFile]::Open((Join-Path $Out "AwanTV-Windows.zip"), [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($f in Get-ChildItem $folder -File) {
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, "AwanTV/" + $f.Name,
                [IO.Compression.CompressionLevel]::Optimal)
        }
    } finally {
        $zip.Dispose()
    }
} finally {
    Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "Awan TV $version, ready to upload from $Out"
Write-Host "SHA-256:"
Get-ChildItem $Out | Sort-Object Name | ForEach-Object {
    Write-Host ("  {0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name)
}
