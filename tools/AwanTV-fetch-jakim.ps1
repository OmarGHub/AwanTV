# AwanTV-fetch-jakim.ps1 - Version 1.0.0
# Downloads JAKIM's prayer timetables (e-Solat, www.e-solat.gov.my) for every Malaysian zone, a whole year each,
# into state\jakim\<year>\<zone>.json, exactly as the site answers. They are for building and checking Awan TV's
# JAKIM method on this computer (the zone times are worked out from them); the app never carries them, and
# state\ is not part of the repository.
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-fetch-jakim.ps1 [-Years 2024,2025,2026]
# Run it again to resume: a zone and year already saved is skipped. At the end it packs everything into
# state\jakim\AwanTV-jakim-tables.zip.

param([int[]]$Years = @(2024, 2025, 2026))

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$ScriptVersion = "1.0.0"
Write-Host "AwanTV-fetch-jakim v$ScriptVersion"

$Root = Split-Path -Parent $PSScriptRoot
$Dest = Join-Path $Root "state\jakim"
# The zones e-Solat publishes a timetable for (its own codes; SWK09 is a single village, asked for in case).
$Zones = @(
    "JHR01", "JHR02", "JHR03", "JHR04",
    "KDH01", "KDH02", "KDH03", "KDH04", "KDH05", "KDH06", "KDH07",
    "KTN01", "KTN02",
    "MLK01",
    "NGS01", "NGS02", "NGS03",
    "PHG01", "PHG02", "PHG03", "PHG04", "PHG05", "PHG06", "PHG07",
    "PLS01",
    "PNG01",
    "PRK01", "PRK02", "PRK03", "PRK04", "PRK05", "PRK06", "PRK07",
    "SBH01", "SBH02", "SBH03", "SBH04", "SBH05", "SBH06", "SBH07", "SBH08", "SBH09",
    "SGR01", "SGR02", "SGR03",
    "SWK01", "SWK02", "SWK03", "SWK04", "SWK05", "SWK06", "SWK07", "SWK08", "SWK09",
    "TRG01", "TRG02", "TRG03", "TRG04",
    "WLY01", "WLY02"
)
$Api = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=duration&zone="
$Utf8 = New-Object System.Text.UTF8Encoding($false)

# Windows PowerShell 5.1 may not offer TLS 1.2 by default.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$total = $Zones.Count * $Years.Count
$n = 0
$failed = @()
foreach ($year in $Years) {
    $dir = Join-Path $Dest $year
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    foreach ($zone in $Zones) {
        $n++
        $file = Join-Path $dir "$zone.json"
        $label = "[{0,3}/{1}] {2} {3}" -f $n, $total, $zone, $year
        if (Test-Path $file) {
            Write-Host "$label already here"
            continue
        }
        $body = "datestart=$year-01-01&dateend=$year-12-31"
        $answer = $null
        for ($try = 1; $try -le 3 -and -not $answer; $try++) {
            try {
                $r = Invoke-WebRequest -Uri ($Api + $zone) -Method Post -Body $body `
                    -ContentType "application/x-www-form-urlencoded" -UseBasicParsing -TimeoutSec 90
                $json = $r.Content | ConvertFrom-Json
                if ($json.prayerTime -is [array]) { $answer = $r.Content }
                elseif ($json.status -eq "NO_RECORD!") { $answer = "none" }
                else { throw "no days in the answer" }
            } catch {
                if ($try -eq 3) { Write-Host "$label failed: $($_.Exception.Message)" }
                else { Start-Sleep -Seconds 5 }
            }
        }
        if (-not $answer) { $failed += "$zone $year"; continue }
        if ($answer -eq "none") { Write-Host "$label none (no timetable for this zone)"; continue }
        # Into a .part file first, so a broken save is never taken for a finished one on the next run.
        [IO.File]::WriteAllText("$file.part", $answer, $Utf8)
        Move-Item -Force -Path "$file.part" -Destination $file
        Write-Host ("$label {0} days" -f $json.prayerTime.Count)
        Start-Sleep -Milliseconds 500
    }
}

if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host ("Not saved ({0}): {1}" -f $failed.Count, ($failed -join ", "))
    Write-Host "Run the tool again later; it continues where it stopped."
    exit 1
}

$zip = Join-Path $Dest "AwanTV-jakim-tables.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
$parts = $Years | ForEach-Object { Join-Path $Dest $_ }
Compress-Archive -Path $parts -DestinationPath $zip
Write-Host ""
Write-Host "Done. Send this file: $zip"
