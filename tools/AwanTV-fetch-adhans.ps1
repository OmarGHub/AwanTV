# AwanTV-fetch-adhans.ps1 - Version 1.0.0
# Downloads the adhan recordings into app\src\main\assets\adhan\ before you build.
# They come from https://github.com/abodehq/Athan-MP3, which states no licence, so they are not part of the
# source (as Afnon does). The release APK carries them, credited to that collection; whoever holds the rights to
# a recording and wants it removed can open an issue. Without them Awan TV still works; the call screen is silent.
# Usage: powershell -ExecutionPolicy Bypass -File tools\AwanTV-fetch-adhans.ps1

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$Root = Split-Path -Parent $PSScriptRoot
$Dest = Join-Path $Root "app\src\main\assets\adhan"
$Base = "https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds"
$List = @(
    @("alafasy", "Athan Mishary Alafasi.mp3"),
    @("almalki", "Athan Hamdan Almalki.mp3"),
    @("alarkani", "Athan Ibrahim Al-Arkani.mp3"),
    @("chebae_fajr", "Athan Al-fajer - Malek chebae.mp3"),
    @("alhamathani", "Athan Majed Al-hamathani.mp3"),
    @("alzahrani", "Athan Mansoor Az-Zahrani.mp3"),
    @("deghreri", "Athan Hamad Deghreri.mp3"),
    @("alqatami", "Athan Nasser Alqatami.mp3"),
    @("khatba", "Athan Suhaib Khatba.mp3"),
    @("menshawy", "Athan Mohammad Almenshawy.mp3"),
    @("refaat", "Athan Mohammad Ref3at.mp3"),
    @("abdulbasit", "Athan Abed Albase6.mp3"),
    @("nuyne", "Athan Ahmad Nuyne3.mp3"),
    @("makkah", "Athan Makkah.mp3")
)

$ScriptVersion = "1.0.0"
Write-Host "AwanTV-fetch-adhans v$ScriptVersion"
# Windows PowerShell 5.1 may not offer TLS 1.2 by default; GitHub requires it.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
New-Item -ItemType Directory -Force -Path $Dest | Out-Null
$n = 0
foreach ($item in $List) {
    $n++
    $file = Join-Path $Dest ($item[0] + ".mp3")
    if (Test-Path $file) {
        Write-Host ("[{0,2}/{1}] {2} already here" -f $n, $List.Count, $item[0])
        continue
    }
    $url = $Base + "/" + [Uri]::EscapeDataString($item[1])
    # Into a .part file first, so a broken download is never taken for a finished one on the next run.
    $part = "$file.part"
    Invoke-WebRequest -Uri $url -OutFile $part -UseBasicParsing
    Move-Item -Force -Path $part -Destination $file
    Write-Host ("[{0,2}/{1}] {2} downloaded" -f $n, $List.Count, $item[0])
}
Write-Host "Done. Recordings are in $Dest"
