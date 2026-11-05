# AwanTV-release.ps1 - Version 1.3.0
# Gathers the files of a GitHub release into release\ (ignored by git). Their names stay the same in every
# version, so the guide's .../releases/latest/download/<name> links keep working:
#   AwanTV.apk           the TV app (app\release\AwanTV.apk)
#   AwanTVInstaller.apk  the phone installer (installer\release\AwanTVInstaller.apk), which carries AwanTV.apk
#   AwanTV-Windows.zip   AwanTV\ with AwanTV.apk, AwanTV-install.cmd/.ps1, AwanTV-restore.cmd/.ps1 and README.txt
# It checks that the installer carries this very AwanTV.apk, that the app carries no spoken reminders (their
# Microsoft voice is not ours to distribute) and does carry mp3quran.net's lists (1.3.0: the names of saved
# recitations after a reinstall without internet; the release build reads them), and prints each file's SHA-256 for
# the release notes.
#
# Update now (1.7.0) on every TV already out depends for good on four things, checked here before anything is
# gathered: the file named AwanTV.apk on a tag v<X.Y.Z> (SelfUpdate's download address), the same release key
# (Android refuses another), a versionName and versionCode that match awanVersion (a stale APK would tell the TVs of a
# version they then never get), and a version above the one published (Android refuses an older versionCode).
# The phone installer's own update (1.11.0) downloads AwanTVInstaller.apk from the same tag: the installer must have the
# app's very version (checked here) and keep that file name.
# Build both first: Build > Generate Signed App Bundle or APK > APK > app > release, then the same for installer.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-release.ps1

$ErrorActionPreference = "Stop"
$ScriptVersion = "1.3.0"
# The release key's certificate (README): TVs take an update only when it is signed with the same key.
$ReleaseCert = "b445fc840602ca363c3ad83b60db22da1d04737633a47622d2405e97a3b50ed3"
$Latest = "https://api.github.com/repos/OmarGHub/AwanTV/releases/latest"
$Root = Split-Path -Parent $PSScriptRoot
$App = Join-Path $Root "app\release\AwanTV.apk"
$Installer = Join-Path $Root "installer\release\AwanTVInstaller.apk"
$Out = Join-Path $Root "release"
$Package = @("AwanTV-install.cmd", "AwanTV-install.ps1", "AwanTV-restore.cmd", "AwanTV-restore.ps1")

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# The first signer's certificate in the APK Signing Block (v3, else v2): the SHA-256 that apksigner prints.
function Get-ApkCert([string]$Path) {
    $b = [IO.File]::ReadAllBytes($Path)
    $eocd = -1
    for ($i = $b.Length - 22; $i -ge [Math]::Max(0, $b.Length - 65557); $i--) {
        if ([BitConverter]::ToUInt32($b, $i) -eq 0x06054b50) { $eocd = $i; break }
    }
    if ($eocd -lt 0) { throw "$Path is not a zip file." }
    $cd = [int][BitConverter]::ToUInt32($b, $eocd + 16)
    if ($cd -lt 32 -or [Text.Encoding]::ASCII.GetString($b, $cd - 16, 16) -ne "APK Sig Block 42") {
        throw "$Path has no v2/v3 signature (Build > Generate Signed App Bundle or APK)."
    }
    $start = $cd - [int][BitConverter]::ToUInt64($b, $cd - 24) - 8
    $found = @{}
    $p = $start + 8
    while ($p -lt $cd - 24) {
        $len = [int][BitConverter]::ToUInt64($b, $p)
        $id = [BitConverter]::ToUInt32($b, $p + 8)
        $found[$id] = $p + 12
        $p += 8 + $len
    }
    $at = $null
    # 0xf05368c0 (v3) and 0x7109871a (v2), written in decimal: PowerShell reads 0xf05368c0 as a negative Int32.
    foreach ($id in @([uint32]4031998144, [uint32]1896449818)) { if ($found.ContainsKey($id)) { $at = $found[$id]; break } }
    if ($null -eq $at) { throw "$Path has no v2/v3 signature (Build > Generate Signed App Bundle or APK)." }
    # signers > first signer > signed data > (digests, then) certificates > first certificate
    $q = $at + 4 + 4 + 4
    $q += 4 + [int][BitConverter]::ToUInt32($b, $q)
    $q += 4
    $certLen = [int][BitConverter]::ToUInt32($b, $q)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return -join ($sha.ComputeHash($b, $q + 4, $certLen) | ForEach-Object { $_.ToString("x2") }) } finally { $sha.Dispose() }
}

# versionCode and versionName from the APK's binary AndroidManifest.xml (the <manifest> element's attributes).
function Get-ApkVersion([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.GetEntry("AndroidManifest.xml")
        if (-not $entry) { throw "$Path has no AndroidManifest.xml." }
        $ms = New-Object IO.MemoryStream
        $s = $entry.Open()
        try { $s.CopyTo($ms) } finally { $s.Dispose() }
        $b = $ms.ToArray()
    } finally { $zip.Dispose() }
    $strings = @(); $ids = @(); $code = $null; $name = $null
    $p = 8
    while ($p -lt $b.Length) {
        $type = [BitConverter]::ToUInt16($b, $p)
        $size = [int][BitConverter]::ToUInt32($b, $p + 4)
        if ($type -eq 0x0001) {
            $count = [int][BitConverter]::ToUInt32($b, $p + 8)
            $utf8 = ([BitConverter]::ToUInt32($b, $p + 16) -band 0x100) -ne 0
            $data = $p + [int][BitConverter]::ToUInt32($b, $p + 20)
            $hs = [int][BitConverter]::ToUInt16($b, $p + 2)
            $strings = for ($i = 0; $i -lt $count; $i++) {
                $o = $data + [int][BitConverter]::ToUInt32($b, $p + $hs + 4 * $i)
                if ($utf8) {
                    if ($b[$o] -band 0x80) { $o += 2 } else { $o += 1 }
                    $n = $b[$o]; $o += 1
                    if ($n -band 0x80) { $n = (($n -band 0x7f) -shl 8) -bor $b[$o]; $o += 1 }
                    [Text.Encoding]::UTF8.GetString($b, $o, $n)
                } else {
                    $n = [int][BitConverter]::ToUInt16($b, $o); $o += 2
                    if ($n -band 0x8000) { $n = (($n -band 0x7fff) -shl 16) -bor [BitConverter]::ToUInt16($b, $o); $o += 2 }
                    [Text.Encoding]::Unicode.GetString($b, $o, 2 * $n)
                }
            }
        } elseif ($type -eq 0x0180) {
            $ids = for ($i = $p + 8; $i -lt $p + $size; $i += 4) { [BitConverter]::ToUInt32($b, $i) }
        } elseif ($type -eq 0x0102) {
            $ext = $p + 16
            $attrStart = [int][BitConverter]::ToUInt16($b, $ext + 8)
            $attrSize = [int][BitConverter]::ToUInt16($b, $ext + 10)
            $attrCount = [int][BitConverter]::ToUInt16($b, $ext + 12)
            for ($i = 0; $i -lt $attrCount; $i++) {
                $a = $ext + $attrStart + $i * $attrSize
                $n = [int][BitConverter]::ToUInt32($b, $a + 4)
                $raw = [BitConverter]::ToUInt32($b, $a + 8)
                $kind = $b[$a + 15]
                $val = [BitConverter]::ToUInt32($b, $a + 16)
                $res = if ($n -lt $ids.Count) { $ids[$n] } else { 0 }
                $attr = if ($n -lt $strings.Count) { $strings[$n] } else { "" }
                if ($res -eq 0x0101021b -or $attr -eq "versionCode") { $code = [long]$val }
                if ($res -eq 0x0101021c -or $attr -eq "versionName") {
                    $idx = if ($raw -ne [uint32]::MaxValue) { $raw } elseif ($kind -eq 3) { $val } else { $null }
                    if ($null -ne $idx) { $name = $strings[[int]$idx] }
                }
            }
            break
        }
        if ($size -le 0) { break }
        $p += $size
    }
    return [pscustomobject]@{ Code = $code; Name = $name }
}

# X.Y.Z against X.Y.Z: -1, 0 or 1.
function Compare-Version([string]$A, [string]$B) {
    $x = $A.Split("."); $y = $B.Split(".")
    for ($i = 0; $i -lt 3; $i++) {
        $d = [int]$x[$i] - [int]$y[$i]
        if ($d -ne 0) { return [Math]::Sign($d) }
    }
    return 0
}

Write-Host "AwanTV-release v$ScriptVersion"

foreach ($f in @($App, $Installer)) {
    if (-not (Test-Path $f)) { throw "Not found: $f  (Build > Generate Signed App Bundle or APK > APK > release)" }
}
$version = [regex]::Match((Get-Content (Join-Path $Root "gradle.properties") -Raw), "(?m)^awanVersion=(\S+)").Groups[1].Value
# A test build (1.1.0a, 1.1.0b) is never released: the release is the plain X.Y.Z.
if ($version -notmatch "^\d+\.\d+\.\d+$") {
    throw "awanVersion is $version, a test build. Set it to the release's X.Y.Z in gradle.properties, then build the app and the installer again."
}

# Update now's four lasting conditions (see the top).
foreach ($f in @($App, $Installer)) {
    $cert = Get-ApkCert $f
    if ($cert -ne $ReleaseCert) {
        throw "$(Split-Path -Leaf $f) is signed with another key ($cert), not the release key: TVs would refuse it as an update. Build it again with Generate Signed App Bundle or APK and the release key."
    }
}
Write-Host "Release key: OK (both APKs)"
$v = Get-ApkVersion $App
$parts = $version.Split(".")
$wantCode = [long]$parts[0] * 1000000 + [long]$parts[1] * 1000 + [long]$parts[2]
if ($v.Name -ne $version -or $v.Code -ne $wantCode) {
    throw "app\release\AwanTV.apk is $($v.Name) (versionCode $($v.Code)), not $version ($wantCode): an older build. Build the app and the installer again."
}
Write-Host "AwanTV.apk: $($v.Name), versionCode $($v.Code): OK"
# The installer has the app's number (it shows it, and its own update compares it with the release's).
$vi = Get-ApkVersion $Installer
if ($vi.Name -ne $v.Name -or $vi.Code -ne $v.Code) {
    throw "installer\release\AwanTVInstaller.apk is $($vi.Name) (versionCode $($vi.Code)), not $($v.Name): remove installerTestVersion from gradle.properties if it is there, then build the installer again."
}
Write-Host "AwanTVInstaller.apk: $($vi.Name): OK"
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $published = ((Invoke-RestMethod -Uri $Latest -Headers @{ "User-Agent" = "AwanTV-release" } -TimeoutSec 15).tag_name) -replace "^[vV]", ""
} catch {
    $published = $null
    Write-Host "Could not ask GitHub for the published version ($($_.Exception.Message)). Make sure $version is newer than it." -ForegroundColor Yellow
}
if ($published) {
    $cmp = Compare-Version $version $published
    if ($cmp -lt 0) { throw "$version is older than the published ${published}: TVs refuse an older version. Raise awanVersion." }
    if ($cmp -eq 0) { Write-Host "$version is already the published version (a second run for the same release?)." -ForegroundColor Yellow }
    else { Write-Host "Published now: ${published}; this one is newer: OK" }
}

# The spoken reminders (tools\AwanTV-fetch-announce.ps1) are for one's own build only: a release never carries them.
$zip = [IO.Compression.ZipFile]::OpenRead($App)
try {
    $voiced = @($zip.Entries | Where-Object { $_.FullName -like "assets/announce/*" }).Count
    $lists = @("reciters-ar.json", "reciters-en.json", "suwar-ar.json", "suwar-en.json") |
        Where-Object { -not $zip.GetEntry("assets/mp3quran/$_") }
} finally { $zip.Dispose() }
if ($voiced -gt 0) {
    throw "AwanTV.apk carries $voiced spoken reminder(s), a Microsoft voice not ours to distribute. Delete app\src\main\assets\announce\*.mp3, then build the app and the installer again."
}
# The release build reads mp3quran.net's lists into app\src\main\assets\mp3quran (app\build.gradle); without internet
# it keeps the copy there, so only a first build offline lacks them.
if ($lists.Count -gt 0) {
    throw "AwanTV.apk lacks mp3quran.net's list(s): $($lists -join ', '). Build the app again with internet (the build reads them), then the installer."
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
Write-Host "Upload the three files with these names unchanged, on the tag v$version (Update now downloads"
Write-Host "  releases/download/v$version/AwanTV.apk on every TV, and the installer its own AwanTVInstaller.apk)."
Write-Host "SHA-256:"
Get-ChildItem $Out | Sort-Object Name | ForEach-Object {
    Write-Host ("  {0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name)
}
