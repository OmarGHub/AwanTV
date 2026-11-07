# AwanTV-textdump.ps1 - Version 1.4.0
# The languages plan's safety net (claude/awan-languages-plan.md, phase 0). A test build of Awan TV (a version ending
# in a letter) writes every text it shows, in Arabic and English with both kinds of digits (TextDump.java); this tool
# asks the TV for that file, keeps it in state\textdump\<version>.txt, and compares it with a golden one.
#
# The golden file comes from a test build with nothing but the dump in it (the app as it was). Every later test build
# must give each golden line unchanged: a changed or missing line stops the release; a new line is only listed, to
# be looked at. The "file|" lines are this TV's own settings file, in its language and with its values: they change
# with the TV's settings, so (1.1.0) they are listed apart and never stop the release (their texts are the editor's
# descriptions, the "schema|" lines, which do).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\AwanTV-textdump.ps1 [-Device <serial>] [-Golden <file>]
#       takes a dump from the TV (and compares it with -Golden when given)
#   powershell -ExecutionPolicy Bypass -File tools\AwanTV-textdump.ps1 -Golden <file> -File <file>
#       compares two dumps already on the computer
# The full comparison is written beside the new dump as <new>-vs-<golden>.txt (UTF-8): numbered, golden then new.
#   powershell -ExecutionPolicy Bypass -File tools\AwanTV-textdump.ps1 [-Device <serial>] -Crops [-Clear]
#       (1.2.0) copies the TV's list of texts that do not fit their place (CropCheck.java, test builds: each screen
#       looks every two seconds while it is in front) to state\textdump\crops-<version>.txt; -Clear then empties
#       it on the TV, to start a new round of screens
# The "schema|" and "file|" lines are numbered by their place in the file (the dump's format is frozen), so one line
# added near the top would move every one after it: from 1.3.0 they are matched by their text first (the lines both
# have alike, then the changed ones in the order they come), and only a line really added, gone or changed is listed.
# Exit code: 0 the same, 1 a golden line changed or is missing, 2 the dump failed.
# 1.4.0: a TV that stops answering adb (asleep, "error: closed") is said so in words, not as a PowerShell error; a
# -Golden file that is not there is named with the dumps saved; a dump taken without -Golden names the newest one
# saved before it to compare with.

param([string]$Device = "", [string]$Golden = "", [string]$File = "", [switch]$Crops, [switch]$Clear)

$ErrorActionPreference = "Continue"  # native adb output on stderr must not abort; failures throw explicitly
$ScriptVersion = "1.4.0"
$Pkg = "app.awanpro.tv"
$Receiver = "$Pkg/app.awanpro.tv.SettingsTransfer"
$Remote = "/sdcard/Android/data/$Pkg/files/text-dump.txt"
$RemoteCrops = "/sdcard/Android/data/$Pkg/files/crop.txt"
$Root = Split-Path -Parent $PSScriptRoot
$Dir = Join-Path (Join-Path $Root "state") "textdump"
$Sep = "`t=>`t"

Write-Host "AwanTV-textdump v$ScriptVersion"

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", "C:\adb\adb.exe", (Join-Path $Root "platform-tools\adb.exe"))) {
        if (Test-Path $c) { return $c }
    }
    throw "adb.exe not found. Install Android Studio (it brings platform-tools) or put adb in PATH."
}

# One TV can be listed twice over wireless debugging ("name" and "name (2)"): that is still one device.
function Select-Device([string]$adb, [string]$wanted) {
    $serials = @(& $adb devices | Select-String -Pattern "^(\S.*?)\s+device$" | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($serials.Count -eq 0) { throw "No device connected. Pair and connect first (Developer options > Wireless debugging)." }
    $match = $serials
    if ($wanted) {
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

# The adb server keeps open the folder it was first started from: started from the temp folder, it never holds the project.
function Start-AdbServer([string]$adb) {
    Push-Location $env:TEMP
    try { & $adb start-server 2>&1 | Out-Null } finally { Pop-Location }
}

# key (everything before the tab-arrow-tab) -> the rest. Lines are unique by key (TextDump numbers repeats).
function Read-Dump([string]$path) {
    if (-not (Test-Path $path)) { throw "No such file: $path" }
    $map = New-Object 'System.Collections.Generic.Dictionary[string,string]'
    foreach ($line in [IO.File]::ReadAllLines($path, [Text.Encoding]::UTF8)) {
        if ($line.Length -eq 0) { continue }
        $i = $line.IndexOf($Sep)
        if ($i -lt 0) { $key = $line; $value = "" } else { $key = $line.Substring(0, $i); $value = $line.Substring($i + $Sep.Length) }
        $map[$key] = $value
    }
    return ,$map
}

# For each b line, the a line it stands for (-1: added): the same start and end, then the longest run of lines
# alike between them; the lines left over between two alike ones are paired in order (changed), the rest added.
function Align([string[]]$a, [string[]]$b) {
    $na = $a.Count; $nb = $b.Count
    $map = New-Object int[] $nb
    for ($j = 0; $j -lt $nb; $j++) { $map[$j] = -1 }
    $pre = 0
    while ($pre -lt $na -and $pre -lt $nb -and $a[$pre] -ceq $b[$pre]) { $map[$pre] = $pre; $pre++ }
    $suf = 0
    while ($suf -lt ($na - $pre) -and $suf -lt ($nb - $pre) -and $a[$na - 1 - $suf] -ceq $b[$nb - 1 - $suf]) {
        $map[$nb - 1 - $suf] = $na - 1 - $suf; $suf++
    }
    $m = $na - $pre - $suf; $k = $nb - $pre - $suf
    if ($m -le 0 -or $k -le 0) { return ,$map }
    if ([long]$m * $k -gt 4000000) {
        for ($j = 0; $j -lt [Math]::Min($m, $k); $j++) { $map[$pre + $j] = $pre + $j }
        return ,$map
    }
    $L = New-Object 'int[,]' ($m + 1), ($k + 1)
    for ($i = $m - 1; $i -ge 0; $i--) {
        for ($j = $k - 1; $j -ge 0; $j--) {
            if ($a[$pre + $i] -ceq $b[$pre + $j]) { $L[$i, $j] = $L[($i + 1), ($j + 1)] + 1 }
            elseif ($L[($i + 1), $j] -ge $L[$i, ($j + 1)]) { $L[$i, $j] = $L[($i + 1), $j] }
            else { $L[$i, $j] = $L[$i, ($j + 1)] }
        }
    }
    $i = 0; $j = 0
    $gone = New-Object System.Collections.Generic.List[int]
    $new = New-Object System.Collections.Generic.List[int]
    while ($i -lt $m -or $j -lt $k) {
        if ($i -lt $m -and $j -lt $k -and $a[$pre + $i] -ceq $b[$pre + $j]) {
            for ($p = 0; $p -lt [Math]::Min($gone.Count, $new.Count); $p++) { $map[$new[$p]] = $gone[$p] }
            $gone.Clear(); $new.Clear()
            $map[$pre + $j] = $pre + $i; $i++; $j++
        } elseif ($j -lt $k -and ($i -ge $m -or $L[$i, ($j + 1)] -ge $L[($i + 1), $j])) {
            $new.Add($pre + $j); $j++
        } else {
            $gone.Add($pre + $i); $i++
        }
    }
    for ($p = 0; $p -lt [Math]::Min($gone.Count, $new.Count); $p++) { $map[$new[$p]] = $gone[$p] }
    return ,$map
}

# The new dump's numbered lines (schema|ar|, schema|en|, file|) keyed as the golden line each stands for (Align); an
# added one as <group>|+<its number>, so it is New and every line after it still meets its own.
function Realign($g, $n) {
    $groups = @{}
    foreach ($k in @($g.Keys) + @($n.Keys)) {
        if ($k -match '^(schema\|[^|]+|file)\|\d+$') { $groups[$Matches[1]] = $true }
    }
    foreach ($grp in $groups.Keys) {
        $gk = @($g.Keys | Where-Object { $_.StartsWith($grp + "|") -and $_ -match '\|\d+$' } | Sort-Object)
        $nk = @($n.Keys | Where-Object { $_.StartsWith($grp + "|") -and $_ -match '\|\d+$' } | Sort-Object)
        $map = Align ([string[]]@($gk | ForEach-Object { $g[$_] })) ([string[]]@($nk | ForEach-Object { $n[$_] }))
        $vals = @($nk | ForEach-Object { $n[$_] })
        foreach ($k in $nk) { [void]$n.Remove($k) }
        for ($j = 0; $j -lt $nk.Count; $j++) {
            $key = if ($map[$j] -ge 0) { $gk[$map[$j]] } else { $grp + "|+" + $nk[$j].Substring($grp.Length + 1) }
            $n[$key] = $vals[$j]
        }
    }
}

function Compare-Dumps([string]$goldenPath, [string]$newPath) {
    $g = Read-Dump $goldenPath
    $n = Read-Dump $newPath
    Realign $g $n
    $changed = New-Object System.Collections.Generic.List[string]
    $missing = New-Object System.Collections.Generic.List[string]
    $added = New-Object System.Collections.Generic.List[string]
    $fileDiff = New-Object System.Collections.Generic.List[string]
    foreach ($k in $g.Keys) {
        $isFile = $k.StartsWith("file|")
        if (-not $n.ContainsKey($k)) { if ($isFile) { $fileDiff.Add($k) } else { $missing.Add($k) } }
        elseif ($n[$k] -cne $g[$k]) { if ($isFile) { $fileDiff.Add($k) } else { $changed.Add($k) } }
    }
    foreach ($k in $n.Keys) { if (-not $g.ContainsKey($k)) { if ($k.StartsWith("file|")) { $fileDiff.Add($k) } else { $added.Add($k) } } }
    $fileDiff.Sort([StringComparer]::Ordinal)
    $changed.Sort([StringComparer]::Ordinal)
    $missing.Sort([StringComparer]::Ordinal)
    $added.Sort([StringComparer]::Ordinal)

    $report = [IO.Path]::Combine([IO.Path]::GetDirectoryName($newPath),
        [IO.Path]::GetFileNameWithoutExtension($newPath) + "-vs-" + [IO.Path]::GetFileNameWithoutExtension($goldenPath) + ".txt")
    $b = New-Object Text.StringBuilder
    [void]$b.AppendLine("AwanTV-textdump v$ScriptVersion")
    [void]$b.AppendLine("Golden: $goldenPath ($($g.Count) lines)")
    [void]$b.AppendLine("New:    $newPath ($($n.Count) lines)")
    [void]$b.AppendLine("Changed: $($changed.Count)   Missing: $($missing.Count)   New: $($added.Count)")
    $i = 0
    [void]$b.AppendLine("")
    [void]$b.AppendLine("=== Changed (stop the release) ===")
    foreach ($k in $changed) {
        $i++
        [void]$b.AppendLine("$i. $k")
        [void]$b.AppendLine("   golden: $($g[$k])")
        [void]$b.AppendLine("   new:    $($n[$k])")
    }
    [void]$b.AppendLine("")
    [void]$b.AppendLine("=== Missing (stop the release) ===")
    foreach ($k in $missing) { $i++; [void]$b.AppendLine("$i. $k"); [void]$b.AppendLine("   golden: $($g[$k])") }
    [void]$b.AppendLine("")
    [void]$b.AppendLine("=== New (look at them) ===")
    foreach ($k in $added) { $i++; [void]$b.AppendLine("$i. $k"); [void]$b.AppendLine("   new:    $($n[$k])") }
    [void]$b.AppendLine("")
    [void]$b.AppendLine("=== This TV's settings file (follows its settings and language; does not stop the release) ===")
    foreach ($k in $fileDiff) {
        $i++
        [void]$b.AppendLine("$i. $k")
        if ($g.ContainsKey($k)) { [void]$b.AppendLine("   golden: $($g[$k])") }
        if ($n.ContainsKey($k)) { [void]$b.AppendLine("   new:    $($n[$k])") }
    }
    [IO.File]::WriteAllText($report, $b.ToString(), (New-Object Text.UTF8Encoding($true)))

    # The terminal gets numbers only (it breaks Arabic); the texts are in the report.
    Write-Host ("Golden lines: {0}   new dump: {1}" -f $g.Count, $n.Count)
    Write-Host ("Changed: {0}   Missing: {1}   New: {2}" -f $changed.Count, $missing.Count, $added.Count)
    if ($fileDiff.Count -gt 0) {
        Write-Host ("Settings file lines that differ: {0} (this TV's own settings or language changed; listed in the report, not counted)" -f $fileDiff.Count)
    }
    Write-Host "Report: $report"
    if ($changed.Count + $missing.Count -gt 0) {
        Write-Host "DIFFERENT: a golden line changed or is missing. The release stops here."
        return 1
    }
    Write-Host "SAME: every golden line is unchanged."
    return 0
}

# A -Golden path that exists, or a failure that names it and the dumps saved (1.4.0).
function Golden-Path([string]$path) {
    if (Test-Path $path) { return (Resolve-Path $path).Path }
    $saved = @(Get-ChildItem -Path $Dir -Filter "*.txt" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "crops-*" -and $_.Name -notlike "*-vs-*" } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 3 | ForEach-Object { $_.Name })
    $list = if ($saved.Count) { " Saved in state\textdump: " + ($saved -join ", ") } else { "" }
    throw "Golden file not found: $path.$list"
}

try {
    if ($File) {
        if (-not $Golden) { throw "-File compares with -Golden: give both." }
        exit (Compare-Dumps (Golden-Path $Golden) (Resolve-Path $File).Path)
    }

    $AdbExe = Find-Adb
    Start-AdbServer $AdbExe
    $Serial = Select-Device $AdbExe $Device
    function Adb { & $AdbExe -s $Serial @args }

    # The TV may have stopped answering since adb listed it (asleep, the connection closed: 1.4.0).
    $info = @(Adb shell "dumpsys package $Pkg" 2>&1 | ForEach-Object { "$_" })
    $said = ($info | Where-Object { $_ -match '^(error|adb: )' } | Select-Object -First 1)
    if ($said) { throw "The TV stopped answering adb ($said). Wake it, check 'adb devices', reconnect (adb connect) and run again." }
    $found = $info | Select-String -Pattern "versionName=(\S+)" | Select-Object -First 1
    $version = if ($found) { $found.Matches[0].Groups[1].Value } else { "" }
    if (-not $version) { throw "Awan TV is not installed on this TV." }
    if ($version -notmatch '\d[a-z]+$') { throw "Awan TV $version is a release: the dump is in test builds only (a version ending in a letter)." }

    if ($Crops) {
        New-Item -ItemType Directory -Force -Path $Dir | Out-Null
        $dest = Join-Path $Dir "crops-$version.txt"
        Remove-Item -Force $dest -ErrorAction SilentlyContinue
        Adb pull $RemoteCrops $dest 2>&1 | Out-Null
        if (-not (Test-Path $dest)) {
            # The TV writes the list only when it finds a cut text (1.3.1: the words said nothing was looked at).
            Write-Host "No text cut on the screens shown since the list was last cleared (the TV keeps no list when all fit)."
            exit 0
        }
        # Once each (the TV writes a finding again after Awan TV restarts), sorted by screen and language.
        $unique = @([IO.File]::ReadAllLines($dest, [Text.Encoding]::UTF8) | Where-Object { $_ } | Sort-Object -Unique)
        [IO.File]::WriteAllLines($dest, [string[]]$unique, (New-Object Text.UTF8Encoding($true)))
        Write-Host "Texts that do not fit: $($unique.Count) (the texts are in the file; the terminal breaks Arabic)"
        Write-Host "Saved: $dest"
        if ($Clear) {
            Adb shell rm -f $RemoteCrops | Out-Null
            Write-Host "Cleared on the TV."
        }
        exit 0
    }

    Write-Host "Awan TV ${version}: writing the dump on the TV (a few seconds; leave the remote alone)..."
    Adb shell rm -f $Remote | Out-Null
    # 0x20: FLAG_INCLUDE_STOPPED_PACKAGES, so it arrives even if the app was never opened since a reboot.
    $out = (Adb shell "am broadcast -f 0x20 -n $Receiver -a $Pkg.TEXT_DUMP" | Out-String).Trim()
    if ($out -notmatch 'result=1, data="([^"]*)"') { throw "The TV did not write the dump. adb said: $out" }
    Write-Host "TV: $($Matches[1])"

    New-Item -ItemType Directory -Force -Path $Dir | Out-Null
    $dest = Join-Path $Dir "$version.txt"
    $k = 2
    while (Test-Path $dest) { $dest = Join-Path $Dir "$version-$k.txt"; $k++ }
    Adb pull $Remote $dest 2>&1 | Out-Null
    if (-not (Test-Path $dest) -or (Get-Item $dest).Length -eq 0) { throw "The dump could not be copied from the TV." }
    $lines = @([IO.File]::ReadAllLines($dest, [Text.Encoding]::UTF8)).Count
    Write-Host "Saved: $dest ($lines lines)"

    if ($Golden) { exit (Compare-Dumps (Golden-Path $Golden) $dest) }
    $before = Get-ChildItem -Path $Dir -Filter "*.txt" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -ne $dest -and $_.Name -notlike "crops-*" -and $_.Name -notlike "*-vs-*" } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($before) { Write-Host "Not compared. To compare with the newest dump before it: -Golden state\textdump\$($before.Name)" }
    exit 0
} catch {
    Write-Host "Failed: $($_.Exception.Message)"
    exit 2
}
