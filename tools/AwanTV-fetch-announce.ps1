# AwanTV-fetch-announce.ps1 - Version 1.0.0
# Records the 12 spoken lines of tools\announce-lines.txt (the reminder before each prayer, and "its time has
# begun" when a prayer's adhan is off) into app\src\main\assets\announce\<name>.mp3, with a Microsoft neural Arabic
# voice through edge-tts. Run it before building; the internet is needed only while it runs, never by the app.
# The clips are not in the source (no licence to redistribute): without them the reminders still show, silently.
#
# Needs Python with edge-tts:  pip install edge-tts
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-fetch-announce.ps1 [-Force] [-Voice <name>]
#   -Force   record every clip again (after changing the voice or a line)
#   -Voice   default ar-SA-HamedNeural; others: python -m edge_tts --list-voices

param([switch]$Force, [string]$Voice = "ar-SA-HamedNeural")

$ErrorActionPreference = "Continue"  # native stderr must not abort; failures throw explicitly
$ScriptVersion = "1.0.0"
$Root = Split-Path -Parent $PSScriptRoot
$Dest = Join-Path $Root "app\src\main\assets\announce"
$Lines = Join-Path $PSScriptRoot "announce-lines.txt"

Write-Host "AwanTV-fetch-announce v$ScriptVersion  (voice: $Voice)"

$py = $null
foreach ($p in @("python", "py", "python3")) {
    $cmd = Get-Command $p -ErrorAction SilentlyContinue
    if ($cmd) {
        & $cmd.Source -c "import edge_tts" 2>$null
        if ($LASTEXITCODE -eq 0) { $py = $cmd.Source; break }
    }
}
if (-not $py) { throw "Python with edge-tts not found. Install Python, then: pip install edge-tts" }

New-Item -ItemType Directory -Force -Path $Dest | Out-Null
$done = 0
foreach ($line in [System.IO.File]::ReadAllLines($Lines, [System.Text.Encoding]::UTF8)) {
    $parts = $line.Split("|", 2)
    if ($parts.Count -lt 2 -or -not $parts[0].Trim()) { continue }
    $name = $parts[0].Trim()
    $out = Join-Path $Dest "$name.mp3"
    if (-not $Force -and (Test-Path $out) -and (Get-Item $out).Length -gt 0) { continue }
    # The Arabic text goes through a UTF-8 file, never the command line (Windows consoles break Arabic).
    $txt = Join-Path $env:TEMP "awantv-announce-$name.txt"
    [System.IO.File]::WriteAllText($txt, $parts[1].Trim(), (New-Object System.Text.UTF8Encoding($false)))
    & $py -m edge_tts --voice $Voice --file $txt --write-media "$out.part" 2>$null
    Remove-Item $txt -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path "$out.part")) { throw "edge-tts failed on $name (internet connection?)" }
    Move-Item -Force "$out.part" $out
    Write-Host ("  {0}.mp3 ({1} KB)" -f $name, [math]::Round((Get-Item $out).Length / 1KB))
    $done++
}
Write-Host "Recorded $done clip(s). Spoken lines are in $Dest"
