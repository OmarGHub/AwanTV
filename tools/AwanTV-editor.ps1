# AwanTV-editor.ps1 - Version 1.0.0
# Awan TV settings editor for Windows.
# One window: get the settings from a connected TV or open a settings file, change them with lists, switches and
# steppers (nothing typed by hand), then send them to one or more connected TVs, or save them to a file. Every TV's own
# settings are saved as a backup before it receives new ones (state\settings\awantv-backup-<TV serial>-<time>.jsonc).
# The sections and rows follow Settings on the TV, from the description the TV writes beside its export, so a new
# setting shows here without changing this tool. The TV still checks every file it gets.
#
# Run: tools\AwanTV-editor.cmd (double-click), or
#      powershell -STA -ExecutionPolicy Bypass -File tools\AwanTV-editor.ps1
# Only one window: running it again brings the open one to the front.
# Needs adb (Android Studio's platform-tools, or adb in PATH) and Windows PowerShell 5.1 (part of Windows 10 and 11).
# Files: state\settings (settings and backups), state\settings\schema (descriptions by app version),
#        state\editor\tvs.jsonc (every TV this computer has met: the name given to it, its model, device name,
#        last address, version and when it was last seen; edit it in Notepad too), state\editor\editor.log (English).
# TVs not connected stay listed, below the connected ones; Identify shows a TV's name on its own screen for ten
# seconds; names fall back to the TV's own device name, then its model, and alike names get the end of the serial.
# Names stay on this computer: nothing is written to the TVs.
# The window's language is its user's, not the TV's: Windows' display language at first (Arabic, else English), then
# the ع / EN switch at the top, kept in state\editor\editor.json. The settings' names and help follow it too (the TV
# describes them in both languages).

$ErrorActionPreference = "Stop"
$ScriptVersion = "1.0.0"
$Pkg = "app.awanpro.tv"
$Receiver = "$Pkg/app.awanpro.tv.SettingsTransfer"
$Files = "/sdcard/Android/data/$Pkg/files"
$Remote = "$Files/awantv-settings.jsonc"
$Root = Split-Path -Parent $PSScriptRoot
$SettingsDir = Join-Path (Join-Path $Root "state") "settings"
$SchemaDir = Join-Path $SettingsDir "schema"
$EditorDir = Join-Path (Join-Path $Root "state") "editor"
$TvsFile = Join-Path $EditorDir "tvs.jsonc"
$LogFile = Join-Path $EditorDir "editor.log"

# WPF needs a single-threaded apartment: start again with -STA if this one is not.
if ([Threading.Thread]::CurrentThread.ApartmentState -ne "STA") {
    Start-Process powershell.exe -ArgumentList @("-STA", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
        "-File", "`"$PSCommandPath`"") -WindowStyle Hidden
    exit
}

foreach ($d in @($SettingsDir, $SchemaDir, $EditorDir)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }

function Log([string]$text) {
    try { Add-Content -Path $LogFile -Value ("{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $text) -Encoding UTF8 } catch { }
}

# One window: a second start signals the first to come to the front, then leaves.
$created = $false
$script:Mutex = [Threading.Mutex]::new($true, "Local\AwanTV.Editor", [ref]$created)
$script:ShowSignal = [Threading.EventWaitHandle]::new($false, [Threading.EventResetMode]::AutoReset, "Local\AwanTV.Editor.Show")
if (-not $created) {
    [void]$script:ShowSignal.Set()
    exit
}

Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase, System.Xaml
Log "AwanTV-editor v$ScriptVersion started"

# No console to show an error in: any error that ends the script is logged and shown in a message box.
trap {
    Log "Fatal: $($_.Exception.Message) at line $($_.InvocationInfo.ScriptLineNumber)"
    [void][Windows.MessageBox]::Show("$($_.Exception.Message)`n`n(state\editor\editor.log)", "Awan TV · AwanTV-editor v$ScriptVersion", "OK", "Error")
    try { $script:Mutex.ReleaseMutex() } catch { }
    exit 1
}

# ---------------------------------------------------------------- words (the window's own; the settings' come from the TV)

$Words = @{
    ar = @{
        title = "Awan TV · محرر الإعدادات"; source = "المصدر"; get = "جلب من الشاشة"; open = "فتح ملف…"; refresh = "تحديث قائمة الشاشات"
        shown = "المعروض: {0}"; nothing = "اجلب الإعدادات من شاشة متصلة، أو افتح ملف إعدادات."
        sendTo = "إرسال إلى"; send = "إرسال"; save = "حفظ في ملف…"; restore = "استعادة نسخة…"
        targets = "{0} مختارة من {1} متصلة"; noTargets = "لا شاشة مختارة"; search = "بحث بالاسم أو الرقم"
        all = "تحديد الكل"; none = "لا شيء"; rename = "تسمية"; renameAsk = "اسم هذه الشاشة (يُحفظ في هذا الحاسب):"; renameHint = "اترك الحقل فارغًا واضغط «حفظ» لمسح الاسم."; saveName = "حفظ"; cancel = "إلغاء"
        offline = "غير متصلة"; older = "أقدم من المصدر: ما لا تعرفه يُتجاهل"
        changes = "● تعديلات لم تُرسل: {0}"; noChanges = "لا تعديلات غير مرسلة"; readonly = "تُغيَّر من التلفاز"
        noTv = "لا شاشة متصلة. صِل الشاشة بـ adb (التصحيح اللاسلكي) ثم اضغط تحديث."
        noAdb = "لم يُعثر على adb. ثبّت Android Studio أو ضع adb في PATH."
        noSchema = "لا يوجد وصف للإعدادات لهذه النسخة. اجلب الإعدادات من شاشة عليها Awan TV مرة واحدة."
        sent = "نتيجة الإرسال"; unsaved = "هناك تعديلات لم تُرسل ولم تُحفظ. الخروج دون حفظ؟"
        add = "إضافة تطبيق…"; up = "▲"; down = "▼"; remove = "حذف"; noneTime = "—"; working = "جارٍ العمل…"
        backups = "النسخ الاحتياطية (state\settings)"; skipped = "إعدادات لا تعرفها هذه النسخة فلم تُرسَل: {0}"
        identify = "تعريف"; identifyTip = "يظهر اسمها على الشاشة نفسها 10 ثوانٍ"; offlineGroup = "غير متصلة"
        okBtn = "حسنًا"; leaveBtn = "خروج دون حفظ"; backBtn = "رجوع"; removeTvBtn = "إزالة"
        changed = "تم تغيير {0}"; nothingToChange = "لا شيء يتغيّر: الشاشة عليها هذه الإعدادات نفسها"; shownOnTv = "ظهر على الشاشة"
        lastSeen = "آخر اتصال: {0}"; connect = "اتصال"; removeTv = "إزالة من القائمة"; removeAsk = "إزالة «{0}» من قائمة الشاشات؟ (تعود إن اتصلت من جديد)"
        connectFail = "تعذّر الاتصال بـ {0}. إن تغيّر عنوانها (التصحيح اللاسلكي يغيّر المنفذ)، صِلها بـ adb ثم اضغط تحديث."
        targetsNote = "◉ تعريف: يظهر اسمها على الشاشة نفسها 10 ثوانٍ · ✎ تسمية (تُحفظ في الحاسب)`nالاسم: تسميتك هنا، وإلا اسم الجهاز في إعدادات التلفاز، وإلا الطراز"
        tvsHeader = "شاشات Awan TV التي عرفها هذا الحاسب. ""name"" يُعدَّل هنا أو من المحرر (✎)؛ فارغ = اسم الجهاز في التلفاز أو طرازه. البقية يكتبها المحرر."
    }
    en = @{
        title = "Awan TV · Settings editor"; source = "Source"; get = "Get from TV"; open = "Open file…"; refresh = "Refresh the TV list"
        shown = "Shown: {0}"; nothing = "Get the settings from a connected TV, or open a settings file."
        sendTo = "Send to"; send = "Send"; save = "Save to file…"; restore = "Restore a copy…"
        targets = "{0} of {1} connected TVs"; noTargets = "No TV chosen"; search = "Search by name or number"
        all = "All"; none = "None"; rename = "Name"; renameAsk = "This TV's name (kept on this computer):"; renameHint = "Leave it empty and Save to clear the name."; saveName = "Save"; cancel = "Cancel"
        offline = "not connected"; older = "older than the source: what it does not know is skipped"
        changes = "● {0} change(s) not sent"; noChanges = "No unsent changes"; readonly = "changed on the TV"
        noTv = "No TV connected. Connect it with adb (Wireless debugging), then Refresh."
        noAdb = "adb not found. Install Android Studio or put adb in PATH."
        noSchema = "No description of the settings for this version. Get the settings once from a TV with Awan TV."
        sent = "Sent"; unsaved = "Some changes were neither sent nor saved. Leave without them?"
        add = "Add an app…"; up = "▲"; down = "▼"; remove = "Remove"; noneTime = "—"; working = "Working…"
        backups = "Backups (state\settings)"; skipped = "Skipped {0} setting(s) this version does not know"
        identify = "Identify"; identifyTip = "Shows its name on its own screen for 10 seconds"; offlineGroup = "Not connected"
        okBtn = "OK"; leaveBtn = "Leave without saving"; backBtn = "Back"; removeTvBtn = "Remove"
        changed = "Changed {0}"; nothingToChange = "Nothing to change: the TV already has these settings"; shownOnTv = "Shown on the TV"
        lastSeen = "Last seen: {0}"; connect = "Connect"; removeTv = "Remove from the list"; removeAsk = "Remove {0} from the list? (It comes back when it connects again)"
        connectFail = "Could not connect to {0}. If its address changed (Wireless debugging changes the port), connect it with adb, then Refresh."
        targetsNote = "◉ Identify: its name on its own screen for 10 seconds · ✎ name it (kept on this computer)`nName: yours here, else the TV's device name, else its model"
        tvsHeader = "Awan TV's TVs this computer has met. Edit ""name"" here or in the editor (✎); empty = the TV's device name or model. The editor writes the rest."
    }
}
$EditorConfig = Join-Path $EditorDir "editor.json"
# The window's language: the one chosen here before (ع / EN), else Windows' display language (Arabic, else English).
$script:Lang = if ([Globalization.CultureInfo]::CurrentUICulture.TwoLetterISOLanguageName -eq "ar") { "ar" } else { "en" }
if (Test-Path $EditorConfig) {
    try { $cfg = Get-Content $EditorConfig -Raw -Encoding UTF8 | ConvertFrom-Json; if (@("ar", "en") -contains $cfg.lang) { $script:Lang = $cfg.lang } } catch { }
}
function W([string]$key) { return $Words[$script:Lang][$key] }

# A number of settings in the window's language, as it reads after «تم تغيير». Arabic counts the noun by the
# number: إعداد واحد، إعدادين، 3 to 10 إعدادات، 11 and more إعدادًا.
function Settings-Count([int]$n) {
    if ($script:Lang -ne "ar") { if ($n -eq 1) { return "1 setting" } else { return "$n settings" } }
    if ($n -eq 1) { return "إعداد واحد" }
    if ($n -eq 2) { return "إعدادين" }
    $m = $n % 100
    if ($m -ge 3 -and $m -le 10) { return "$n إعدادات" }
    return "$n إعدادًا"
}

# The TV answers in English (its answers are also read in a terminal); the window shows the known ones in its own
# language. Anything else (a refusal and its reasons) is shown as the TV said it.
function Tv-Words([string]$answer) {
    if ($answer -match '^Changed (\d+) setting\(s\)$') { return [string]::Format((W "changed"), (Settings-Count ([int]$Matches[1]))) }
    if ($answer -like "Nothing to change*") { return (W "nothingToChange") }
    if ($answer -eq "Shown on the TV") { return (W "shownOnTv") }
    return $answer
}

# ---------------------------------------------------------------- adb

function Find-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", "C:\adb\adb.exe")) {
        if (Test-Path $c) { return $c }
    }
    return $null
}
$script:Adb = Find-Adb

# The adb server keeps open the folder it was first started from, so Windows refuses to move, rename or delete
# that folder until adb stops. Started from the temp folder, it never holds the project.
function Start-AdbServer([string]$adb) {
    Push-Location $env:TEMP
    try { & $adb start-server 2>&1 | Out-Null } finally { Pop-Location }
}
if ($script:Adb) { Start-AdbServer $script:Adb }

function Adb([string]$serial, [string[]]$arguments) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"   # adb writes its progress on stderr
    try { $out = & $script:Adb -s $serial @arguments 2>&1 | Out-String } finally { $ErrorActionPreference = $old }
    return $out.Trim()
}
function Shell([string]$serial, [string]$command) { return Adb $serial @("shell", $command) }

function Version-Of([string]$serial) {
    $out = Shell $serial "dumpsys package $Pkg | grep versionName"
    if ($out -match "versionName=(\S+)") { return $Matches[1] }
    return $null
}

# "1.0.0" compared part by part; a missing version is the oldest.
function Version-Less([string]$a, [string]$b) {
    if (-not $a) { return $true }
    if (-not $b) { return $false }
    $x = @($a -split "\." | ForEach-Object { [int]$_ }); $y = @($b -split "\." | ForEach-Object { [int]$_ })
    for ($i = 0; $i -lt [Math]::Max($x.Count, $y.Count); $i++) {
        $p = 0; $q = 0
        if ($i -lt $x.Count) { $p = $x[$i] }
        if ($i -lt $y.Count) { $q = $y[$i] }
        if ($p -ne $q) { return ($p -lt $q) }
    }
    return $false
}

# ---------------------------------------------------------------- the TVs this computer has met (tvs.jsonc)

$script:Registry = $null     # id -> @{ name; model; deviceName; address; version; seen }

function Load-Registry {
    $script:Registry = [ordered]@{}
    if (Test-Path $TvsFile) {
        try {
            $o = Strip-Jsonc ([IO.File]::ReadAllText($TvsFile, [Text.Encoding]::UTF8)) | ConvertFrom-Json
            foreach ($p in $o.PSObject.Properties) {
                $e = [ordered]@{ name = ""; model = ""; deviceName = ""; address = ""; version = ""; seen = "" }
                foreach ($f in @($e.Keys)) { if ($null -ne $p.Value.$f) { $e[$f] = [string]$p.Value.$f } }
                $script:Registry[$p.Name] = $e
            }
        } catch { Log "tvs.jsonc unreadable: $($_.Exception.Message)" }
    }
}

function Save-Registry {
    $lines = New-Object Collections.Generic.List[string]
    $lines.Add("// " + (W "tvsHeader"))
    $lines.Add("{")
    $ids = @($script:Registry.Keys)
    for ($i = 0; $i -lt $ids.Count; $i++) {
        $e = $script:Registry[$ids[$i]]
        $lines.Add("  " + (ConvertTo-Json -InputObject $ids[$i]) + ": {")
        $fields = @("name", "model", "deviceName", "address", "version", "seen")
        for ($k = 0; $k -lt $fields.Count; $k++) {
            $comma = if ($k -lt $fields.Count - 1) { "," } else { "" }
            $lines.Add("    ""$($fields[$k])"": " + (ConvertTo-Json -InputObject ([string]$e[$fields[$k]])) + $comma)
        }
        $lines.Add("  }" + $(if ($i -lt $ids.Count - 1) { "," } else { "" }))
    }
    $lines.Add("}")
    Write-Utf8 $TvsFile (($lines -join "`r`n") + "`r`n")
}

# Every TV met: the connected ones now (one entry per TV: a wireless TV may be listed twice under changing names),
# then the rest of the registry, not connected.
function Get-Tvs {
    if ($null -eq $script:Registry) { Load-Registry }
    $online = @{}
    if ($script:Adb) {
        $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
        $lines = @(& $script:Adb devices 2>&1 | ForEach-Object { "$_" })
        $ErrorActionPreference = $old
        foreach ($line in $lines) {
            if ($line -notmatch "^(\S.*?)\s+device$") { continue }
            $serial = $Matches[1]
            $id = (Shell $serial "getprop ro.serialno") -replace "[^A-Za-z0-9._-]", "_"
            if (-not $id) { $id = $serial -replace "[^A-Za-z0-9._-]", "_" }
            if ($online.ContainsKey($id)) { continue }
            $online[$id] = $serial
            $e = $script:Registry[$id]
            if (-not $e) { $e = [ordered]@{ name = ""; model = ""; deviceName = ""; address = ""; version = ""; seen = "" }; $script:Registry[$id] = $e }
            $e.model = Shell $serial "getprop ro.product.model"
            $dn = Shell $serial "settings get global device_name"
            $e.deviceName = if ($dn -and $dn -ne "null") { $dn } else { "" }
            $e.address = $serial
            $v = Version-Of $serial
            $e.version = if ($v) { $v } else { "" }
            $e.seen = Get-Date -Format "yyyy-MM-dd HH:mm"
        }
        Save-Registry
    }
    $list = @()
    foreach ($id in @($script:Registry.Keys)) {
        $e = $script:Registry[$id]
        $isOn = $online.ContainsKey($id)
        $list += New-Object PSObject -Property ([ordered]@{
            Id = $id; Serial = $(if ($isOn) { $online[$id] } else { $e.address }); Online = $isOn
            Model = $(if ($e.model) { $e.model } else { $id }); DeviceName = $e.deviceName; Name = $e.name
            Version = $e.version; Seen = $e.seen; Address = $e.address; Selected = $false; Display = "" })
    }
    # Shown names: yours, else the TV's device name, else its model; alike ones get the end of their serial.
    foreach ($t in $list) { $t.Display = if ($t.Name) { $t.Name } elseif ($t.DeviceName) { $t.DeviceName } else { $t.Model } }
    $groups = $list | Group-Object Display | Where-Object { $_.Count -gt 1 }
    foreach ($g in $groups) {
        foreach ($t in $g.Group) { $t.Display = "$($t.Display) · …" + $t.Id.Substring([Math]::Max(0, $t.Id.Length - 3)) }
    }
    # Connected first, then the rest by when they were last seen.
    return @(@($list | Where-Object { $_.Online }) + @($list | Where-Object { -not $_.Online } | Sort-Object Seen -Descending))
}

function Name-Of($tv) {
    if ($tv.Name -or $tv.DeviceName) { return "$($tv.Display) · $($tv.Model)" }
    return $tv.Display
}
function Set-Name($tv, [string]$name) {
    $e = $script:Registry[$tv.Id]
    if ($e) { $e.name = $name; Save-Registry }
}

# The TV's answer to an action; throws with its words when it did not work.
function Send-Action([string]$serial, [string]$action, [string]$extras = "") {
    $out = Shell $serial "am broadcast -f 0x20 -n $Receiver -a $action $extras"
    if ($out -match 'result=(-?\d+)(, data="(.*)")?') {
        if ([int]$Matches[1] -eq 1) { return $Matches[3] }
        if ($Matches[3]) { throw ($Matches[3] -replace " \| ", "`n") }
    }
    throw "Awan TV did not answer: $out"
}

# The TV's settings and description, copied to this computer; returns @{ File; Schema }.
function Export-Tv($tv, [string]$file) {
    [void](Send-Action $tv.Serial "$Pkg.EXPORT")
    [void](Adb $tv.Serial @("pull", $Remote, $file))
    [void](Shell $tv.Serial "rm -f $Remote")
    if (-not (Test-Path $file)) { throw "Could not copy the settings from the TV." }
    # The description in Arabic and in English.
    $schema = $null
    foreach ($pair in @(@("-ar", "$Files/awantv-settings-schema-ar.json"), @("-en", "$Files/awantv-settings-schema-en.json"))) {
        if ((Shell $tv.Serial "test -f $($pair[1]) && echo yes") -ne "yes") { continue }
        $local = Join-Path $SchemaDir ("awantv-schema-$($tv.Version)$($pair[0]).json")
        [void](Adb $tv.Serial @("pull", $pair[1], $local))
        if ((Test-Path $local) -and -not $schema) { $schema = $local }
    }
    return @{ File = $file; Schema = $schema }
}

# ---------------------------------------------------------------- files

function Write-Utf8([string]$path, [string]$text) {
    [IO.File]::WriteAllText($path, $text, (New-Object Text.UTF8Encoding($false)))
}
function Stamp { return (Get-Date -Format "yyyyMMdd-HHmmss") }

# The settings file without its // comments and trailing commas (as the TV reads it).
function Strip-Jsonc([string]$s) {
    $b = New-Object Text.StringBuilder($s.Length)
    $inString = $false; $escape = $false
    for ($i = 0; $i -lt $s.Length; $i++) {
        $ch = $s[$i]
        if ($inString) {
            [void]$b.Append($ch)
            if ($escape) { $escape = $false } elseif ($ch -eq '\') { $escape = $true } elseif ($ch -eq '"') { $inString = $false }
        } elseif ($ch -eq '"') {
            $inString = $true; [void]$b.Append($ch)
        } elseif ($ch -eq '/' -and $i + 1 -lt $s.Length -and $s[$i + 1] -eq '/') {
            while ($i + 1 -lt $s.Length -and $s[$i + 1] -ne "`n") { $i++ }
        } elseif ($ch -eq ',') {
            $j = $i + 1
            while ($j -lt $s.Length) {
                if ([char]::IsWhiteSpace($s[$j])) { $j++ }
                elseif ($s[$j] -eq '/' -and $j + 1 -lt $s.Length -and $s[$j + 1] -eq '/') { while ($j -lt $s.Length -and $s[$j] -ne "`n") { $j++ } }
                else { break }
            }
            if ($j -ge $s.Length -or ($s[$j] -ne '}' -and $s[$j] -ne ']')) { [void]$b.Append($ch) }
        } else {
            [void]$b.Append($ch)
        }
    }
    return $b.ToString()
}

function Read-Settings([string]$path) {
    $text = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    $o = Strip-Jsonc $text | ConvertFrom-Json
    if ($o.app -ne $Pkg) { throw "Not an Awan TV settings file: $path" }
    return $o
}

function Load-Schema([string]$version) {
    # This version in the window's language, else the newest kept.
    $file = $null
    $f = Join-Path $SchemaDir "awantv-schema-$version-$($script:Lang).json"
    if (Test-Path $f) { $file = $f }
    if (-not $file) {
        $best = $null
        foreach ($f in @(Get-ChildItem $SchemaDir -Filter "awantv-schema-*.json" -ErrorAction SilentlyContinue)) {
            if ($f.BaseName -notmatch "^awantv-schema-(\d+(\.\d+)*)(-(ar|en))?$") { continue }
            $v = $Matches[1]; $l = $Matches[4]
            $better = (-not $best) -or (Version-Less $best.V $v) -or ($best.V -eq $v -and $l -eq $script:Lang)
            if ($better) { $best = @{ V = $v; F = $f.FullName } }
        }
        if ($best) { $file = $best.F }
    }
    if (-not $file) { return $null }
    return ([IO.File]::ReadAllText($file, [Text.Encoding]::UTF8) | ConvertFrom-Json)
}

# ---------------------------------------------------------------- the settings in memory

$script:Schema = $null       # the description (sections, items, views, combos, apps)
$script:Values = $null       # key -> value as the file writes it
$script:Orig = @{}           # key -> value when got or opened, for the changed marks
$script:Extra = $null        # keys in the file the description does not know: not sent (see Build-File)
$script:SourceVersion = $null
$script:SourceText = ""
$script:Views = @{ iqama = "days" }
$script:Items = @{}          # key -> description item

function Norm($item, $v) {
    if ($null -eq $v) { return $null }
    switch ($item.kind) {
        "bool" { return [bool]$v }
        "range" { return [int]$v }
        "choice" { if ($item.values.Count -gt 0 -and ($item.values[0] -is [string])) { return [string]$v } else { return [int]$v } }
        "time" { return [string]$v }
        "list" { return @($v | ForEach-Object { [string]$_ }) }
        default { return $v }
    }
}
function Same($a, $b) {
    if ($a -is [array] -or $b -is [array]) { return ((@($a) -join "`n") -eq (@($b) -join "`n")) }
    if ($a -is [PSCustomObject] -or $b -is [PSCustomObject]) { return (($a | ConvertTo-Json -Compress) -eq ($b | ConvertTo-Json -Compress)) }
    return ("$a" -eq "$b")
}

function Use-Settings($fileObject, [string]$sourceText) {
    $version = [string]$fileObject.version
    $schema = Load-Schema $version
    if (-not $schema) { throw (W "noSchema") }
    $script:Schema = $schema
    $script:Items = @{}
    foreach ($sec in $schema.sections) { foreach ($it in $sec.items) { $script:Items[$it.key] = $it } }
    $script:Values = [ordered]@{}
    $script:Extra = [ordered]@{}
    foreach ($p in $fileObject.PSObject.Properties) {
        if (@("app", "format", "version") -contains $p.Name) { continue }
        $it = $script:Items[$p.Name]
        if ($it) { $script:Values[$p.Name] = Norm $it $p.Value } else { $script:Extra[$p.Name] = $p.Value }
    }
    $script:Orig = @{}
    foreach ($k in @($script:Values.Keys)) { $script:Orig[$k] = $script:Values[$k] }
    $script:SourceVersion = $version
    $script:SourceText = $sourceText
}

function Changed-Count {
    $n = 0
    if (-not $script:Values) { return 0 }
    foreach ($k in @($script:Values.Keys)) { if (-not (Same $script:Values[$k] $script:Orig[$k])) { $n++ } }
    return $n
}

# The file to send or save: the settings, a comment above each from the description, in the TV's order.
function Build-File([string[]]$onlyKeys) {
    $lines = New-Object Collections.Generic.List[string]
    $lines.Add("// Awan TV settings · AwanTV-editor v$ScriptVersion · $(Get-Date -Format 'yyyy-MM-dd HH:mm')")
    $lines.Add("{")
    $lines.Add("  ""app"": ""$Pkg"",")
    $lines.Add("  ""format"": 2,")
    $lines.Add("  ""version"": ""$($script:SourceVersion)""")
    $body = New-Object Collections.Generic.List[string]
    foreach ($sec in $script:Schema.sections) {
        foreach ($it in $sec.items) {
            if (-not $script:Values.Contains($it.key)) { continue }
            if ($onlyKeys -and ($onlyKeys -notcontains $it.key)) { continue }
            $v = $script:Values[$it.key]
            $json = if ($it.kind -eq "list") { ConvertTo-Json -InputObject @($v) -Compress } else { ConvertTo-Json -InputObject $v -Compress -Depth 5 }
            $body.Add("  // $($it.label)")
            $body.Add("  ""$($it.key)"": $json")
        }
    }
    # Keys the description does not know ($script:Extra: a file from a newer version) are left out: the TV would
    # refuse the whole file for one setting it does not know.
    # Commas after every value line but the last.
    $valueLines = @(for ($i = 0; $i -lt $body.Count; $i++) { if ($body[$i] -notmatch "^\s*//") { $i } })
    if ($valueLines.Count -gt 0) { $lines[$lines.Count - 1] = $lines[$lines.Count - 1] + "," }
    for ($i = 0; $i -lt $body.Count; $i++) {
        $line = $body[$i]
        if ($valueLines -contains $i -and $i -ne $valueLines[-1]) { $line += "," }
        $lines.Add($line)
    }
    $lines.Add("}")
    return ($lines -join "`r`n") + "`r`n"
}

# ---------------------------------------------------------------- the window

$Navy = "#0C1B33"; $Gold = "#B8892E"
[xml]$xaml = @"
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Awan TV" Width="1280" Height="820" MinWidth="980" MinHeight="600" Background="#F3F3F3"
        FontFamily="Segoe UI Variable Text, Segoe UI" FontSize="14" WindowStartupLocation="CenterScreen">
  <Window.Resources>
    <Style x:Key="Btn" TargetType="Button">
      <Setter Property="Background" Value="White"/><Setter Property="BorderBrush" Value="#D1D1D1"/>
      <Setter Property="Padding" Value="14,6"/><Setter Property="Margin" Value="4,0"/><Setter Property="Cursor" Value="Hand"/>
      <Setter Property="Template"><Setter.Value><ControlTemplate TargetType="Button">
        <Border x:Name="b" Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}" BorderThickness="1" CornerRadius="6" Padding="{TemplateBinding Padding}">
          <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/></Border>
        <ControlTemplate.Triggers>
          <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="b" Property="Opacity" Value="0.85"/></Trigger>
          <Trigger Property="IsEnabled" Value="False"><Setter TargetName="b" Property="Opacity" Value="0.4"/></Trigger>
        </ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter>
    </Style>
    <Style x:Key="Primary" TargetType="Button" BasedOn="{StaticResource Btn}">
      <Setter Property="Background" Value="$Navy"/><Setter Property="BorderBrush" Value="$Navy"/><Setter Property="Foreground" Value="White"/>
    </Style>
    <Style x:Key="Switch" TargetType="CheckBox">
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="Template"><Setter.Value><ControlTemplate TargetType="CheckBox">
        <Border x:Name="track" Width="40" Height="20" CornerRadius="10" BorderThickness="1" BorderBrush="#8A8A8A" Background="Transparent">
          <Ellipse x:Name="knob" Width="12" Height="12" Fill="#5F5F5F" HorizontalAlignment="Left" Margin="4,0"/></Border>
        <ControlTemplate.Triggers>
          <Trigger Property="IsChecked" Value="True">
            <Setter TargetName="track" Property="Background" Value="$Navy"/><Setter TargetName="track" Property="BorderBrush" Value="$Navy"/>
            <Setter TargetName="knob" Property="Fill" Value="White"/><Setter TargetName="knob" Property="HorizontalAlignment" Value="Right"/>
          </Trigger>
        </ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter>
    </Style>
    <Style x:Key="Seg" TargetType="RadioButton">
      <Setter Property="Cursor" Value="Hand"/>
      <Setter Property="Template"><Setter.Value><ControlTemplate TargetType="RadioButton">
        <Border x:Name="b" Background="White" Padding="14,6"><ContentPresenter x:Name="c" HorizontalAlignment="Center"/></Border>
        <ControlTemplate.Triggers>
          <Trigger Property="IsChecked" Value="True"><Setter TargetName="b" Property="Background" Value="$Navy"/><Setter Property="Foreground" Value="White"/></Trigger>
        </ControlTemplate.Triggers></ControlTemplate></Setter.Value></Setter>
    </Style>
    <Style TargetType="ComboBox"><Setter Property="MinWidth" Value="200"/><Setter Property="Height" Value="32"/><Setter Property="VerticalContentAlignment" Value="Center"/></Style>
  </Window.Resources>
  <DockPanel>
    <Border DockPanel.Dock="Top" BorderBrush="#E5E5E5" BorderThickness="0,0,0,1" Padding="18,10">
      <DockPanel>
        <TextBlock x:Name="SourceLabel" VerticalAlignment="Center" Foreground="#5F5F5F" Margin="0,0,8,0"/>
        <ComboBox x:Name="SourceBox" Width="320" Margin="0,0,6,0"/>
        <Button x:Name="RefreshBtn" Style="{StaticResource Btn}" Content="⟳"/>
        <Button x:Name="GetBtn" Style="{StaticResource Primary}"/>
        <Button x:Name="OpenBtn" Style="{StaticResource Btn}"/>
        <Button x:Name="LangBtn" Style="{StaticResource Btn}" MinWidth="44" FontWeight="SemiBold"/>
        <TextBlock x:Name="ShownText" VerticalAlignment="Center" HorizontalAlignment="Right" Foreground="#5F5F5F" TextTrimming="CharacterEllipsis"/>
      </DockPanel>
    </Border>
    <Border DockPanel.Dock="Bottom" BorderBrush="#E5E5E5" BorderThickness="0,1,0,0" Background="#FAFAFA" Padding="18,10">
      <DockPanel>
        <TextBlock x:Name="ChangesText" VerticalAlignment="Center" Foreground="$Gold" FontWeight="SemiBold"/>
        <StackPanel Orientation="Horizontal" HorizontalAlignment="Right">
          <TextBlock x:Name="SendToLabel" VerticalAlignment="Center" Foreground="#5F5F5F" Margin="0,0,8,0"/>
          <ToggleButton x:Name="TargetsBtn" MinWidth="230" Height="34" Padding="10,0" Background="White" BorderBrush="#D1D1D1" HorizontalContentAlignment="Left"/>
          <Button x:Name="SendBtn" Style="{StaticResource Primary}" Margin="8,0,4,0"/>
          <Border Width="1" Height="24" Background="#E5E5E5" Margin="8,0"/>
          <Button x:Name="SaveBtn" Style="{StaticResource Btn}"/>
          <Button x:Name="RestoreBtn" Style="{StaticResource Btn}"/>
        </StackPanel>
      </DockPanel>
    </Border>
    <Popup x:Name="TargetsPop" StaysOpen="False" Placement="Top" AllowsTransparency="True">
      <Border Background="White" BorderBrush="#D1D1D1" BorderThickness="1" CornerRadius="8" Padding="8" Width="420" Margin="8">
        <Border.Effect><DropShadowEffect BlurRadius="16" ShadowDepth="4" Opacity="0.2"/></Border.Effect>
        <StackPanel>
          <DockPanel Margin="4,0,4,8">
            <Button x:Name="NoneBtn" DockPanel.Dock="Right" Style="{StaticResource Btn}" Padding="8,4"/>
            <Button x:Name="AllBtn" DockPanel.Dock="Right" Style="{StaticResource Btn}" Padding="8,4"/>
            <TextBox x:Name="SearchBox" Height="30" VerticalContentAlignment="Center" Padding="6,0"/>
          </DockPanel>
          <ScrollViewer MaxHeight="330" VerticalScrollBarVisibility="Auto"><StackPanel x:Name="TargetsList"/></ScrollViewer>
          <TextBlock x:Name="TargetsNote" Foreground="#5F5F5F" FontSize="11" Margin="8,8,8,2" TextWrapping="Wrap"/>
        </StackPanel>
      </Border>
    </Popup>
    <Border DockPanel.Dock="Left" Width="250" BorderBrush="#E5E5E5" BorderThickness="0,0,1,0" Padding="10,14">
      <StackPanel>
        <ListBox x:Name="Nav" BorderThickness="0" Background="Transparent"/>
        <TextBlock x:Name="NavNote" Foreground="#5F5F5F" FontSize="11" Margin="12,10" TextWrapping="Wrap"/>
      </StackPanel>
    </Border>
    <ScrollViewer x:Name="Scroll" VerticalScrollBarVisibility="Auto" Padding="22,16">
      <StackPanel x:Name="Rows"/>
    </ScrollViewer>
  </DockPanel>
</Window>
"@
$window = [Windows.Markup.XamlReader]::Load((New-Object Xml.XmlNodeReader $xaml))

# The window's icon: Awan TV's gold mihrab on lapis (not PowerShell's), drawn here so no file is needed.
function Awan-Icon {
    $size = 64
    $visual = New-Object Windows.Media.DrawingVisual
    $dc = $visual.RenderOpen()
    $dc.DrawRoundedRectangle((New-Object Windows.Media.SolidColorBrush ([Windows.Media.ColorConverter]::ConvertFromString("#0C1B33"))),
        $null, (New-Object Windows.Rect(0, 0, $size, $size)), 12, 12)
    $arch = [Windows.Media.Geometry]::Parse("M32,8 C44,17 50,26 50,38 L50,56 L14,56 L14,38 C14,26 20,17 32,8 Z")
    $dc.DrawGeometry((New-Object Windows.Media.SolidColorBrush ([Windows.Media.ColorConverter]::ConvertFromString("#D8B46A"))), $null, $arch)
    $dc.Close()
    $bmp = New-Object Windows.Media.Imaging.RenderTargetBitmap($size, $size, 96, 96, [Windows.Media.PixelFormats]::Pbgra32)
    $bmp.Render($visual)
    return [Windows.Media.Imaging.BitmapFrame]::Create($bmp)
}
try { $window.Icon = Awan-Icon } catch { Log "Icon: $($_.Exception.Message)" }
$ui = @{}
foreach ($n in @("SourceLabel", "SourceBox", "RefreshBtn", "GetBtn", "OpenBtn", "LangBtn", "ShownText", "ChangesText", "SendToLabel", "TargetsBtn",
                 "SendBtn", "SaveBtn", "RestoreBtn", "TargetsPop", "NoneBtn", "AllBtn", "SearchBox", "TargetsList", "TargetsNote",
                 "Nav", "NavNote", "Scroll", "Rows")) { $ui[$n] = $window.FindName($n) }
$ui.TargetsPop.PlacementTarget = $ui.TargetsBtn

function Brush([string]$hex) { return (New-Object Windows.Media.BrushConverter).ConvertFromString($hex) }

$script:Notice = ""          # the last Identify's answer, shown at the top until the next action
function Busy([scriptblock]$work) {
    $script:Notice = ""
    $window.Cursor = [Windows.Input.Cursors]::Wait
    $ui.ShownText.Text = W "working"
    # Let the window draw before the adb calls block it.
    $window.Dispatcher.Invoke([Action]{}, [Windows.Threading.DispatcherPriority]::Background)
    try { & $work }
    catch {
        Log "Error: $($_.Exception.Message)"
        [void](Show-Dialog "Awan TV" @(@{ Text = $_.Exception.Message }) "warn" @((W "okBtn")))
    }
    finally { $window.Cursor = $null; Update-Chrome }
}

# ---------------------------------------------------------------- chrome (the window's own words)

function Update-Chrome {
    $window.FlowDirection = if ($script:Lang -eq "ar") { "RightToLeft" } else { "LeftToRight" }
    $window.Title = "$(W 'title')  v$ScriptVersion"
    $ui.SourceLabel.Text = W "source"; $ui.GetBtn.Content = "⭳  " + (W "get"); $ui.OpenBtn.Content = W "open"
    $ui.RefreshBtn.ToolTip = W "refresh"; $ui.SendToLabel.Text = W "sendTo"; $ui.SendBtn.Content = "⭱  " + (W "send")
    $ui.SaveBtn.Content = W "save"; $ui.RestoreBtn.Content = W "restore"; $ui.AllBtn.Content = W "all"; $ui.NoneBtn.Content = W "none"
    # The other language, named in itself, so it is found by someone who does not read this one.
    $ui.LangBtn.Content = if ($script:Lang -eq "ar") { "EN" } else { "ع" }
    $ui.LangBtn.ToolTip = if ($script:Lang -eq "ar") { "English" } else { "العربية" }
    $ui.TargetsNote.Text = W "targetsNote"
    $ui.ShownText.Text = if ($script:Notice) { $script:Notice } elseif ($script:SourceText) { [string]::Format((W "shown"), $script:SourceText) } else { W "nothing" }
    $n = Changed-Count
    $ui.ChangesText.Text = if ($n -gt 0) { [string]::Format((W "changes"), $n) } else { "" }
    $online = @($script:Tvs | Where-Object { $_.Online })
    $chosen = @($online | Where-Object { $_.Selected })
    $ui.TargetsBtn.Content = if ($chosen.Count -gt 0) { [string]::Format((W "targets"), $chosen.Count, $online.Count) + "   ⌃" } else { (W "noTargets") + "   ⌃" }
    $have = $null -ne $script:Values
    $ui.SendBtn.IsEnabled = $have -and $chosen.Count -gt 0
    $ui.SaveBtn.IsEnabled = $have
    $ui.GetBtn.IsEnabled = $null -ne $ui.SourceBox.SelectedItem
    if ($script:Schema) {
        $ui.NavNote.Text = if ($script:Lang -eq "ar") { "بترتيب الإعدادات في التلفاز. «الشبكة» و«حول التطبيق» بلا إعدادات تُنقل." } else { "In the TV's Settings order. Network and About have nothing to copy." }
    }
}

# ---------------------------------------------------------------- TVs

$script:Tvs = @()

function Refresh-Tvs {
    $chosen = @{}
    foreach ($t in $script:Tvs) { if ($t.Selected) { $chosen[$t.Id] = $true } }
    $script:Tvs = @(Get-Tvs)
    foreach ($t in $script:Tvs) { if ($t.Online -and $chosen[$t.Id]) { $t.Selected = $true } }
    $ui.SourceBox.Items.Clear()
    foreach ($t in @($script:Tvs | Where-Object { $_.Online })) {
        $item = New-Object Windows.Controls.ComboBoxItem
        $item.Content = "$(Name-Of $t) · v$($t.Version)"
        $item.Tag = $t
        [void]$ui.SourceBox.Items.Add($item)
    }
    if ($ui.SourceBox.Items.Count -gt 0) { $ui.SourceBox.SelectedIndex = 0 }
    if (-not $script:Adb) { [void](Show-Dialog "Awan TV" @(@{ Text = (W "noAdb") }) "warn" @((W "okBtn"))) }
    elseif ($ui.SourceBox.Items.Count -eq 0) { $ui.ShownText.Text = W "noTv" }
    Fill-Targets
}

# A TV's name on this computer: the text, "" to clear it (Save with the box empty), or $null when cancelled.
function Ask-Name([string]$tvTitle, [string]$current) {
    $dlg = New-Object Windows.Window
    $dlg.Title = W "rename"; $dlg.Owner = $window; $dlg.WindowStartupLocation = "CenterOwner"; $dlg.ResizeMode = "NoResize"
    $dlg.SizeToContent = "WidthAndHeight"; $dlg.Background = Brush "#F3F3F3"; $dlg.FontFamily = $window.FontFamily; $dlg.FontSize = 14
    $dlg.FlowDirection = $window.FlowDirection; $dlg.ShowInTaskbar = $false
    try { $dlg.Icon = $window.Icon } catch { }
    $panel = New-Object Windows.Controls.StackPanel; $panel.Margin = "22,18"; $panel.Width = 420
    $l1 = New-Object Windows.Controls.TextBlock; $l1.Text = $tvTitle; $l1.FontWeight = "SemiBold"; $l1.TextWrapping = "Wrap"
    $l2 = New-Object Windows.Controls.TextBlock; $l2.Text = W "renameAsk"; $l2.Foreground = Brush "#5F5F5F"; $l2.Margin = "0,4,0,8"; $l2.TextWrapping = "Wrap"
    $box = New-Object Windows.Controls.TextBox; $box.Text = $current; $box.Height = 32; $box.VerticalContentAlignment = "Center"; $box.Padding = "6,0"
    $l3 = New-Object Windows.Controls.TextBlock; $l3.Text = W "renameHint"; $l3.Foreground = Brush "#5F5F5F"; $l3.FontSize = 12; $l3.Margin = "0,6,0,0"; $l3.TextWrapping = "Wrap"
    $buttons = New-Object Windows.Controls.StackPanel; $buttons.Orientation = "Horizontal"; $buttons.HorizontalAlignment = "Right"; $buttons.Margin = "0,16,0,0"
    $ok = New-Object Windows.Controls.Button; $ok.Content = W "saveName"; $ok.Style = $window.FindResource("Primary"); $ok.IsDefault = $true
    $cancel = New-Object Windows.Controls.Button; $cancel.Content = W "cancel"; $cancel.Style = $window.FindResource("Btn"); $cancel.IsCancel = $true
    # Through $this, not the function's variables: the handlers run when WPF calls them.
    $ok.Add_Click({ [Windows.Window]::GetWindow($this).DialogResult = $true })
    [void]$buttons.Children.Add($ok); [void]$buttons.Children.Add($cancel)
    foreach ($c in @($l1, $l2, $box, $l3, $buttons)) { [void]$panel.Children.Add($c) }
    $dlg.Content = $panel
    $dlg.Add_ContentRendered({ $b = $this.Content.Children[2]; [void]$b.Focus(); $b.SelectAll() })
    if ($dlg.ShowDialog()) { return $box.Text }
    return $null
}

# The editor's own message window, in its language and direction: Windows' MessageBox takes its buttons' words from
# Windows' language, not the editor's ("OK" in an Arabic window). $items: @{ Title; Text } each (Title may be empty);
# $kind: info, ask or warn; $buttons: their words, the first the main action, the last the way out (Esc).
# A question (ask) keeps Enter on the way out, so nothing is lost by a key pressed too soon.
# Returns the index of the button pressed, -1 when the window is closed.
function Show-Dialog([string]$title, [object[]]$items, [string]$kind, [string[]]$buttons) {
    $dlg = New-Object Windows.Window
    $dlg.Title = $title; $dlg.ResizeMode = "NoResize"; $dlg.SizeToContent = "WidthAndHeight"; $dlg.ShowInTaskbar = $false
    $dlg.Background = Brush "#F3F3F3"; $dlg.FontFamily = $window.FontFamily; $dlg.FontSize = 14; $dlg.FlowDirection = $window.FlowDirection
    if ($window.IsVisible) { $dlg.Owner = $window; $dlg.WindowStartupLocation = "CenterOwner" } else { $dlg.WindowStartupLocation = "CenterScreen" }
    try { $dlg.Icon = $window.Icon } catch { }
    $dlg.Tag = -1
    $look = @{ info = @("i", "#1F6FEB"); ask = @("?", "#5F6B7A"); warn = @("!", $Gold) }[$kind]
    $circle = New-Object Windows.Controls.Border
    $circle.Width = 30; $circle.Height = 30; $circle.CornerRadius = 15; $circle.Background = Brush $look[1]
    $circle.VerticalAlignment = "Top"; $circle.Margin = "0,0,14,0"
    $glyph = New-Object Windows.Controls.TextBlock
    $glyph.Text = $look[0]; $glyph.Foreground = Brush "#FFFFFF"; $glyph.FontWeight = "SemiBold"
    $glyph.HorizontalAlignment = "Center"; $glyph.VerticalAlignment = "Center"
    $circle.Child = $glyph
    $texts = New-Object Windows.Controls.StackPanel; $texts.Width = 400
    foreach ($it in $items) {
        if ($it.Title) {
            $t1 = New-Object Windows.Controls.TextBlock; $t1.Text = $it.Title; $t1.FontWeight = "SemiBold"; $t1.TextWrapping = "Wrap"
            [void]$texts.Children.Add($t1)
        }
        $t2 = New-Object Windows.Controls.TextBlock; $t2.Text = $it.Text; $t2.TextWrapping = "Wrap"; $t2.Margin = "0,2,0,10"
        if ($it.Title) { $t2.Foreground = Brush "#3A3A3A" }
        [void]$texts.Children.Add($t2)
    }
    $row = New-Object Windows.Controls.DockPanel
    [Windows.Controls.DockPanel]::SetDock($circle, "Left")
    [void]$row.Children.Add($circle); [void]$row.Children.Add($texts)
    $bar = New-Object Windows.Controls.StackPanel; $bar.Orientation = "Horizontal"; $bar.HorizontalAlignment = "Right"; $bar.Margin = "0,8,0,0"
    $last = $buttons.Count - 1
    for ($i = 0; $i -le $last; $i++) {
        $b = New-Object Windows.Controls.Button
        $b.Content = $buttons[$i]; $b.Tag = $i
        $b.Style = $window.FindResource($(if ($i -eq 0) { "Primary" } else { "Btn" }))
        if ($i -eq $last) { $b.IsCancel = $true }
        if (($kind -eq "ask" -and $i -eq $last) -or ($kind -ne "ask" -and $i -eq 0)) { $b.IsDefault = $true }
        # Through $this, not the function's variables: the handler runs when WPF calls it.
        $b.Add_Click({ $w = [Windows.Window]::GetWindow($this); $w.Tag = $this.Tag; $w.Close() })
        [void]$bar.Children.Add($b)
    }
    $outer = New-Object Windows.Controls.StackPanel; $outer.Margin = "22,18"
    [void]$outer.Children.Add($row); [void]$outer.Children.Add($bar)
    $dlg.Content = $outer
    [void]$dlg.ShowDialog()
    return [int]$dlg.Tag
}

function Small-Button([string]$text, [string]$tip) {
    $b = New-Object Windows.Controls.Button
    $b.Content = $text; $b.ToolTip = $tip; $b.Style = $window.FindResource("Btn"); $b.Padding = "8,2"; $b.FontSize = 12
    [Windows.Controls.DockPanel]::SetDock($b, "Right")
    return $b
}

# The "Send to" list: the connected TVs, then (below, apart) those met before and not connected now.
function Fill-Targets {
    $ui.TargetsList.Children.Clear()
    $filter = $ui.SearchBox.Text.Trim()
    $headed = $false
    foreach ($t in $script:Tvs) {
        $title = Name-Of $t
        if ($filter -and ("$title $($t.Id) $($t.Serial)" -notlike "*$filter*")) { continue }
        if (-not $t.Online -and -not $headed) {
            $headed = $true
            $h = New-Object Windows.Controls.TextBlock
            $h.Text = W "offlineGroup"; $h.FontSize = 12; $h.FontWeight = "SemiBold"; $h.Foreground = Brush "#5F5F5F"; $h.Margin = "8,12,8,4"
            [void]$ui.TargetsList.Children.Add($h)
        }
        $row = New-Object Windows.Controls.DockPanel
        $row.Margin = "4,3"
        $rename = Small-Button "✎" (W "rename")
        $rename.Tag = $t
        $rename.Add_Click({
            param($s, $e)
            $tv = $s.Tag
            # Save with the box empty clears the name; Cancel or Esc changes nothing.
            $answer = Ask-Name (Name-Of $tv) ([string]$tv.Name)
            if ($null -eq $answer) { return }
            Set-Name $tv $answer.Trim()
            Refresh-Names
        })
        [void]$row.Children.Add($rename)
        if ($t.Online) {
            $ident = Small-Button ("◉ " + (W "identify")) (W "identifyTip")
            $ident.Tag = $t
            $ident.Add_Click({
                param($s, $e)
                $tv = $s.Tag
                Busy {
                    # Only the computer's name: the TV adds its own device name, model and serial.
                    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$tv.Name))
                    $extras = "--es id $($tv.Id)"
                    if ($b64) { $extras += " --es name_b64 $b64" }
                    $answer = Tv-Words (Send-Action $tv.Serial "$Pkg.IDENTIFY" $extras)
                    Log "Identify $($tv.Id) ($($tv.Serial)): $answer"
                    # Said in the window, with the TV's name: which TV was asked, and what it answered.
                    $script:Notice = "◉ $(Name-Of $tv): $answer"
                }
            })
            [void]$row.Children.Add($ident)
        } else {
            $remove = Small-Button "✕" (W "removeTv")
            $remove.Tag = $t
            $remove.Add_Click({
                param($s, $e)
                $tv = $s.Tag
                $r = Show-Dialog "Awan TV" @(@{ Text = [string]::Format((W "removeAsk"), (Name-Of $tv)) }) "ask" @((W "removeTvBtn"), (W "cancel"))
                if ($r -ne 0) { return }
                $script:Registry.Remove($tv.Id); Save-Registry
                $script:Tvs = @($script:Tvs | Where-Object { $_.Id -ne $tv.Id })
                Fill-Targets; Update-Chrome
            })
            [void]$row.Children.Add($remove)
            # A TV reached by its network address (ip:port) can be called again from here.
            if ($t.Address -match "^\d{1,3}(\.\d{1,3}){3}:\d+$") {
                $connect = Small-Button (W "connect") $t.Address
                $connect.Tag = $t
                $connect.Add_Click({
                    param($s, $e)
                    $tv = $s.Tag
                    Busy {
                        $old = $ErrorActionPreference; $ErrorActionPreference = "Continue"
                        $out = & $script:Adb connect $tv.Address 2>&1 | Out-String
                        $ErrorActionPreference = $old
                        Log "adb connect $($tv.Address): $($out.Trim())"
                        Refresh-Tvs
                        if (-not (@($script:Tvs | Where-Object { $_.Id -eq $tv.Id -and $_.Online }).Count)) { throw ([string]::Format((W "connectFail"), $tv.Address)) }
                    }
                })
                [void]$row.Children.Add($connect)
            }
        }
        $box = New-Object Windows.Controls.CheckBox
        $box.VerticalAlignment = "Center"; $box.IsChecked = $t.Selected; $box.IsEnabled = $t.Online; $box.Tag = $t
        $text = New-Object Windows.Controls.StackPanel
        $l1 = New-Object Windows.Controls.TextBlock; $l1.Text = $title; $l1.FontSize = 13
        $l2 = New-Object Windows.Controls.TextBlock; $l2.FontSize = 11; $l2.Foreground = Brush "#5F5F5F"
        if (-not $t.Online) {
            $l2.Text = "$($t.Id) · " + [string]::Format((W "lastSeen"), $t.Seen)
            $box.Opacity = 0.5
        }
        elseif ($script:SourceVersion -and (Version-Less $t.Version $script:SourceVersion)) { $l2.Text = "$($t.Id) · v$($t.Version) · $(W 'older')"; $l2.Foreground = Brush $Gold }
        else { $l2.Text = "$($t.Id) · v$($t.Version)" }
        [void]$text.Children.Add($l1); [void]$text.Children.Add($l2)
        $box.Content = $text
        $box.Add_Click({ param($s, $e) $s.Tag.Selected = [bool]$s.IsChecked; Update-Chrome })
        [void]$row.Children.Add($box)
        [void]$ui.TargetsList.Children.Add($row)
    }
}

# After a name changed: the shown names (and alike ones) again, without asking adb.
function Refresh-Names {
    $chosen = @{}
    foreach ($t in $script:Tvs) { $chosen[$t.Id] = $t.Selected; $t.Name = $script:Registry[$t.Id].name }
    foreach ($t in $script:Tvs) { $t.Display = if ($t.Name) { $t.Name } elseif ($t.DeviceName) { $t.DeviceName } else { $t.Model } }
    foreach ($g in @($script:Tvs | Group-Object Display | Where-Object { $_.Count -gt 1 })) {
        foreach ($t in $g.Group) { $t.Display = "$($t.Display) · …" + $t.Id.Substring([Math]::Max(0, $t.Id.Length - 3)) }
    }
    foreach ($item in $ui.SourceBox.Items) { $item.Content = "$(Name-Of $item.Tag) · v$($item.Tag.Version)" }
    Fill-Targets; Update-Chrome
}

# ---------------------------------------------------------------- the rows, built from the description

function Visible($it) {
    if ($it.view) {
        $parts = $it.view -split ":"
        if ($script:Views[$parts[0]] -ne $parts[1]) { return $false }
    }
    if ($it.when) {
        $now = $script:Values[$it.when.key]
        if ($null -eq $now -and $script:Items[$it.when.key]) { $now = $script:Items[$it.when.key].default }
        $list = if ($it.when.in) { @($it.when.in) } else { @($it.when.not) }
        $hit = @($list | Where-Object { Same $_ $now }).Count -gt 0
        if ($it.when.in) { return $hit } else { return (-not $hit) }
    }
    return $true
}

function Row([string]$label, [string]$help, $control, [bool]$changed) {
    $card = New-Object Windows.Controls.Border
    $card.Background = Brush "#FFFFFF"; $card.BorderBrush = Brush "#E5E5E5"; $card.BorderThickness = 1; $card.CornerRadius = 6
    $card.Padding = "16,8"; $card.Margin = "0,0,0,4"; $card.MinHeight = 58
    $dock = New-Object Windows.Controls.DockPanel
    $mark = New-Object Windows.Controls.Border
    $mark.Width = 3; $mark.CornerRadius = 2; $mark.Margin = "0,0,10,0"
    $mark.Background = if ($changed) { Brush $Gold } else { [Windows.Media.Brushes]::Transparent }
    [Windows.Controls.DockPanel]::SetDock($mark, "Left"); [void]$dock.Children.Add($mark)
    if ($control) {
        $control.VerticalAlignment = "Center"; $control.Margin = "16,0,0,0"
        [Windows.Controls.DockPanel]::SetDock($control, "Right"); [void]$dock.Children.Add($control)
    }
    $words = New-Object Windows.Controls.StackPanel
    $words.VerticalAlignment = "Center"
    $t1 = New-Object Windows.Controls.TextBlock; $t1.Text = $label; $t1.TextWrapping = "Wrap"
    [void]$words.Children.Add($t1)
    if ($help) {
        $t2 = New-Object Windows.Controls.TextBlock; $t2.Text = $help; $t2.FontSize = 12; $t2.Foreground = Brush "#5F5F5F"; $t2.TextWrapping = "Wrap"
        [void]$words.Children.Add($t2)
    }
    [void]$dock.Children.Add($words)
    $card.Child = $dock
    return $card
}

# Segmented buttons for up to four short choices, a list for the rest.
function Choice-Control([object[]]$values, [string[]]$labels, $current, [scriptblock]$onPick) {
    $short = $values.Count -le 4 -and (@($labels | Where-Object { $_.Length -gt 14 }).Count -eq 0)
    if ($short) {
        $border = New-Object Windows.Controls.Border
        $border.BorderBrush = Brush "#D1D1D1"; $border.BorderThickness = 1; $border.CornerRadius = 6; $border.ClipToBounds = $true
        $panel = New-Object Windows.Controls.StackPanel; $panel.Orientation = "Horizontal"
        $group = [guid]::NewGuid().ToString()
        for ($i = 0; $i -lt $values.Count; $i++) {
            $rb = New-Object Windows.Controls.RadioButton
            $rb.Style = $window.FindResource("Seg"); $rb.GroupName = $group; $rb.Content = $labels[$i]; $rb.FontSize = 13
            $rb.IsChecked = (Same $values[$i] $current)
            $rb.Tag = @{ V = $values[$i]; Pick = $onPick }
            $rb.Add_Checked({ param($s, $e) & $s.Tag.Pick $s.Tag.V })
            [void]$panel.Children.Add($rb)
        }
        $border.Child = $panel
        return $border
    }
    $cb = New-Object Windows.Controls.ComboBox
    for ($i = 0; $i -lt $values.Count; $i++) {
        $ci = New-Object Windows.Controls.ComboBoxItem; $ci.Content = $labels[$i]; $ci.Tag = $values[$i]
        [void]$cb.Items.Add($ci)
        if (Same $values[$i] $current) { $cb.SelectedIndex = $i }
    }
    $cb.Tag = $onPick
    $cb.Add_SelectionChanged({ param($s, $e) if ($s.SelectedItem) { & $s.Tag $s.SelectedItem.Tag } })
    return $cb
}

function Set-Value([string]$key, $value) {
    $script:Values[$key] = $value
    # Rows that show by this value (the mode's rows), and the marks, follow at once.
    $window.Dispatcher.BeginInvoke([Action]{ Show-Section $script:Section }) | Out-Null
}

function Control-For($it) {
    $key = $it.key
    $v = $script:Values[$key]
    if ($null -eq $v) { $v = $it.default }
    if ($it.readonly -or $it.kind -eq "city" -or $it.kind -eq "string") {
        $tb = New-Object Windows.Controls.TextBlock
        $shown = if ($it.kind -eq "city") { $v.name } else { "$v" }
        $tb.Text = "$shown  ·  $(W 'readonly')"; $tb.Foreground = Brush "#5F5F5F"
        return $tb
    }
    switch ($it.kind) {
        "bool" {
            $sw = New-Object Windows.Controls.CheckBox
            $sw.Style = $window.FindResource("Switch"); $sw.IsChecked = [bool]$v; $sw.Tag = $key
            $sw.Add_Click({ param($s, $e) Set-Value $s.Tag ([bool]$s.IsChecked) })
            return $sw
        }
        "choice" {
            $k = $key
            return (Choice-Control @($it.values) @($it.labels) $v ([scriptblock]::Create("param(`$x) Set-Value '$k' `$x")))
        }
        "range" {
            $panel = New-Object Windows.Controls.StackPanel; $panel.Orientation = "Horizontal"
            $minus = New-Object Windows.Controls.Button; $minus.Content = "−"; $minus.Style = $window.FindResource("Btn"); $minus.Padding = "10,2"
            $plus = New-Object Windows.Controls.Button; $plus.Content = "+"; $plus.Style = $window.FindResource("Btn"); $plus.Padding = "10,2"
            $num = New-Object Windows.Controls.TextBlock; $num.Text = "$v"; $num.MinWidth = 44; $num.TextAlignment = "Center"; $num.VerticalAlignment = "Center"
            $minus.Tag = @{ K = $key; D = -1; Min = [int]$it.min; Max = [int]$it.max }
            $plus.Tag = @{ K = $key; D = 1; Min = [int]$it.min; Max = [int]$it.max }
            $step = {
                param($s, $e)
                $t = $s.Tag; $now = $script:Values[$t.K]; if ($null -eq $now) { $now = $script:Items[$t.K].default }
                $n = [Math]::Max($t.Min, [Math]::Min($t.Max, [int]$now + $t.D))
                Set-Value $t.K $n
            }
            $minus.Add_Click($step); $plus.Add_Click($step)
            [void]$panel.Children.Add($minus); [void]$panel.Children.Add($num); [void]$panel.Children.Add($plus)
            $panel.FlowDirection = "LeftToRight"
            return $panel
        }
        "time" {
            # Hours and five-minute steps, inside the setting's range; "—" where no time is allowed.
            $panel = New-Object Windows.Controls.StackPanel; $panel.Orientation = "Horizontal"; $panel.FlowDirection = "LeftToRight"
            $hh = New-Object Windows.Controls.ComboBox; $hh.MinWidth = 70
            $mm = New-Object Windows.Controls.ComboBox; $mm.MinWidth = 70; $mm.Margin = "4,0,0,0"
            $allowNone = ("$($it.default)" -eq "")
            if ($allowNone) { [void]$hh.Items.Add((W "noneTime")) }
            $lo = [int]$it.min; $hi = [int]$it.max
            for ($h = [Math]::Floor($lo / 60); $h -le [Math]::Floor($hi / 60); $h++) { [void]$hh.Items.Add(("{0:00}" -f $h)) }
            for ($m = 0; $m -lt 60; $m += 5) { [void]$mm.Items.Add(("{0:00}" -f $m)) }
            if ("$v" -match "^(\d{1,2}):(\d{2})$") {
                $hh.SelectedItem = ("{0:00}" -f [int]$Matches[1])
                $five = [int]([Math]::Round([int]$Matches[2] / 5) * 5) % 60
                $mm.SelectedItem = ("{0:00}" -f $five)
            } else { $hh.SelectedIndex = 0; $mm.SelectedIndex = 0; $mm.IsEnabled = -not $allowNone }
            $tag = @{ K = $key; H = $hh; M = $mm; Lo = $lo; Hi = $hi }
            $hh.Tag = $tag; $mm.Tag = $tag
            $pick = {
                param($s, $e)
                $t = $s.Tag
                if ("$($t.H.SelectedItem)" -eq (W "noneTime")) { Set-Value $t.K ""; return }
                if (-not $t.M.SelectedItem) { $t.M.SelectedIndex = 0 }
                $total = [int]$t.H.SelectedItem * 60 + [int]$t.M.SelectedItem
                $total = [Math]::Max($t.Lo, [Math]::Min($t.Hi, $total))
                Set-Value $t.K ("{0:00}:{1:00}" -f [Math]::Floor($total / 60), ($total % 60))
            }
            $hh.Add_SelectionChanged($pick); $mm.Add_SelectionChanged($pick)
            [void]$panel.Children.Add($hh); [void]$panel.Children.Add($mm)
            return $panel
        }
        "list" { return (List-Control $it @($v)) }
    }
    return $null
}

# Home's apps by name: order with ▲▼, remove, add from the TV's apps.
function List-Control($it, [string[]]$current) {
    $names = @{}
    foreach ($a in @($script:Schema.apps)) { $names[$a.pkg] = $a.name }
    $panel = New-Object Windows.Controls.StackPanel; $panel.MinWidth = 380
    for ($i = 0; $i -lt $current.Count; $i++) {
        $pkg = $current[$i]
        $line = New-Object Windows.Controls.DockPanel; $line.Margin = "0,1"
        foreach ($op in @(@("remove", "✕"), @("down", (W "down")), @("up", (W "up")))) {
            $b = New-Object Windows.Controls.Button; $b.Content = $op[1]; $b.Style = $window.FindResource("Btn"); $b.Padding = "8,1"; $b.Margin = "2,0"
            $b.Tag = @{ K = $it.key; I = $i; Op = $op[0] }
            $b.Add_Click({
                param($s, $e)
                $t = $s.Tag; $list = New-Object Collections.ArrayList(,@($script:Values[$t.K]))
                if ($t.Op -eq "remove") { $list.RemoveAt($t.I) }
                elseif ($t.Op -eq "up" -and $t.I -gt 0) { $x = $list[$t.I]; $list.RemoveAt($t.I); $list.Insert($t.I - 1, $x) }
                elseif ($t.Op -eq "down" -and $t.I -lt $list.Count - 1) { $x = $list[$t.I]; $list.RemoveAt($t.I); $list.Insert($t.I + 1, $x) }
                Set-Value $t.K @($list | ForEach-Object { [string]$_ })
            })
            [Windows.Controls.DockPanel]::SetDock($b, "Right"); [void]$line.Children.Add($b)
        }
        $label = New-Object Windows.Controls.TextBlock; $label.VerticalAlignment = "Center"
        $label.Text = if ($names[$pkg]) { "$($i + 1). $($names[$pkg])" } else { "$($i + 1). $pkg" }
        $label.ToolTip = $pkg
        [void]$line.Children.Add($label)
        [void]$panel.Children.Add($line)
    }
    $add = New-Object Windows.Controls.ComboBox; $add.Margin = "0,4,0,0"
    $first = New-Object Windows.Controls.ComboBoxItem; $first.Content = W "add"; [void]$add.Items.Add($first); $add.SelectedIndex = 0
    foreach ($a in @($script:Schema.apps)) {
        if ($current -contains $a.pkg) { continue }
        $ci = New-Object Windows.Controls.ComboBoxItem; $ci.Content = $a.name; $ci.Tag = $a.pkg; [void]$add.Items.Add($ci)
    }
    $add.Tag = $it.key
    $add.Add_SelectionChanged({
        param($s, $e)
        if (-not $s.SelectedItem -or -not $s.SelectedItem.Tag) { return }
        Set-Value $s.Tag (@($script:Values[$s.Tag]) + @([string]$s.SelectedItem.Tag))
    })
    [void]$panel.Children.Add($add)
    return $panel
}

$script:Section = 0
function Show-Section([int]$index) {
    $script:Section = $index
    $ui.Rows.Children.Clear()
    if (-not $script:Schema) { Update-Chrome; return }
    $sec = $script:Schema.sections[$index]
    $head = New-Object Windows.Controls.TextBlock; $head.Text = $sec.label; $head.FontSize = 20; $head.FontWeight = "SemiBold"; $head.Margin = "0,0,0,12"
    [void]$ui.Rows.Children.Add($head)
    $viewShown = @{}
    $combosShown = @{}
    foreach ($it in $sec.items) {
        # A view switch (Iqamah: other days / Ramadan) before the first row that belongs to one side of it.
        if ($it.view) {
            $vname = ($it.view -split ":")[0]
            if (-not $viewShown[$vname] -and $script:Schema.views.$vname) {
                $viewShown[$vname] = $true
                $vd = $script:Schema.views.$vname
                $mode = $script:Values["iqama_mode"]
                if ($vname -ne "iqama" -or ($null -eq $mode) -or [int]$mode -ne 0) {
                    $n = $vname
                    $ctl = Choice-Control @($vd.values) @($vd.labels) $script:Views[$vname] ([scriptblock]::Create("param(`$x) `$script:Views['$n'] = `$x; Show-Section `$script:Section"))
                    [void]$ui.Rows.Children.Add((Row $vd.label "" $ctl $false))
                }
            }
        }
        if (-not (Visible $it)) { continue }
        if ($it.combo) {
            # Two settings, one row (the screensaver's middle), as on the TV.
            if ($combosShown[$it.combo]) { continue }
            $combosShown[$it.combo] = $true
            $cd = $script:Schema.combos.($it.combo)
            $keys = @($cd.keys)
            $now = @($keys | ForEach-Object { $x = $script:Values[$_]; if ($null -eq $x) { $script:Items[$_].default } else { $x } })
            $vals = @(); $cur = $null
            for ($i = 0; $i -lt $cd.values.Count; $i++) {
                $pair = @($cd.values[$i]); $vals += $i
                if ((Same $pair[0] $now[0]) -and (Same $pair[1] $now[1])) { $cur = $i }
            }
            $cname = $it.combo
            $ctl = Choice-Control $vals @($cd.labels) $cur ([scriptblock]::Create(@"
param(`$x)
`$cd = `$script:Schema.combos.'$cname'; `$pair = @(`$cd.values[`$x]); `$keys = @(`$cd.keys)
for (`$j = 0; `$j -lt `$keys.Count; `$j++) { `$script:Values[`$keys[`$j]] = [bool]`$pair[`$j] }
Show-Section `$script:Section
"@))
            $changed = @($keys | Where-Object { -not (Same $script:Values[$_] $script:Orig[$_]) }).Count -gt 0
            [void]$ui.Rows.Children.Add((Row $cd.label $cd.help $ctl $changed))
            continue
        }
        $help = $it.help
        if ($it.note) { $help = if ($help) { "$help · $($it.note)" } else { $it.note } }
        $changed = -not (Same $script:Values[$it.key] $script:Orig[$it.key])
        [void]$ui.Rows.Children.Add((Row $it.label $help (Control-For $it) $changed))
    }
    Update-Chrome
}

function Fill-Nav {
    $ui.Nav.Items.Clear()
    foreach ($sec in $script:Schema.sections) {
        $li = New-Object Windows.Controls.ListBoxItem; $li.Content = $sec.label; $li.Padding = "12,8"; $li.FontSize = 14
        [void]$ui.Nav.Items.Add($li)
    }
    $ui.Nav.SelectedIndex = [Math]::Min($script:Section, $ui.Nav.Items.Count - 1)
}

function Loaded {
    $script:Views = @{ iqama = "days" }
    # New settings shown: no TV chosen to send to, so nothing is sent by mistake; the viewer chooses each time.
    foreach ($t in $script:Tvs) { $t.Selected = $false }
    Fill-Nav
    Show-Section $ui.Nav.SelectedIndex
    Fill-Targets
}

# ---------------------------------------------------------------- actions

$ui.Nav.Add_SelectionChanged({ if ($ui.Nav.SelectedIndex -ge 0) { Show-Section $ui.Nav.SelectedIndex } })
$ui.RefreshBtn.Add_Click({ Busy { Refresh-Tvs } })
$ui.SourceBox.Add_SelectionChanged({ Update-Chrome })

$ui.GetBtn.Add_Click({
    Busy {
        $tv = $ui.SourceBox.SelectedItem.Tag
        $file = Join-Path $SettingsDir ("awantv-settings-$($tv.Id)-$(Stamp).jsonc")
        $got = Export-Tv $tv $file
        Use-Settings (Read-Settings $got.File) ("$(Name-Of $tv) · v$($tv.Version) · $(Get-Date -Format 'HH:mm')")
        Log "Got the settings from $($tv.Id) (v$($tv.Version)) into $file"
        Loaded
    }
})

function Open-From([string]$dir, [string]$filter) {
    $dlg = New-Object Microsoft.Win32.OpenFileDialog
    $dlg.InitialDirectory = $dir
    $dlg.Filter = $filter
    if ($dlg.ShowDialog($window)) {
        Busy {
            Use-Settings (Read-Settings $dlg.FileName) ([IO.Path]::GetFileName($dlg.FileName))
            Log "Opened $($dlg.FileName)"
            Loaded
        }
    }
}
$ui.OpenBtn.Add_Click({ Open-From $SettingsDir "Awan TV (*.jsonc)|*.jsonc" })

# ع / EN: the whole window in the other language, the settings' names and help too; the values stay as they are.
$ui.LangBtn.Add_Click({
    $script:Lang = if ($script:Lang -eq "ar") { "en" } else { "ar" }
    Write-Utf8 $EditorConfig (@{ lang = $script:Lang } | ConvertTo-Json)
    Log "Language: $($script:Lang)"
    if ($script:SourceVersion) {
        $schema = Load-Schema $script:SourceVersion
        if ($schema) {
            $script:Schema = $schema
            $script:Items = @{}
            foreach ($sec in $schema.sections) { foreach ($it in $sec.items) { $script:Items[$it.key] = $it } }
            Fill-Nav
            Show-Section $script:Section
        }
    }
    if ($script:Registry) { Save-Registry }
    Refresh-Names
    Update-Chrome
})
$ui.RestoreBtn.Add_Click({ Open-From $SettingsDir "$(W 'backups')|awantv-backup-*.jsonc;awantv-before-reset-*.jsonc|Awan TV (*.jsonc)|*.jsonc" })

$ui.SaveBtn.Add_Click({
    $dlg = New-Object Microsoft.Win32.SaveFileDialog
    $dlg.InitialDirectory = $SettingsDir
    $dlg.FileName = "awantv-settings-edited-$(Stamp).jsonc"
    $dlg.Filter = "Awan TV (*.jsonc)|*.jsonc"
    if ($dlg.ShowDialog($window)) {
        Busy {
            Write-Utf8 $dlg.FileName (Build-File $null)
            foreach ($k in @($script:Values.Keys)) { $script:Orig[$k] = $script:Values[$k] }
            Log "Saved $($dlg.FileName)"
            Show-Section $script:Section
        }
    }
})

$ui.SendBtn.Add_Click({
    Busy {
        $results = @()
        $n = 0
        foreach ($tv in @($script:Tvs | Where-Object { $_.Online -and $_.Selected })) {
            $n++
            $name = Name-Of $tv
            try {
                # The TV's own settings first, so a send can be undone (Restore a copy).
                $backup = Join-Path $SettingsDir ("awantv-backup-$($tv.Id)-$(Stamp).jsonc")
                $got = Export-Tv $tv $backup
                # A TV with an older version: only what it knows (its own description), the rest named as skipped.
                $only = $null; $skipped = 0
                if ($got.Schema) {
                    $ts = [IO.File]::ReadAllText($got.Schema, [Text.Encoding]::UTF8) | ConvertFrom-Json
                    $only = @(foreach ($sec in $ts.sections) { foreach ($it in $sec.items) { $it.key } })
                    $skipped = @($script:Values.Keys | Where-Object { $only -notcontains $_ }).Count
                }
                $payload = Join-Path $EditorDir "send.jsonc"
                Write-Utf8 $payload (Build-File $only)
                [void](Shell $tv.Serial "mkdir -p $Files")
                [void](Adb $tv.Serial @("push", $payload, $Remote))
                try { $answer = Tv-Words (Send-Action $tv.Serial "$Pkg.IMPORT") } finally { [void](Shell $tv.Serial "rm -f $Remote") }
                if ($skipped -gt 0) { $answer += "`n" + [string]::Format((W "skipped"), $skipped) }
                $results += @{ Title = "$n. $name"; Text = $answer }
                Log "Sent to $($tv.Id): $answer (backup $backup)"
            } catch {
                $results += @{ Title = "$n. $name"; Text = $_.Exception.Message }
                Log "Send to $($tv.Id) failed: $($_.Exception.Message)"
            }
        }
        foreach ($k in @($script:Values.Keys)) { $script:Orig[$k] = $script:Values[$k] }
        Show-Section $script:Section
        [void](Show-Dialog (W "sent") $results "info" @((W "okBtn")))
    }
})

$ui.TargetsBtn.Add_Click({ $ui.TargetsPop.IsOpen = [bool]$ui.TargetsBtn.IsChecked; if ($ui.TargetsPop.IsOpen) { Fill-Targets } })
$ui.TargetsPop.Add_Closed({ $ui.TargetsBtn.IsChecked = $false })
$ui.SearchBox.Add_TextChanged({ Fill-Targets })
$ui.AllBtn.Add_Click({ foreach ($t in $script:Tvs) { if ($t.Online) { $t.Selected = $true } }; Fill-Targets; Update-Chrome })
$ui.NoneBtn.Add_Click({ foreach ($t in $script:Tvs) { $t.Selected = $false }; Fill-Targets; Update-Chrome })

$window.Add_Closing({
    param($s, $e)
    if ((Changed-Count) -gt 0) {
        $r = Show-Dialog "Awan TV" @(@{ Text = (W "unsaved") }) "ask" @((W "leaveBtn"), (W "backBtn"))
        if ($r -ne 0) { $e.Cancel = $true }
    }
})

# A second start asked for this window: bring it to the front.
$timer = New-Object Windows.Threading.DispatcherTimer
$timer.Interval = [TimeSpan]::FromMilliseconds(500)
$timer.Add_Tick({
    if ($script:ShowSignal.WaitOne(0)) {
        if ($window.WindowState -eq "Minimized") { $window.WindowState = "Normal" }
        [void]$window.Activate(); $window.Topmost = $true; $window.Topmost = $false
    }
})
$timer.Start()

$window.Add_ContentRendered({ Busy { Refresh-Tvs } })
Update-Chrome
[void]$window.ShowDialog()
$timer.Stop()
$script:Mutex.ReleaseMutex()
Log "Closed"
