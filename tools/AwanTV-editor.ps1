# AwanTV-editor.ps1 - Version 1.2.2
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
# TVs now (first in the list, Awan TV 1.10.0 on the TV): each connected TV's radio, with Play or Stop beside it, and
# Play / Stop on the TVs ticked there (kept in state\editor\now.json, none at first: a TV someone else uses is never
# played by surprise). Play uses the station chosen on that TV (changed on the Radio page, then Send), and does not
# cut in on a TV playing sound from another app (Android gives sound to one: a video would stop) unless asked.

$ErrorActionPreference = "Stop"
$ScriptVersion = "1.2.2"
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
        libTitle = "التلاوات المحفوظة"; storage = "المساحة على {0}"; storageLine = "فارغ {0} من {1} · يُترك {2} للتلفاز دائمًا"; gb = "{0} جيجابايت"
        onTv = "على هذه الشاشة"; noneSaved = "لا تلاوات محفوظة عليها بعد"; complete = "كاملة: {0} سورة"; partial = "{0} سورة من {1}"; partialNoOf = "{0} سورة"
        downloading = "يُحمَّل الآن: السورة {0}"; downloadingPct = "يُحمَّل الآن: السورة {0} ({1}%)"; queuedTag = "في قائمة التحميل: {0} سورة"; nowTag = "يُحمَّل"
        waitingSpace = "التحميل متوقف: بقي في الشاشة أقل من {0} فارغة"; broken = "مدير التحميل في الشاشة رفض آخر تحميل (قد يكون مطفأً فيها)"
        pendingHead = "يُرسل للتحميل مع «إرسال»"; pendingNote = "تبدأ كل شاشة مختارة في «إرسال إلى» التحميل بنفسها. الحذف من الشاشة نفسها: الإعدادات ← الإذاعة ← التلاوات المحفوظة."
        addRec = "+ إضافة تلاوة…"; wholeQuran = "القرآن كاملًا: {0} سورة"; someSurahs = "{0} سورة: {1}"; noPending = "لا شيء بعد"
        fileSource = "المصدر ملف: ما يُضاف هنا يُرسل مع «إرسال»، وقائمة القرّاء من آخر شاشة جُلبت منها الإعدادات."
        noLibrary = "لا توجد قائمة قرّاء على هذا الحاسب بعد: اجلب الإعدادات من شاشة عليها Awan TV 1.9.0 أو أحدث."
        noList = "قائمة القرّاء لم تُحفظ على الشاشة بعد، وهي تقرؤها الآن من mp3quran.net: اجلب الإعدادات مرة أخرى بعد دقيقة."
        addTitle = "إضافة تلاوة"; stepReciter = "1. القارئ"; stepMoshaf = "2. المصحف"; stepWhat = "3. ما يُحمَّل"; allSurahs = "القرآن كاملًا: {0} سورة"; someChosen = "سور مختارة"
        listFrom = "قائمة القرّاء من mp3quran.net كما تحفظها الشاشة"; addBtn = "إضافة"; pickSome = "اختر سورة واحدة على الأقل."; searchReciter = "ابحث عن قارئ"
        gStations = "المحطات"; gReciters = "إذاعات القرّاء (mp3quran.net)"; gSaved = "التلاوات المحفوظة على {0}"; gComing = "تُرسل للتحميل الآن"; savedMark = "محفوظة"; gSavedShort = "المحفوظة"; gRecitersShort = "إذاعات القرّاء"; comingMark = "تُحمَّل مع الإرسال"
        noSettingChanged = "لم يتغيّر أي إعداد"; queuedSent = "أُضيف إلى قائمة التحميل: {0} سورة من {1} تلاوة"; queuedNone = "ما أُرسل محفوظ عليها أو في قائمة تحميلها من قبل"
        keptSaved = "بقيت محطتها: «{0}» غير محفوظة عليها"; keptChannels = "بقيت محطتها: قائمة إذاعات القرّاء لم تُحفظ عليها بعد (تقرؤها الآن؛ أعد الإرسال بعد دقيقة)"; keptUnknown = "بقيت محطتها: «{0}» غير موجودة عليها"
        olderNoLibrary = "على نسخة أقدم من 1.9.0: لم تُرسل إليها التلاوات ولا المحطة (حدّثها)"
        nowTitle = "الشاشات الآن"; nowRefresh = "⟳ تحديث الحالة"; playChosen = "▶ تشغيل في المحددة ({0})"; stopChosen = "■ إيقاف في المحددة ({0})"; playChosenTitle = "تشغيل في المحددة ({0})"; stopChosenTitle = "إيقاف في المحددة ({0})"
        chooseNote = "حدّد الشاشات التي يشملها «تشغيل في المحددة» و«إيقاف في المحددة». يُحفظ تحديدك على هذا الحاسب للمرة القادمة."
        busyHead = "يصدر منها صوت الآن من تطبيق آخر (فيديو أو موسيقى)، فلم تُشغَّل:"; busyPick = "حدّد ما تريد تشغيله رغم ذلك: يتوقف ما يُعرض عليها بالصورة والصوت."
        playTicked = "شغّل المحددة رغم ذلك"; playTickedN = "شغّل المحددة رغم ذلك ({0})"; playTickedTitle = "تشغيل رغم ذلك ({0})"
        nowNote = "الإذاعة على كل شاشة متصلة، بمحطتها المختارة فيها (تُغيَّر من صفحة «الإذاعة» ثم «إرسال»). «تشغيل» لا يقاطع شاشة يصدر منها صوت الآن إلا إن طلبت ذلك."
        nowAt = "الحالة كما كانت عند {0}"; nowUnknown = "اضغط «تحديث الحالة» لترى ما تبثه."; play = "▶ تشغيل"; stop = "■ إيقاف"
        radioOn = "● الإذاعة تعمل: {0}"; radioOff = "○ الإذاعة متوقفة · المحطة: {0}"; otherSound = "◐ يصدر منها صوت الآن من تطبيق آخر · المحطة: {0}"
        nowOlder = "على نسخة أقدم من 1.10.0: حدّثها لتتحكم في إذاعتها من هنا"; nowOlderShort = "على نسخة أقدم من 1.10.0"; noAwan = "Awan TV غير مثبّت عليها"; noAnswer = "لم تُجب: {0}"
        busyText = "يصدر منها صوت الآن من تطبيق آخر (فيديو أو موسيقى)، فلم تُشغَّل الإذاعة."; busyNote = "إن شغّلتها رغم ذلك، يتوقف ما يُعرض عليها بالصورة والصوت."
        playAnyway = "شغّل رغم ذلك"
        started = "بدأت الإذاعة ({0})"; alreadyOn = "تعمل من قبل"; stopped = "توقفت الإذاعة"; alreadyOff = "متوقفة من قبل"; busyShort = "يصدر منها صوت من تطبيق آخر، فلم تُشغَّل"
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
        libTitle = "Saved recitations"; storage = "Storage on {0}"; storageLine = "{0} free of {1} · {2} always left for the TV"; gb = "{0} GB"
        onTv = "On this TV"; noneSaved = "No saved recitations on it yet"; complete = "Complete: {0} surahs"; partial = "{0} of {1} surahs"; partialNoOf = "{0} surahs"
        downloading = "Downloading surah {0}"; downloadingPct = "Downloading surah {0} ({1}%)"; queuedTag = "Queued: {0} surahs"; nowTag = "Downloading"
        waitingSpace = "Downloads paused: less than {0} left free on the TV"; broken = "The TV's download manager refused the last download (it may be turned off there)"
        pendingHead = "To download with Send"; pendingNote = "Each TV chosen in Send to downloads them by itself. Deleting stays on the TV: Settings > Radio > Saved recitations."
        addRec = "+ Add a recitation…"; wholeQuran = "The whole Quran: {0} surahs"; someSurahs = "{0} surahs: {1}"; noPending = "Nothing yet"
        fileSource = "The source is a file: what you add here goes with Send, and the reciter list is the last TV's you got settings from."
        noLibrary = "No reciter list on this computer yet: get the settings from a TV with Awan TV 1.9.0 or later."
        noList = "The TV has no reciter list yet and is reading it from mp3quran.net now: get the settings again in a minute."
        addTitle = "Add a recitation"; stepReciter = "1. Reciter"; stepMoshaf = "2. Recording"; stepWhat = "3. What to download"; allSurahs = "The whole Quran: {0} surahs"; someChosen = "Chosen surahs"
        listFrom = "mp3quran.net's reciter list as the TV keeps it"; addBtn = "Add"; pickSome = "Choose at least one surah."; searchReciter = "Search for a reciter"
        gStations = "Stations"; gReciters = "Reciter channels (mp3quran.net)"; gSaved = "Saved on {0}"; gComing = "Downloading with this Send"; savedMark = "saved"; gSavedShort = "Saved"; gRecitersShort = "Reciters"; comingMark = "downloads with Send"
        noSettingChanged = "No setting changed"; queuedSent = "Queued {0} surah(s) of {1} recitation(s)"; queuedNone = "What was sent is already saved or queued there"
        keptSaved = "Kept its station: {0} is not saved there"; keptChannels = "Kept its station: it has no reciter channel list yet (reading it now; send again in a minute)"; keptUnknown = "Kept its station: {0} is not there"
        olderNoLibrary = "On a version before 1.9.0: recitations and the station were not sent (update it)"
        nowTitle = "TVs now"; nowRefresh = "⟳ Refresh status"; playChosen = "▶ Play on chosen ({0})"; stopChosen = "■ Stop on chosen ({0})"; playChosenTitle = "Play on chosen ({0})"; stopChosenTitle = "Stop on chosen ({0})"
        chooseNote = "Tick the TVs that Play on chosen and Stop on chosen include. Your choice is kept on this computer for next time."
        busyHead = "Another app is playing sound on these now (a video or music), so they were not started:"; busyPick = "Tick the ones to play anyway: what they are showing stops, picture and sound."
        playTicked = "Play on ticked anyway"; playTickedN = "Play on ticked anyway ({0})"; playTickedTitle = "Played anyway ({0})"
        nowNote = "The radio on each connected TV, at the station chosen on it (changed on the Radio page, then Send). Play does not cut in on a TV playing sound now unless you ask."
        nowAt = "Status as of {0}"; nowUnknown = "Press Refresh status to see what it is playing."; play = "▶ Play"; stop = "■ Stop"
        radioOn = "● The radio is playing: {0}"; radioOff = "○ The radio is stopped · station: {0}"; otherSound = "◐ Another app is playing sound on it now · station: {0}"
        nowOlder = "On a version before 1.10.0: update it to control its radio from here"; nowOlderShort = "On a version before 1.10.0"; noAwan = "Awan TV is not installed on it"; noAnswer = "Did not answer: {0}"
        busyText = "Another app is playing sound on it now (a video or music), so the radio was not started."; busyNote = "Played anyway, what it is showing stops, picture and sound."
        playAnyway = "Play anyway"
        started = "Radio started ({0})"; alreadyOn = "Already playing"; stopped = "Radio stopped"; alreadyOff = "Already stopped"; busyShort = "Another app is playing sound, so it was not started"
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
    # Since 1.9.0 an import answers in parts, " | " between them: each on its own line.
    $parts = @($answer -split " \| ")
    $out = foreach ($part in $parts) {
        if ($part -match '^Changed (\d+) setting\(s\)$') { [string]::Format((W "changed"), (Settings-Count ([int]$Matches[1]))) }
        elseif ($part -like "Nothing to change*") { W "nothingToChange" }
        elseif ($part -eq "No setting changed") { W "noSettingChanged" }
        elseif ($part -eq "Shown on the TV") { W "shownOnTv" }
        elseif ($part -match '^Queued (\d+) surah\(s\) of (\d+) recitation\(s\)$') {
            if ([int]$Matches[1] -eq 0) { W "queuedNone" } else { [string]::Format((W "queuedSent"), $Matches[1], $Matches[2]) }
        }
        elseif ($part -match '^Station kept: (saved|channels|unknown) (.+)$') {
            $what = Station-Title $Matches[2]
            switch ($Matches[1]) { "saved" { [string]::Format((W "keptSaved"), $what) } "channels" { W "keptChannels" } default { [string]::Format((W "keptUnknown"), $what) } }
        }
        else { $part }
    }
    return (@($out) -join "`n")
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

# "1.0.0" compared part by part; a missing version is the oldest. A test build's letter (1.8.0a, 1.8.0b) comes before
# its release (1.8.0), as in the app (Updates.compare).
function Version-Less([string]$a, [string]$b) {
    if (-not $a) { return $true }
    if (-not $b) { return $false }
    $ma = [regex]::Match($a, "^([\d.]*\d)([a-z]*)"); $mb = [regex]::Match($b, "^([\d.]*\d)([a-z]*)")
    if (-not $ma.Success -or -not $mb.Success) { return ($a -lt $b) }
    $x = @($ma.Groups[1].Value -split "\." | ForEach-Object { [int]$_ }); $y = @($mb.Groups[1].Value -split "\." | ForEach-Object { [int]$_ })
    for ($i = 0; $i -lt [Math]::Max($x.Count, $y.Count); $i++) {
        $p = 0; $q = 0
        if ($i -lt $x.Count) { $p = $x[$i] }
        if ($i -lt $y.Count) { $q = $y[$i] }
        if ($p -ne $q) { return ($p -lt $q) }
    }
    $la = $ma.Groups[2].Value; $lb = $mb.Groups[2].Value
    if ($la -eq $lb) { return $false }
    if (-not $la) { return $false }
    if (-not $lb) { return $true }
    return ($la -lt $lb)
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
    # Its saved recitations and stations (1.9.0 on): kept per TV, for the Saved recitations page and the station list.
    $library = $null
    $remoteLib = "$Files/awantv-library.json"
    if ((Shell $tv.Serial "test -f $remoteLib && echo yes") -eq "yes") {
        $local = Join-Path $EditorDir ("library-$($tv.Id).json")
        [void](Adb $tv.Serial @("pull", $remoteLib, $local))
        [void](Shell $tv.Serial "rm -f $remoteLib")
        if (Test-Path $local) { $library = $local }
    }
    return @{ File = $file; Schema = $schema; Library = $library }
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
$script:Library = $null      # the source TV's awantv-library.json (1.9.0 on): stations, saved recitations, reciter list
$script:LibraryLive = $false # read from the source TV just now (a file shows only the reciter list, from the last TV)
$script:LibrarySource = ""   # the TV it came from, by name
$script:Pending = New-Object Collections.ArrayList   # recordings to download with the next Send: @{ R = recording; Want = surahs or $null }

function Read-Json([string]$path) { return ([IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) | ConvertFrom-Json) }

# A file opened: the reciter list (and stations) of the last TV got from, without its storage or saved ones.
function Last-Library {
    $f = @(Get-ChildItem $EditorDir -Filter "library-*.json" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
    if ($f.Count -eq 0) { return $null }
    try { return (Read-Json $f[0].FullName) } catch { return $null }
}

# Names in the window's language (English ones fall back to Arabic where mp3quran.net gives none).
function Lang-Of($o, [string]$field) {
    $en = [string]$o.("${field}_en"); $ar = [string]$o.("${field}_ar")
    if ($field -eq "") { $en = [string]$o.en; $ar = [string]$o.ar }
    if ($script:Lang -eq "en" -and $en) { return $en }
    return $ar
}
function Rec-Title($r) {
    $name = Lang-Of $r ""
    $moshaf = Lang-Of $r "moshaf"
    if ($moshaf) { return "$name · $moshaf" } else { return $name }
}
# A station id by its name, from the source TV's lists (the TV's answers name the id).
function Station-Title([string]$id) {
    if ($script:Library) {
        foreach ($st in @($script:Library.stations)) { if ($st.id -eq $id) { return (Lang-Of $st "") } }
    }
    foreach ($p in $script:Pending) { if ("saved:$($p.R.key)" -eq $id) { return (Rec-Title $p.R) } }
    return $id
}
function Gb([double]$bytes) { return [string]::Format((W "gb"), [Math]::Round($bytes / 1GB, 1).ToString("0.0")) }
function Has-StationList { return ($script:Library -and @($script:Library.stations).Count -gt 0 -and $script:Items["station"] -and $script:Items["station"].kind -eq "station") }

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
    # Recordings added for another source are not this one's.
    $script:Pending.Clear()
    $script:SourceVersion = $version
    $script:SourceText = $sourceText
}

function Changed-Count {
    $n = 0
    if (-not $script:Values) { return 0 }
    foreach ($k in @($script:Values.Keys)) { if (-not (Same $script:Values[$k] $script:Orig[$k])) { $n++ } }
    return $n + $script:Pending.Count
}

# The file to send or save: the settings, a comment above each from the description, in the TV's order.
# $forFile: a saved recitation as the station stays out (it plays on its own TV only). $downloads: the recordings to
# queue, sent to a TV only (Saved recitations).
function Build-File([string[]]$onlyKeys, [bool]$forFile = $false, [object[]]$downloads = @()) {
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
            if ($forFile -and $it.key -eq "station" -and "$v" -like "saved:*") { continue }
            $json = if ($it.kind -eq "list") { ConvertTo-Json -InputObject @($v) -Compress } else { ConvertTo-Json -InputObject $v -Compress -Depth 5 }
            $body.Add("  // $($it.label)")
            $body.Add("  ""$($it.key)"": $json")
        }
    }
    if ($downloads.Count -gt 0) {
        $list = @(foreach ($d in $downloads) {
            $o = [ordered]@{ id = [int]$d.R.id; moshaf = [int]$d.R.moshaf; ar = [string]$d.R.ar; en = [string]$d.R.en
                moshaf_ar = [string]$d.R.moshaf_ar; moshaf_en = [string]$d.R.moshaf_en; server = [string]$d.R.server; surahs = @($d.R.surahs | ForEach-Object { [int]$_ }) }
            if ($d.Want) { $o.want = @($d.Want | ForEach-Object { [int]$_ }) }
            $o
        })
        $body.Add("  // $(W 'libTitle')")
        $body.Add("  ""downloads"": " + (ConvertTo-Json -InputObject $list -Compress -Depth 5))
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
    if ($it.kind -eq "station" -and (Has-StationList)) { return (Station-Control) }
    if ($it.readonly -or $it.kind -eq "city" -or $it.kind -eq "string" -or $it.kind -eq "station") {
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

# The station as Settings > Radio > Station picks it: first its group (stations, reciter channels, saved), then one of
# that group, from the source TV (awantv-library.json); the recordings added to download with this Send join the saved
# ones. Each TV checks the one sent and keeps its own when it does not have it.
$script:StationGroup = $null   # the group shown, while choosing; $null = the current station's
function Station-Control {
    $cur = $script:Values["station"]
    if ($null -eq $cur) { $cur = [string]$script:Library.station }
    $groups = New-Object Collections.ArrayList
    foreach ($g in @(@("stations", (W "gStations")), @("reciters", (W "gRecitersShort")), @("saved", (W "gSavedShort")))) {
        $items = New-Object Collections.ArrayList
        # A file's source: another TV's saved recordings are not a choice here.
        if ($g[0] -ne "saved" -or $script:LibraryLive) {
            foreach ($st in @($script:Library.stations | Where-Object { $_.group -eq $g[0] })) {
                $text = Lang-Of $st ""
                if ($g[0] -eq "stations") { $where = Lang-Of $st "where"; if ($where) { $text = "$text · $where" } }
                if ($g[0] -eq "saved") { $m = Lang-Of $st "moshaf"; if ($m) { $text = "$text · $m" } }
                [void]$items.Add(@{ Text = $text; Tag = [string]$st.id })
            }
        }
        if ($g[0] -eq "saved") {
            foreach ($p in $script:Pending) {
                $tag = "saved:$($p.R.key)"
                if (@($items | Where-Object { $_.Tag -eq $tag }).Count) { continue }
                [void]$items.Add(@{ Text = (Rec-Title $p.R) + " · " + (W "comingMark"); Tag = $tag })
            }
        }
        if ($items.Count -gt 0) { [void]$groups.Add(@{ Id = $g[0]; Label = $g[1]; Items = $items }) }
    }
    $shown = $script:StationGroup
    if (-not $shown) {
        foreach ($g in $groups) { if (@($g.Items | Where-Object { $_.Tag -eq "$cur" }).Count) { $shown = $g.Id } }
        if (-not $shown -and $groups.Count) { $shown = $groups[0].Id }
    }
    $panel = New-Object Windows.Controls.StackPanel; $panel.Orientation = "Horizontal"
    $ids = @($groups | ForEach-Object { $_.Id }); $labels = @($groups | ForEach-Object { $_.Label })
    $seg = Choice-Control $ids $labels $shown ([scriptblock]::Create("param(`$x) `$script:StationGroup = `$x; Show-Section `$script:Section"))
    $seg.Margin = "0,0,10,0"
    [void]$panel.Children.Add($seg)
    $cb = New-Object Windows.Controls.ComboBox; $cb.MinWidth = 300; $cb.MaxDropDownHeight = 520
    $group = @($groups | Where-Object { $_.Id -eq $shown })
    if ($group.Count) {
        foreach ($it in $group[0].Items) {
            $ci = New-Object Windows.Controls.ComboBoxItem; $ci.Content = $it.Text; $ci.Tag = $it.Tag
            [void]$cb.Items.Add($ci)
            if ($it.Tag -eq "$cur") { $cb.SelectedItem = $ci }
        }
    }
    # The current one in none of the lists (a channel gone from mp3quran.net): shown as it is, in its own group.
    if ($null -eq $cb.SelectedItem -and $cur -and -not $script:StationGroup -and -not (@($groups | ForEach-Object { $_.Items } | Where-Object { $_.Tag -eq "$cur" }).Count)) {
        $ci = New-Object Windows.Controls.ComboBoxItem; $ci.Content = Station-Title $cur; $ci.Tag = $cur
        [void]$cb.Items.Insert(0, $ci); $cb.SelectedItem = $ci
    }
    $cb.Tag = "$cur"
    $cb.Add_SelectionChanged({
        param($s, $e)
        if ($s.SelectedItem -and $s.SelectedItem.Tag -and "$($s.SelectedItem.Tag)" -ne $s.Tag) { Set-Value "station" ([string]$s.SelectedItem.Tag) }
    })
    [void]$panel.Children.Add($cb)
    return $panel
}

function Note-Line([string]$text, [string]$color = "#5F5F5F") {
    $t = New-Object Windows.Controls.TextBlock; $t.Text = $text; $t.TextWrapping = "Wrap"; $t.Foreground = Brush $color; $t.FontSize = 12; $t.Margin = "4,2,4,8"
    [void]$ui.Rows.Children.Add($t)
}
function Head-Line([string]$text, $control = $null) {
    $d = New-Object Windows.Controls.DockPanel; $d.Margin = "0,14,0,6"
    if ($control) { [Windows.Controls.DockPanel]::SetDock($control, "Right"); [void]$d.Children.Add($control) }
    $t = New-Object Windows.Controls.TextBlock; $t.Text = $text; $t.FontWeight = "SemiBold"; $t.VerticalAlignment = "Center"
    [void]$d.Children.Add($t)
    [void]$ui.Rows.Children.Add($d)
}
function Tag-Text([string]$text, [string]$color = "#0C1B33", [string]$back = "#EEF2F8") {
    $b = New-Object Windows.Controls.Border; $b.CornerRadius = 10; $b.Padding = "10,3"; $b.Background = Brush $back
    $t = New-Object Windows.Controls.TextBlock; $t.Text = $text; $t.FontSize = 12; $t.Foreground = Brush $color
    $b.Child = $t
    return $b
}

# Saved recitations: the source TV's storage, what it has, downloads and queue; then what goes with the next Send.
function Show-Library {
    $L = $script:Library
    $head = New-Object Windows.Controls.TextBlock; $head.Text = W "libTitle"; $head.FontSize = 20; $head.FontWeight = "SemiBold"; $head.Margin = "0,0,0,12"
    [void]$ui.Rows.Children.Add($head)
    if (-not $L) { Note-Line (W "noLibrary"); return }
    if ($script:LibraryLive) {
        $bar = New-Object Windows.Controls.ProgressBar; $bar.Width = 260; $bar.Height = 8; $bar.Minimum = 0; $bar.Maximum = 100
        $total = [double]$L.total; $free = [double]$L.free
        if ($total -gt 0) { $bar.Value = [Math]::Round(100 * ($total - $free) / $total) }
        $bar.Foreground = Brush $Navy
        [void]$ui.Rows.Children.Add((Row ([string]::Format((W "storage"), $script:LibrarySource)) ([string]::Format((W "storageLine"), (Gb $free), (Gb $total), (Gb ([double]$L.reserve)))) $bar $false))
        if ($L.waiting_space) { Note-Line ([string]::Format((W "waitingSpace"), (Gb ([double]$L.reserve)))) $Gold }
        if ($L.broken) { Note-Line (W "broken") $Gold }
        Head-Line (W "onTv")
        $rows = 0
        $queued = @{}
        foreach ($q in @($L.queue)) { $queued[$q.key] = [int]$q.count }
        $nowKey = if ($L.current) { [string]$L.current.key } else { "" }
        foreach ($r in @($L.saved)) {
            $tag = if ([int]$r.of -gt 0 -and [int]$r.count -ge [int]$r.of) { [string]::Format((W "complete"), $r.count) }
                   elseif ([int]$r.of -gt 0) { [string]::Format((W "partial"), $r.count, $r.of) } else { [string]::Format((W "partialNoOf"), $r.count) }
            $help = Lang-Of $r "moshaf"
            $color = "#0C1B33"; $back = "#EEF2F8"
            if ($r.key -eq $nowKey) {
                $help = "$help · " + $(if ([int]$L.current.percent -ge 0) { [string]::Format((W "downloadingPct"), $L.current.surah, $L.current.percent) } else { [string]::Format((W "downloading"), $L.current.surah) })
                $tag = W "nowTag"; $color = "#7A5A14"; $back = "#F6EEDC"
            }
            if ($queued.ContainsKey($r.key)) { $help = "$help · " + [string]::Format((W "queuedTag"), $queued[$r.key]); $queued.Remove($r.key) }
            [void]$ui.Rows.Children.Add((Row (Lang-Of $r "") $help (Tag-Text $tag $color $back) $false)); $rows++
        }
        # Downloading or queued and nothing saved yet.
        if ($L.current -and -not (@($L.saved | Where-Object { $_.key -eq $nowKey }).Count)) {
            $help = (Lang-Of $L.current "moshaf") + " · " + $(if ([int]$L.current.percent -ge 0) { [string]::Format((W "downloadingPct"), $L.current.surah, $L.current.percent) } else { [string]::Format((W "downloading"), $L.current.surah) })
            if ($queued.ContainsKey($nowKey)) { $help = "$help · " + [string]::Format((W "queuedTag"), $queued[$nowKey]); $queued.Remove($nowKey) }
            [void]$ui.Rows.Children.Add((Row (Lang-Of $L.current "") $help (Tag-Text (W "nowTag") "#7A5A14" "#F6EEDC") $false)); $rows++
        }
        foreach ($q in @($L.queue)) {
            if (-not $queued.ContainsKey($q.key)) { continue }
            [void]$ui.Rows.Children.Add((Row (Lang-Of $q "") (Lang-Of $q "moshaf") (Tag-Text ([string]::Format((W "queuedTag"), $q.count))) $false)); $rows++
        }
        if ($rows -eq 0) { Note-Line (W "noneSaved") }
    } else {
        Note-Line (W "fileSource")
    }
    $addBtn = New-Object Windows.Controls.Button; $addBtn.Content = W "addRec"; $addBtn.Style = $window.FindResource("Primary")
    $addBtn.IsEnabled = @($L.reciters).Count -gt 0
    $addBtn.Add_Click({ $pick = Show-AddRecitation; if ($pick) { [void]$script:Pending.Add($pick); Show-Section $script:Section } })
    Head-Line (W "pendingHead") $addBtn
    Note-Line (W "pendingNote")
    if (@($L.reciters).Count -eq 0) { Note-Line (W "noList") $Gold }
    if ($script:Pending.Count -eq 0) { Note-Line (W "noPending") }
    $names = if ($script:Lang -eq "en") { @($L.suwar_en) } else { @($L.suwar_ar) }
    for ($i = 0; $i -lt $script:Pending.Count; $i++) {
        $p = $script:Pending[$i]
        if ($p.Want) {
            $shown = @($p.Want | Select-Object -First 6 | ForEach-Object { if ($names[$_]) { $names[$_] } else { "$_" } }) -join "، "
            if (@($p.Want).Count -gt 6) { $shown += "…" }
            $what = [string]::Format((W "someSurahs"), @($p.Want).Count, $shown)
        } else { $what = [string]::Format((W "wholeQuran"), @($p.R.surahs).Count) }
        $x = New-Object Windows.Controls.Button; $x.Content = "✕"; $x.Style = $window.FindResource("Btn"); $x.Padding = "8,2"; $x.Tag = $i
        $x.Add_Click({ param($s, $e) $script:Pending.RemoveAt([int]$s.Tag); Show-Section $script:Section })
        [void]$ui.Rows.Children.Add((Row (Lang-Of $p.R "") ((Lang-Of $p.R "moshaf") + " · " + $what) $x $true))
    }
}

# Add a recitation: reciter (searched), recording, the whole Quran or chosen surahs. Returns @{ R; Want } or $null.
function Show-AddRecitation {
    $L = $script:Library
    $dlg = New-Object Windows.Window
    $dlg.Title = "Awan TV · " + (W "addTitle"); $dlg.Width = 760; $dlg.Height = 760; $dlg.ShowInTaskbar = $false; $dlg.ResizeMode = "CanResizeWithGrip"
    $dlg.Background = Brush "#F9F9F9"; $dlg.FontFamily = $window.FontFamily; $dlg.FontSize = 14; $dlg.FlowDirection = $window.FlowDirection
    $dlg.Owner = $window; $dlg.WindowStartupLocation = "CenterOwner"
    try { $dlg.Icon = $window.Icon } catch { }
    $script:AddState = @{ Result = $null; Recs = @($L.reciters); Rec = $null; Names = $(if ($script:Lang -eq "en") { @($L.suwar_en) } else { @($L.suwar_ar) }) }
    $root = New-Object Windows.Controls.DockPanel; $root.Margin = "20,16"
    $bar = New-Object Windows.Controls.StackPanel; $bar.Orientation = "Horizontal"; $bar.HorizontalAlignment = "Right"; $bar.Margin = "0,12,0,0"
    [Windows.Controls.DockPanel]::SetDock($bar, "Bottom")
    $ok = New-Object Windows.Controls.Button; $ok.Content = W "addBtn"; $ok.Style = $window.FindResource("Primary"); $ok.IsDefault = $true; $ok.IsEnabled = $false
    $cancel = New-Object Windows.Controls.Button; $cancel.Content = W "cancel"; $cancel.Style = $window.FindResource("Btn"); $cancel.IsCancel = $true
    [void]$bar.Children.Add($ok); [void]$bar.Children.Add($cancel)
    [void]$root.Children.Add($bar)
    $body = New-Object Windows.Controls.Grid
    foreach ($h in @("Auto", "Auto", "160", "Auto", "Auto", "Auto", "Auto", "Auto", "Auto", "*")) {
        $rd = New-Object Windows.Controls.RowDefinition; $rd.Height = (New-Object Windows.GridLengthConverter).ConvertFromString($h); [void]$body.RowDefinitions.Add($rd)
    }
    $cell = { param($el, [int]$row) [Windows.Controls.Grid]::SetRow($el, $row); [void]$body.Children.Add($el) }
    $label = { param($text) $t = New-Object Windows.Controls.TextBlock; $t.Text = $text; $t.FontWeight = "SemiBold"; $t.Margin = "0,10,0,4"; return $t }
    & $cell (& $label (W "stepReciter")) 0
    $search = New-Object Windows.Controls.TextBox; $search.Height = 30; $search.VerticalContentAlignment = "Center"; $search.Padding = "6,0"; $search.ToolTip = W "searchReciter"
    & $cell $search 1
    $people = New-Object Windows.Controls.ListBox; $people.Margin = "0,4,0,0"
    & $cell $people 2
    $from = New-Object Windows.Controls.TextBlock; $from.Text = W "listFrom"; $from.FontSize = 11; $from.Foreground = Brush "#5F5F5F"
    & $cell $from 3
    & $cell (& $label (W "stepMoshaf")) 4
    $moshafs = New-Object Windows.Controls.StackPanel
    & $cell $moshafs 5
    & $cell (& $label (W "stepWhat")) 6
    $whole = New-Object Windows.Controls.RadioButton; $whole.GroupName = "what"; $whole.IsChecked = $true; $whole.Margin = "0,2"
    $some = New-Object Windows.Controls.RadioButton; $some.GroupName = "what"; $some.Content = W "someChosen"; $some.Margin = "0,2"
    $whatPanel = New-Object Windows.Controls.StackPanel; [void]$whatPanel.Children.Add($whole); [void]$whatPanel.Children.Add($some)
    & $cell $whatPanel 7
    $surahScroll = New-Object Windows.Controls.ScrollViewer; $surahScroll.VerticalScrollBarVisibility = "Auto"; $surahScroll.Margin = "0,4,0,0"; $surahScroll.IsEnabled = $false
    $surahGrid = New-Object Windows.Controls.Primitives.UniformGrid; $surahGrid.Columns = 3
    $surahScroll.Content = $surahGrid
    & $cell $surahScroll 9
    [void]$root.Children.Add($body)
    $dlg.Content = $root
    $script:AddState.People = $people; $script:AddState.Moshafs = $moshafs; $script:AddState.Grid = $surahGrid; $script:AddState.Scroll = $surahScroll; $script:AddState.Whole = $whole; $script:AddState.Some = $some; $script:AddState.Ok = $ok; $script:AddState.Search = $search

    # The reciters by name (one line each), filtered by the search. Handlers read $script:AddState: WPF calls them later.
    $script:AddState.Fill = {
        $script:AddState.People.Items.Clear()
        $seen = @{}
        $q = $script:AddState.Search.Text.Trim()
        foreach ($r in $script:AddState.Recs) {
            if ($seen.ContainsKey([string]$r.id)) { continue }
            $name = Lang-Of $r ""
            if ($q -and $name -notlike "*$q*" -and [string]$r.ar -notlike "*$q*" -and [string]$r.en -notlike "*$q*") { continue }
            $seen[[string]$r.id] = $true
            $li = New-Object Windows.Controls.ListBoxItem; $li.Content = $name; $li.Tag = [int]$r.id; $li.Padding = "8,4"
            [void]$script:AddState.People.Items.Add($li)
        }
    }
    # A recording chosen: its surahs as boxes, the whole Quran's count on the first choice.
    $script:AddState.ShowRec = {
        $script:AddState.Grid.Children.Clear()
        $r = $script:AddState.Rec
        $script:AddState.Ok.IsEnabled = $null -ne $r
        if (-not $r) { $script:AddState.Whole.Content = [string]::Format((W "allSurahs"), 0); return }
        $script:AddState.Whole.Content = [string]::Format((W "allSurahs"), @($r.surahs).Count)
        foreach ($n in @($r.surahs)) {
            $cb = New-Object Windows.Controls.CheckBox; $cb.Tag = [int]$n; $cb.Margin = "0,2"; $cb.FontSize = 13
            $name = $script:AddState.Names[[int]$n]; $cb.Content = if ($name) { "$n. $name" } else { "$n" }
            [void]$script:AddState.Grid.Children.Add($cb)
        }
    }
    $search.Add_TextChanged({ & $script:AddState.Fill })
    $people.Add_SelectionChanged({
        $script:AddState.Moshafs.Children.Clear(); $script:AddState.Rec = $null
        $li = $script:AddState.People.SelectedItem
        if ($li) {
            $first = $true
            foreach ($r in @($script:AddState.Recs | Where-Object { [int]$_.id -eq [int]$li.Tag })) {
                $rb = New-Object Windows.Controls.RadioButton; $rb.GroupName = "moshaf"; $rb.Margin = "0,2"; $rb.Tag = $r
                $m = Lang-Of $r "moshaf"; $rb.Content = if ($m) { $m } else { [string]$r.key }
                $rb.Add_Checked({ param($s, $e) $script:AddState.Rec = $s.Tag; & $script:AddState.ShowRec })
                [void]$script:AddState.Moshafs.Children.Add($rb)
                if ($first) { $rb.IsChecked = $true; $first = $false }
            }
        }
        if (-not $script:AddState.Rec) { & $script:AddState.ShowRec }
    })
    $some.Add_Checked({ $script:AddState.Scroll.IsEnabled = $true })
    $whole.Add_Checked({ $script:AddState.Scroll.IsEnabled = $false })
    $ok.Add_Click({
        $want = $null
        if ($script:AddState.Some.IsChecked) {
            $want = @($script:AddState.Grid.Children | Where-Object { $_.IsChecked } | ForEach-Object { [int]$_.Tag })
            if ($want.Count -eq 0) { [void](Show-Dialog "Awan TV" @(@{ Text = (W "pickSome") }) "warn" @((W "okBtn"))); return }
        }
        $script:AddState.Result = @{ R = $script:AddState.Rec; Want = $want }
        [Windows.Window]::GetWindow($this).Close()
    })
    & $script:AddState.Fill
    & $script:AddState.ShowRec
    [void]$dlg.ShowDialog()
    return $script:AddState.Result
}


# ---------------------------------------------------------------- TVs now (the radio on each TV, 1.10.0 on the TV)

$script:NowStatus = @{}      # TV id -> @{ State: on|off|sound|older|none|error; Station; Ar; En; Text }
$script:NowAt = ""
$NowSince = "1.10.0a"        # the first version that answers STATUS and RADIO

# STATUS's answer (also after RADIO's " | "): radio=on|off station=<id> sound=yes|no name=<Base64 of "ar\nen">.
function Parse-Status([string]$text) {
    $st = @{ State = "off"; Station = ""; Ar = ""; En = ""; Text = "" }
    $on = $text -match '(^|\s)radio=on(\s|$)'
    $sound = $text -match '(^|\s)sound=yes(\s|$)'
    if ($text -match '(^|\s)station=(\S+)') { $st.Station = $Matches[2] }
    if ($text -match '(^|\s)name=([A-Za-z0-9+/=]+)') {
        try {
            $both = @([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Matches[2])) -split "`n")
            $st.Ar = $both[0]; $st.En = if ($both.Count -gt 1 -and $both[1]) { $both[1] } else { $both[0] }
        } catch { }
    }
    $st.State = if ($on) { "on" } elseif ($sound) { "sound" } else { "off" }
    return $st
}

function Now-Station($st) {
    $name = if ($script:Lang -eq "en") { $st.En } else { $st.Ar }
    if (-not $name) { $name = $st.Station }
    if ($st.Station -like "saved:*") { $name += " (" + (W "savedMark") + ")" }
    return $name
}

# Every connected TV's radio now, asked once each (Refresh status, opening the page).
function Query-Now {
    foreach ($tv in @($script:Tvs | Where-Object { $_.Online })) {
        if (-not $tv.Version) { $script:NowStatus[$tv.Id] = @{ State = "none" }; continue }
        if (Version-Less $tv.Version $NowSince) { $script:NowStatus[$tv.Id] = @{ State = "older" }; continue }
        try { $script:NowStatus[$tv.Id] = Parse-Status (Send-Action $tv.Serial "$Pkg.STATUS") }
        catch {
            $msg = $_.Exception.Message
            $script:NowStatus[$tv.Id] = if ($msg -match "Unknown action") { @{ State = "older" } } else { @{ State = "error"; Text = $msg } }
        }
    }
    $script:NowAt = Get-Date -Format "HH:mm:ss"
}

# RADIO play or stop on one TV: its first answer (Playing, Already playing, Busy: ..., Stopped, Already stopped);
# the status after it is kept for the page.
function Radio-Do($tv, [string]$what, [bool]$force = $false) {
    $extras = "--es do $what"
    if ($force) { $extras += " --ez force true" }
    $answer = Send-Action $tv.Serial "$Pkg.RADIO" $extras
    $parts = @($answer -split " \| ", 2)
    if ($parts.Count -gt 1) { $script:NowStatus[$tv.Id] = Parse-Status $parts[1] }
    Log "Radio $what$(if ($force) { ' (anyway)' }) on $($tv.Id): $($parts[0])"
    return $parts[0]
}

# The window's words for RADIO's first answer.
function Radio-Words($tv, [string]$head) {
    if ($head -eq "Playing") {
        $st = $script:NowStatus[$tv.Id]
        return [string]::Format((W "started"), $(if ($st) { Now-Station $st } else { "" }))
    }
    if ($head -eq "Already playing") { return W "alreadyOn" }
    if ($head -eq "Stopped") { return W "stopped" }
    if ($head -eq "Already stopped") { return W "alreadyOff" }
    if ($head -like "Busy*") { return W "busyShort" }
    return $head
}

# The TVs ticked for Play on chosen / Stop on chosen, kept on this computer (state\editor\now.json, by TV id).
$NowFile = Join-Path $EditorDir "now.json"
$script:NowChosen = @{}
try {
    if (Test-Path $NowFile) { foreach ($id in @((Read-Json $NowFile).chosen)) { if ($id) { $script:NowChosen[[string]$id] = $true } } }
} catch { Log "now.json unreadable: $($_.Exception.Message)" }
function Save-NowChosen {
    Write-Utf8 $NowFile (ConvertTo-Json -InputObject @{ chosen = @($script:NowChosen.Keys | Sort-Object) })
}

# A TV whose radio can be played or stopped from here now (connected, 1.10.0 on, answered).
function Can-Control($tv) {
    $st = $script:NowStatus[$tv.Id]
    return ($tv.Online -and $st -and @("on", "off", "sound") -contains $st.State)
}
# A function hands back one TV as itself, not in a list, and Windows PowerShell 5.1 gives such an object no Count:
# callers wrap it, @(Chosen-Tvs).
function Chosen-Tvs { return @($script:Tvs | Where-Object { $script:NowChosen[$_.Id] -and (Can-Control $_) }) }

# While a question waits for the viewer: the window as usual, not "Working…"; then busy again for what follows.
function Wait-Free { $window.Cursor = $null; Update-Chrome }
function Wait-Busy { $window.Cursor = [Windows.Input.Cursors]::Wait; $ui.ShownText.Text = W "working"; $window.Dispatcher.Invoke([Action]{}, [Windows.Threading.DispatcherPriority]::Background) }

# Play on one TV; sound from another app there: asked first, Play anyway forces it.
function Play-One($tv) {
    $head = Radio-Do $tv "play"
    if ($head -like "Busy*") {
        Wait-Free
        $r = Show-Dialog (Name-Of $tv) @(@{ Text = (W "busyText") }, @{ Text = (W "busyNote") }) "ask" @((W "playAnyway"), (W "cancel"))
        if ($r -eq 0) { Wait-Busy; $head = Radio-Do $tv "play" $true }
    }
    $script:Notice = "$(Name-Of $tv): $(Radio-Words $tv $head)"
}

# Play on chosen's answers, with the TVs that play another app's sound each with a box (none ticked): returns the
# ticked ones to play anyway, none when OK (Enter, Esc) closes it.
function Ask-Busy([string]$title, [object[]]$results, [object[]]$busy) {
    $dlg = New-Object Windows.Window
    $dlg.Title = "Awan TV"; $dlg.ResizeMode = "NoResize"; $dlg.SizeToContent = "WidthAndHeight"; $dlg.ShowInTaskbar = $false
    $dlg.Background = Brush "#F3F3F3"; $dlg.FontFamily = $window.FontFamily; $dlg.FontSize = 14; $dlg.FlowDirection = $window.FlowDirection
    $dlg.Owner = $window; $dlg.WindowStartupLocation = "CenterOwner"
    try { $dlg.Icon = $window.Icon } catch { }
    $panel = New-Object Windows.Controls.StackPanel; $panel.Margin = "22,18"; $panel.Width = 560
    $h = New-Object Windows.Controls.TextBlock; $h.Text = $title; $h.FontWeight = "SemiBold"; $h.FontSize = 16; $h.Margin = "0,0,0,10"
    [void]$panel.Children.Add($h)
    foreach ($it in $results) {
        $t = New-Object Windows.Controls.TextBlock; $t.Text = $it.Text; $t.TextWrapping = "Wrap"; $t.Margin = "0,3"
        if ($it.Text -like "!*") { $t.Foreground = Brush "#7A5A14" }
        [void]$panel.Children.Add($t)
    }
    $box = New-Object Windows.Controls.Border; $box.Background = Brush "#F6EEDC"; $box.CornerRadius = 8; $box.Padding = "14,10"; $box.Margin = "0,12,0,0"
    $inner = New-Object Windows.Controls.StackPanel
    $bh = New-Object Windows.Controls.TextBlock; $bh.Text = W "busyHead"; $bh.FontWeight = "SemiBold"; $bh.Foreground = Brush "#7A5A14"; $bh.TextWrapping = "Wrap"; $bh.Margin = "0,0,0,6"
    [void]$inner.Children.Add($bh)
    $force = New-Object Windows.Controls.Button; $force.Style = $window.FindResource("Btn"); $force.IsEnabled = $false; $force.Content = W "playTicked"
    $script:BusyState = @{ Boxes = @(); Force = $force; Result = @() }
    foreach ($tv in $busy) {
        $cb = New-Object Windows.Controls.CheckBox; $cb.Content = Name-Of $tv; $cb.Tag = $tv; $cb.Margin = "0,4"; $cb.VerticalContentAlignment = "Center"
        # Through $script:BusyState: WPF calls the handler later.
        $cb.Add_Click({
            $n = @($script:BusyState.Boxes | Where-Object { $_.IsChecked }).Count
            $script:BusyState.Force.IsEnabled = $n -gt 0
            $script:BusyState.Force.Content = if ($n -gt 0) { [string]::Format((W "playTickedN"), $n) } else { W "playTicked" }
        })
        $script:BusyState.Boxes += $cb
        [void]$inner.Children.Add($cb)
    }
    $note = New-Object Windows.Controls.TextBlock; $note.Text = W "busyPick"; $note.Foreground = Brush "#5F5F5F"; $note.TextWrapping = "Wrap"; $note.Margin = "0,6,0,0"
    [void]$inner.Children.Add($note)
    $box.Child = $inner
    [void]$panel.Children.Add($box)
    $bar = New-Object Windows.Controls.StackPanel; $bar.Orientation = "Horizontal"; $bar.HorizontalAlignment = "Right"; $bar.Margin = "0,16,0,0"
    $ok = New-Object Windows.Controls.Button; $ok.Content = W "okBtn"; $ok.Style = $window.FindResource("Primary"); $ok.IsDefault = $true; $ok.IsCancel = $true
    $ok.Add_Click({ [Windows.Window]::GetWindow($this).Close() })
    $force.Add_Click({
        $script:BusyState.Result = @($script:BusyState.Boxes | Where-Object { $_.IsChecked } | ForEach-Object { $_.Tag })
        [Windows.Window]::GetWindow($this).Close()
    })
    [void]$bar.Children.Add($force); [void]$bar.Children.Add($ok)
    [void]$panel.Children.Add($bar)
    $dlg.Content = $panel
    [void]$dlg.ShowDialog()
    return @($script:BusyState.Result)
}

# Play or Stop on the chosen TVs, then their answers in one window; Play: the ones playing another app's sound are
# offered to play anyway, each ticked by the viewer.
function Radio-All([string]$what) {
    $tvs = @(Chosen-Tvs)
    $title = [string]::Format((W $(if ($what -eq "play") { "playChosenTitle" } else { "stopChosenTitle" })), $tvs.Count)
    $results = @(); $busy = @()
    foreach ($tv in $tvs) {
        $name = Name-Of $tv
        try {
            $head = Radio-Do $tv $what
            if ($head -like "Busy*") { $busy += $tv }
            else { $results += @{ Text = "✓ ${name}: $(Radio-Words $tv $head)" } }
        } catch {
            $msg = $_.Exception.Message
            if ($msg -match "Unknown action") { $script:NowStatus[$tv.Id] = @{ State = "older" }; $results += @{ Text = "! ${name}: $(W 'nowOlderShort')" } }
            else { $results += @{ Text = "! ${name}: $msg" } }
        }
    }
    Wait-Free
    if ($busy.Count -eq 0) { [void](Show-Dialog $title $results "info" @((W "okBtn"))); return }
    $again = @(Ask-Busy $title $results $busy)
    if ($again.Count -eq 0) { return }
    Wait-Busy
    $done = @()
    foreach ($tv in $again) {
        try { $done += @{ Text = "✓ $(Name-Of $tv): $(Radio-Words $tv (Radio-Do $tv 'play' $true))" } }
        catch { $done += @{ Text = "! $(Name-Of $tv): $($_.Exception.Message)" } }
    }
    Wait-Free
    [void](Show-Dialog ([string]::Format((W "playTickedTitle"), $again.Count)) $done "info" @((W "okBtn")))
}

# The two buttons' counts, after a box is ticked (the page is not built again).
function Now-Counts {
    $n = @(Chosen-Tvs).Count
    if ($script:NowPlay) { $script:NowPlay.Content = [string]::Format((W "playChosen"), $n); $script:NowPlay.IsEnabled = $n -gt 0 }
    if ($script:NowStop) { $script:NowStop.Content = [string]::Format((W "stopChosen"), $n); $script:NowStop.IsEnabled = $n -gt 0 }
}

function Now-Card($tv, $st) {
    $card = New-Object Windows.Controls.Border
    $card.Background = Brush "#FFFFFF"; $card.BorderBrush = Brush "#E5E5E5"; $card.BorderThickness = 1; $card.CornerRadius = 6
    $card.Padding = "16,10"; $card.Margin = "0,0,0,4"
    $dock = New-Object Windows.Controls.DockPanel
    $can = Can-Control $tv
    # Its box, or the same room left empty so the names line up.
    $cb = New-Object Windows.Controls.CheckBox; $cb.VerticalAlignment = "Center"; $cb.Margin = "0,0,14,0"; $cb.Tag = $card
    if ($can) {
        $cb.IsChecked = [bool]$script:NowChosen[$tv.Id]
        $card.Tag = $tv
        if ($cb.IsChecked) { $card.BorderBrush = Brush $Navy }
        $cb.Add_Click({
            param($s, $e)
            $c = $s.Tag; $id = $c.Tag.Id
            if ($s.IsChecked) { $script:NowChosen[$id] = $true; $c.BorderBrush = Brush $Navy } else { $script:NowChosen.Remove($id); $c.BorderBrush = Brush "#E5E5E5" }
            Save-NowChosen; Now-Counts
        })
    } else { $cb.Visibility = "Hidden" }
    [Windows.Controls.DockPanel]::SetDock($cb, "Left"); [void]$dock.Children.Add($cb)
    if ($can) {
        $btn = New-Object Windows.Controls.Button; $btn.Tag = $tv; $btn.VerticalAlignment = "Center"; $btn.Margin = "16,0,0,0"; $btn.MinWidth = 96
        if ($st.State -eq "on") {
            $btn.Content = W "stop"; $btn.Style = $window.FindResource("Btn")
            $btn.Add_Click({ param($s, $e) $tv = $s.Tag; Busy { $script:Notice = "$(Name-Of $tv): $(Radio-Words $tv (Radio-Do $tv 'stop'))" }; Show-Section $script:Section })
        } else {
            $btn.Content = W "play"; $btn.Style = $window.FindResource($(if ($st.State -eq "off") { "Primary" } else { "Btn" }))
            $btn.Add_Click({ param($s, $e) $tv = $s.Tag; Busy { Play-One $tv }; Show-Section $script:Section })
        }
        [Windows.Controls.DockPanel]::SetDock($btn, "Right"); [void]$dock.Children.Add($btn)
    }
    $text = New-Object Windows.Controls.StackPanel
    $l1 = New-Object Windows.Controls.TextBlock; $l1.Text = Name-Of $tv; $l1.FontWeight = "SemiBold"; $l1.TextWrapping = "Wrap"
    $l2 = New-Object Windows.Controls.TextBlock; $l2.FontSize = 12; $l2.Foreground = Brush "#5F5F5F"; $l2.Margin = "0,1,0,3"
    $l2.Text = if ($tv.Online) { "v$($tv.Version)" } else { [string]::Format((W "lastSeen"), $tv.Seen) }
    $l3 = New-Object Windows.Controls.TextBlock; $l3.TextWrapping = "Wrap"; $l3.FontSize = 13
    $color = "#5F5F5F"
    if (-not $tv.Online) { $l3.Text = W "offline" }
    elseif (-not $st) { $l3.Text = W "nowUnknown" }
    else {
        switch ($st.State) {
            "on" { $l3.Text = [string]::Format((W "radioOn"), (Now-Station $st)); $color = "#1B6E3A" }
            "off" { $l3.Text = [string]::Format((W "radioOff"), (Now-Station $st)); $color = "#555555" }
            "sound" { $l3.Text = [string]::Format((W "otherSound"), (Now-Station $st)); $color = "#7A5A14" }
            "older" { $l3.Text = W "nowOlder"; $color = "#7A5A14" }
            "none" { $l3.Text = W "noAwan"; $color = "#7A5A14" }
            default { $l3.Text = [string]::Format((W "noAnswer"), $st.Text); $color = "#7A5A14" }
        }
    }
    $l3.Foreground = Brush $color
    foreach ($l in @($l1, $l2, $l3)) { [void]$text.Children.Add($l) }
    [void]$dock.Children.Add($text)
    $card.Child = $dock
    if (-not $tv.Online) { $card.Opacity = 0.55 }
    [void]$ui.Rows.Children.Add($card)
}

function Link-Text([string]$text, [scriptblock]$onClick) {
    $t = New-Object Windows.Controls.TextBlock; $t.Margin = "14,0,0,0"; $t.VerticalAlignment = "Center"
    $link = New-Object Windows.Documents.Hyperlink; [void]$link.Inlines.Add($text); $link.Add_Click($onClick)
    [void]$t.Inlines.Add($link)
    [Windows.Controls.DockPanel]::SetDock($t, "Right")
    return $t
}

# TVs now: the page from the last status asked (opening the page or Refresh status asks again).
function Show-Now {
    $top = New-Object Windows.Controls.DockPanel; $top.Margin = "0,0,0,8"
    $buttons = New-Object Windows.Controls.StackPanel; $buttons.Orientation = "Horizontal"
    [Windows.Controls.DockPanel]::SetDock($buttons, "Right")
    $online = @($script:Tvs | Where-Object { $_.Online })
    $refresh = New-Object Windows.Controls.Button; $refresh.Content = W "nowRefresh"; $refresh.Style = $window.FindResource("Btn")
    $refresh.Add_Click({ Busy { Refresh-Tvs; Query-Now }; Show-Section $script:Section })
    $script:NowPlay = New-Object Windows.Controls.Button; $script:NowPlay.Style = $window.FindResource("Primary")
    $script:NowPlay.Add_Click({ Busy { Radio-All "play" }; Show-Section $script:Section })
    $script:NowStop = New-Object Windows.Controls.Button; $script:NowStop.Style = $window.FindResource("Btn")
    $script:NowStop.Add_Click({ Busy { Radio-All "stop" }; Show-Section $script:Section })
    foreach ($b in @($refresh, $script:NowPlay, $script:NowStop)) { [void]$buttons.Children.Add($b) }
    Now-Counts
    [void]$top.Children.Add($buttons)
    $head = New-Object Windows.Controls.TextBlock; $head.Text = W "nowTitle"; $head.FontSize = 20; $head.FontWeight = "SemiBold"; $head.VerticalAlignment = "Center"
    [void]$top.Children.Add($head)
    [void]$ui.Rows.Children.Add($top)
    Note-Line (W "nowNote")
    # Tick all / none (the TVs that can be controlled now), beside what the boxes are for.
    $pick = New-Object Windows.Controls.DockPanel; $pick.Margin = "4,0,4,8"
    [void]$pick.Children.Add((Link-Text (W "none") {
        foreach ($t in @($script:Tvs | Where-Object { Can-Control $_ })) { $script:NowChosen.Remove($t.Id) }
        Save-NowChosen; Show-Section $script:Section
    }))
    [void]$pick.Children.Add((Link-Text (W "all") {
        foreach ($t in @($script:Tvs | Where-Object { Can-Control $_ })) { $script:NowChosen[$t.Id] = $true }
        Save-NowChosen; Show-Section $script:Section
    }))
    $pn = New-Object Windows.Controls.TextBlock; $pn.Text = W "chooseNote"; $pn.TextWrapping = "Wrap"; $pn.FontSize = 12; $pn.Foreground = Brush "#5F5F5F"; $pn.VerticalAlignment = "Center"
    [void]$pick.Children.Add($pn)
    [void]$ui.Rows.Children.Add($pick)
    if ($script:NowAt) { Note-Line ([string]::Format((W "nowAt"), $script:NowAt)) }
    if ($online.Count -eq 0) { Note-Line $(if ($script:Adb) { W "noTv" } else { W "noAdb" }) $Gold }
    $headed = $false
    foreach ($tv in $script:Tvs) {
        if (-not $tv.Online -and -not $headed) { $headed = $true; Head-Line (W "offlineGroup") }
        Now-Card $tv $(if ($tv.Online) { $script:NowStatus[$tv.Id] } else { $null })
    }
}

# Nav lines: 0 TVs now, 1 a line between, then the settings' sections from 2 (once a source is shown).
$FirstSection = 2
$script:Section = $FirstSection
$script:NavMap = New-Object Collections.ArrayList   # each nav line: a section's index, -1 Saved recitations, -2 TVs now, -3 the line
$script:FillingNav = $false
function Show-Section([int]$index) {
    $script:Section = $index
    $ui.Rows.Children.Clear()
    $target = if ($index -ge 0 -and $index -lt $script:NavMap.Count) { [int]$script:NavMap[$index] } else { 0 }
    if ($target -eq -2) { Show-Now; Update-Chrome; return }
    if (-not $script:Schema -or $target -lt -1) { Update-Chrome; return }
    if ($target -eq -1) { Show-Library; Update-Chrome; return }
    $sec = $script:Schema.sections[$target]
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
    # Lines added and selected here open nothing by themselves (Show-Section is called after).
    $script:FillingNav = $true
    try { Fill-NavLines } finally { $script:FillingNav = $false }
}
function Fill-NavLines {
    $ui.Nav.Items.Clear()
    $script:NavMap.Clear()
    # TVs now first: it needs no settings shown, only connected TVs.
    $li = New-Object Windows.Controls.ListBoxItem; $li.Content = W "nowTitle"; $li.Padding = "12,8"; $li.FontSize = 14; $li.FontWeight = "SemiBold"
    [void]$ui.Nav.Items.Add($li); [void]$script:NavMap.Add(-2)
    if (-not $script:Schema) {
        $ui.Nav.SelectedIndex = if ($script:Section -eq 0) { 0 } else { -1 }
        return
    }
    $line = New-Object Windows.Controls.ListBoxItem; $line.IsEnabled = $false; $line.Focusable = $false; $line.Padding = "12,6"
    $rule = New-Object Windows.Controls.Border; $rule.Height = 1; $rule.Width = 200; $rule.Background = Brush "#E5E5E5"
    $line.Content = $rule
    [void]$ui.Nav.Items.Add($line); [void]$script:NavMap.Add(-3)
    $sections = @($script:Schema.sections)
    for ($i = 0; $i -lt $sections.Count; $i++) {
        $li = New-Object Windows.Controls.ListBoxItem; $li.Content = $sections[$i].label; $li.Padding = "12,8"; $li.FontSize = 14
        [void]$ui.Nav.Items.Add($li); [void]$script:NavMap.Add($i)
        # Saved recitations after Radio, as on the TV, for a source on 1.9.0 or later.
        if ($sections[$i].id -eq "radio" -and $script:Items["station"] -and $script:Items["station"].kind -eq "station") {
            $li = New-Object Windows.Controls.ListBoxItem; $li.Content = W "libTitle"; $li.Padding = "12,8"; $li.FontSize = 14
            [void]$ui.Nav.Items.Add($li); [void]$script:NavMap.Add(-1)
        }
    }
    if ($script:Section -eq 1) { $script:Section = $FirstSection }
    $ui.Nav.SelectedIndex = [Math]::Min($script:Section, $ui.Nav.Items.Count - 1)
}

function Loaded {
    $script:Views = @{ iqama = "days" }
    $script:StationGroup = $null
    # New settings shown: no TV chosen to send to, so nothing is sent by mistake; the viewer chooses each time.
    foreach ($t in $script:Tvs) { $t.Selected = $false }
    # Settings just got or opened: their first section, also when TVs now was open.
    if ($script:Section -lt $FirstSection) { $script:Section = $FirstSection }
    Fill-Nav
    Show-Section $ui.Nav.SelectedIndex
    Fill-Targets
}

# ---------------------------------------------------------------- actions

$ui.Nav.Add_SelectionChanged({
    if ($script:FillingNav -or $ui.Nav.SelectedIndex -lt 0) { return }
    $i = $ui.Nav.SelectedIndex
    # TVs now asks each TV when opened, so what it shows is now.
    if ([int]$script:NavMap[$i] -eq -2) { $script:Section = $i; Busy { Query-Now } }
    Show-Section $i
})
$ui.RefreshBtn.Add_Click({
    Busy { Refresh-Tvs; if ($script:Section -lt $script:NavMap.Count -and [int]$script:NavMap[$script:Section] -eq -2) { Query-Now } }
    if ($script:Section -lt $script:NavMap.Count -and [int]$script:NavMap[$script:Section] -eq -2) { Show-Section $script:Section }
})
$ui.SourceBox.Add_SelectionChanged({ Update-Chrome })

$ui.GetBtn.Add_Click({
    Busy {
        $tv = $ui.SourceBox.SelectedItem.Tag
        $file = Join-Path $SettingsDir ("awantv-settings-$($tv.Id)-$(Stamp).jsonc")
        $got = Export-Tv $tv $file
        Use-Settings (Read-Settings $got.File) ("$(Name-Of $tv) · v$($tv.Version) · $(Get-Date -Format 'HH:mm')")
        $script:Library = if ($got.Library) { Read-Json $got.Library } else { $null }
        $script:LibraryLive = $null -ne $script:Library
        $script:LibrarySource = Name-Of $tv
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
            $script:Library = Last-Library
            $script:LibraryLive = $false
            $script:LibrarySource = ""
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
        }
    }
    Fill-Nav
    Show-Section $script:Section
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
            Write-Utf8 $dlg.FileName (Build-File $null $true)
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
                $only = $null; $skipped = 0; $knows = $false
                if ($got.Schema) {
                    $ts = [IO.File]::ReadAllText($got.Schema, [Text.Encoding]::UTF8) | ConvertFrom-Json
                    $only = @(foreach ($sec in $ts.sections) { foreach ($it in $sec.items) { $it.key; if ($it.key -eq "station" -and $it.kind -eq "station") { $knows = $true } } })
                    # Before 1.9.0 a TV takes no recordings, and no station it has not got (it falls back to Cairo).
                    if (-not $knows) { $only = @($only | Where-Object { $_ -ne "station" }) }
                    $skipped = @($script:Values.Keys | Where-Object { $only -notcontains $_ -and $_ -ne "station" }).Count
                }
                $downloads = if ($knows) { @($script:Pending) } else { @() }
                $payload = Join-Path $EditorDir "send.jsonc"
                Write-Utf8 $payload (Build-File $only $false $downloads)
                [void](Shell $tv.Serial "mkdir -p $Files")
                [void](Adb $tv.Serial @("push", $payload, $Remote))
                try { $answer = Tv-Words (Send-Action $tv.Serial "$Pkg.IMPORT") } finally { [void](Shell $tv.Serial "rm -f $Remote") }
                if ($skipped -gt 0) { $answer += "`n" + [string]::Format((W "skipped"), $skipped) }
                if (-not $knows -and ($script:Pending.Count -gt 0 -or -not (Same $script:Values["station"] $script:Orig["station"]))) { $answer += "`n" + (W "olderNoLibrary") }
                $results += @{ Title = "$n. $name"; Text = $answer }
                Log "Sent to $($tv.Id): $answer (backup $backup)"
            } catch {
                $results += @{ Title = "$n. $name"; Text = $_.Exception.Message }
                Log "Send to $($tv.Id) failed: $($_.Exception.Message)"
            }
        }
        foreach ($k in @($script:Values.Keys)) { $script:Orig[$k] = $script:Values[$k] }
        # Sent: the recordings are each TV's to download now (Get from TV shows how far).
        if ($n -gt 0) { $script:Pending.Clear() }
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
Fill-Nav
Update-Chrome
[void]$window.ShowDialog()
$timer.Stop()
$script:Mutex.ReleaseMutex()
Log "Closed"
