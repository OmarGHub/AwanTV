# Changelog

All notable changes to Awan TV. Versions match `awanVersion` in `gradle.properties`.
Awan TV is built on [Afnon](https://github.com/egyjs/afnon-android-tv-launcher) (MIT), up to its 0.5; Afnon's own
history is in its repository.

## [Unreleased]

## [1.22.0]

### The app
- Spanish (Español), the fifth language added to Arabic and English (test builds from 1.22.0a): every screen's words
  (res/values-es), Spain's Spanish: the prayers as Spanish mosques and Mawaqit write them (Fajr, Shuruq, Dhuhr, Asr,
  Maghrib, Isha; adhan, iqama, qibla), Spanish spelling where Spanish Muslims use it (jutba, Yumu'a, hadiz, Ramadán)
  and Android's own Spanish for the settings words; "45 %" and "2 GB" as the RAE writes them. Ramadan's Fajr cell
  says "AYUNO". Reciters and surahs in Spanish by mp3quran.net ("es"); the adhkar's counts and the hadiths'
  references in Spanish (`tools/labels-es.tsv`, "Narrado por …", "e Ibn" before an i sound). Every hint beside choices
  measured against the room the tested languages proved before the build. Checked by Gemini before the build, each
  item against the code.
  1.22.0b: Home's prayer times grow smaller to their cell when the 12-hour marker is longer than AM/PM ("12:04 p. m."
  was 8 px too wide); times that fit keep their size.
  1.22.0c: the radio card's buttons in Spanish "Escuchar" and "Solo audio" ("Pantalla apagada" took two lines: the
  three buttons share one row).
  In What's new, in the seven languages.

### Tools
- AwanTV-texts-sheet 1.1.2: Spanish's column named.

## [1.21.0]

### The app
- Malay (Bahasa Melayu), the fourth language added to Arabic and English (test builds from 1.21.0a): every screen's
  words (res/values-ms), in JAKIM's terms (Subuh, Syuruk, Zohor, Asar, Maghrib, Isyak; azan, iqamah, kiblat, Jumaat;
  the Hijri months as Malaysia spells them) and Android's own Malay for the settings words. Ramadan's Fajr cell says
  "PUASA": in JAKIM's timetables Imsak is its own column, ten minutes before Subuh. Reciters and surahs stay in English
  (mp3quran.net has no Malay); the adhkar's counts and the hadiths' references in Malay (`tools/labels-ms.tsv`,
  "Riwayat …"). Checked by Gemini before the build, each item against the code.
  1.21.0b: three settings hints shorter, as 1.21.0a's crop check found (Runs, Screen off while listening with
  narrower choices, Imsak in Ramadan: "imsak: ihtiyat pilihan", JAKIM's own word for the margin).
  1.21.0c: Screen off while listening's hint is "Skrin gelap, radio hidup" (fits without growing smaller).
  In What's new, in the six languages.

### Tools
- AwanTV-texts-sheet 1.1.1: Malay's column named.
- The crop check (test builds) writes a cut only when two checks in a row see it: a row caught as it was drawn,
  before it grew smaller, was taken for a cut (1.21.0c). mp3quran.net's names of the reciter channels are not
  checked: their data as it comes, which changes, not Awan TV's words.

## [1.20.0]

### The app
- French (Français), the third language added to Arabic and English (test builds from 1.20.0a): every screen's
  words (res/values-fr), in the terms of French mosques and Mawaqit (Fajr, Chourouq, Dhuhr, Asr, Maghrib, Isha;
  Iqama; Jumua; Khutba) and Android's own French for the settings words; French typography (a no-break space before
  ":" ";" "?" and inside « », "45 %", Mo and Go). Ramadan's Fajr cell says "JEÛNE": in French timetables imsak is the
  precaution minutes before Fajr, as in Arabic. Reciters and surahs in French by mp3quran.net ("fr"); the adhkar's
  counts and the hadiths' references in French (`tools/labels-fr.tsv`, "Rapporté par …"). Checked by Gemini before
  the build, each item against the code. Counts: 0 takes the singular in French alone ("0 minute", `Text.one`).
- Home's cells keep one line each, the prayer's name and its tag (NEXT, NOW...) smaller to fit, to 75%; the Hijri
  date to 85%; and the lists over a screen to 75% (1.20.0b's crop check in French: "Chourouq", "MAINTENANT" on two
  lines, "26 Joumada al-oula 1448 H", mp3quran.net's French names). `Screen.fitWidth` fits again on every new text
  (a view of set size lays nothing out when only its text changes). Four French help lines shorter.
  `fitWidth` takes any step, two pixels to spare (1.20.0c: a text 1-2% too wide skipped the half-pixel step and kept
  its "…": French "Chourou…", "MAINTENA…"). Turkish "Tümünü gör" in Home's all-apps box ("Tüm uygulamalar" broke
  mid-word). Still too wide at the smallest size, a text's letters come closer before any "…" (1.20.0d: the spaced
  capitals of a cell tag); French's current prayer is "EN COURS" ("MAINTENANT" needed 70%).
- The imsak hint in Settings > Prayer times shorter in English and Urdu, the same meaning (1.20.0c's crop check:
  both ran past the minute choices): "The fast starts at Fajr's adhan · imsak: an optional caution".

### Tools
- `CropCheck` (test builds) forgets what it has written once the list is cleared from the computer: a cut found
  before `AwanTV-textdump -Crops -Clear` was never written again.

## [1.19.0]

### The app
- Turkish (Türkçe), the second language added to Arabic and English, after Urdu (test builds from 1.19.0a): every
  screen's words (res/values-tr), in Diyanet's terms and as Turkish prayer apps write them (Namaz Vakti, Al-Azan, and Android's own
  Turkish for the settings words): Sabah, Güneş, Öğle, İkindi, Akşam, Yatsı; Kamet for the iqamah; Asr-ı Evvel and
  Asr-ı Sânî. Ramadan's Fajr cell says "İMSAK": in Turkey imsak is the fast's start itself (Diyanet: the first light
  of true dawn, the start of the fast and of Sabah's time), so the optional precaution before it is "İhtiyat"
  (Settings > Prayer times). Reciters and surahs in Turkish by mp3quran.net ("tr"); the adhkar's counts and the
  hadiths' references in Turkish (`tools/labels-tr.tsv`, the books as Diyanet names them). No text ends in a
  suffix after a part (Turkish suffixes follow the word put in: "Sabah vaktine", "%2$s gün sonra %1$s"); the
  percent sign before the number ("%45"). Settings' section titles in capitals by the screen's language, not the
  TV's (Turkish "i" is "İ"). In What's new.
  After Gemini's review, each item checked against the code and Turkish sources (1.19.0b): the adhan is read, not
  played ("Okundu", "Ezanı dinle"); "Dünya İslâm Birliği" (TDV İslâm Ansiklopedisi's name for the League);
  "Eyyâm-ı Biyd"; "düğme" for every button; no comma before "ve"; Turkish quotes; and a few closer to the Arabic.
- Lists drawn over a screen (the stations, the languages, every chooser) grow smaller where a name is wider than its
  row, to 80%, then end in "…" (1.19.0b's crop check: mp3quran.net's reciter channels in Turkish ran past the row);
  the About codes' titles keep one line and grow smaller likewise. Arabic, English and Urdu look the same.

### Tools
- `AwanTV-texts-sheet.py` 1.1.0: several languages side by side (`ur,tr`: Arabic, English, Urdu, Turkish), a
  column and its notes each.

## [1.18.0]

### The app
- Urdu (اردو), the first language after Arabic and English (languages plan, phase 4; test builds from 1.18.0d):
  every screen's words (res/values-ur), right to left in the app's fonts, with Urdu's own digits (۰۱۲۳) when Eastern
  digits are chosen (Western by default); a TV in Urdu set to "TV" shows Urdu. Reciters, riwayahs, surahs and
  reciter channels are named by mp3quran.net in Urdu (its code "ur"); the counts and references around the adhkar
  and hadiths are in Urdu (`tools/labels-ur.tsv`); cities, Afnon's stations and the adhan voices in English. The
  settings file and the editor stay Arabic or English; What's new of earlier versions in English. The Arabic digit
  paths (the decimal sign, the 2 GB note, the waiting line) follow the Arabic script, Urdu too.
  Home's prayer times in Urdu with a 12-hour clock as small as English's (they write AM/PM too: 1.18.0d's crop
  check found "12:07 PM" 10 px too wide).
  From the Urdu screens (1.18.0f): Home's prayer names keep one line, smaller where wide ("طلوعِ آفتاب" took two);
  Settings' menu and its windows' lines read from the screen's start even when they begin with "Awan TV" (the
  menu's "Awan TV" stood at the left, the reset window's ✓ at the line's end). Arabic and English look the same.
  After a review of the Urdu against Android's own Urdu and Urdu prayer apps (Al-Azan, Prayer Times) (1.18.0g):
  the countdown labels "فجر تک" (Fajr's "میں" read as "during"), "ایڈجسٹمنٹ" for adjusting the times (Android's
  word), "کوآرڈینیٹس" for coordinates, "رابطہ عالمِ اسلامی" (the League's Urdu name), "گہرا سرمئی" for Charcoal,
  "تھیم", "محفوظ کریں", "تبدیل کریں", "دبائے رکھنے پر کچھ نہیں", the update line's word order and a few closer to the
  Arabic.
- Ramadan's Fajr cell on Home says the fast begins: "بداية الصوم" / "FASTING" / "روزے کا آغاز" (was "الإمساك" /
  "SUHOOR" / "سحری"). Imsak in Arabic timetables is the optional precaution minutes before Fajr (Egypt's Dar al-Ifta:
  a precaution, not binding), so on the Fajr cell it read as optional; it stays the name of that precaution alone
  (Settings > Prayer times > Imsak in Ramadan, whose help now says the fast begins at the Fajr adhan and imsak is an
  optional caution before it). Omar's choice. In What's new, with Urdu.
- Home: the weather sits closer to the clock (16 px each side of the line between them, 32 before), so beside a
  12-hour clock "28°" no longer runs under the radio button; a wider reading ("-12°", "113°") makes the
  temperature's figures alone smaller, only as far as needed (Omar's idea).
- Languages plan, phase 3 (no change on screen in Arabic and English): `Text` takes any language (`Text.of(c, tag,
  eastern)`): direction, script, dates and order by language (`Languages.rtl`, `arabicScript`); Urdu's own Eastern
  digits (۰۱۲۳, `Languages.easternZero`); "TV" = the TV's language when Awan TV has it (`Screen.languageTag`,
  Android's "in" read as "id"); countries by Android in another language with Palestine ours (`Places.country(cc,
  t)`, `Prefs.countryOf`); the adhkar's and hadiths' count and reference in any language written in their files
  ("countUr", "refUr"), else English (`Languages.fields`, `pick`). The text dump: every line the same.
- Before a language is announced (1.18.0b): the build stops when a language's texts use a letter missing from the
  app's fonts (`checkFonts`, beside `checkTexts`); test builds list every text that does not fit its place
  (`CropCheck`: "...", lines or width past the view, cut by its box) for `AwanTV-textdump -Crops`.
- Texts that did not fit (1.18.0b's crop check): Settings' help lines grow smaller to fit their row, to 80%, then
  end in "…"; the screensaver's gold line likewise to 60% (Android's own autosize let "Friday night · Al-Kahf ·
  Salawat" run out of the centre); the About codes' hint takes two lines. Shorter in both languages (Omar: short
  is best in this app): the hadith and adhkar help, Home and screensaver's run mode, the radio's dark screen, and in
  English the codes' hint, Friday's gold line ("Al-Kahf · Salawat ﷺ", as Friday night's) and, 1.18.0d, the radio's
  dark screen again ("Screen dark, radio on · any key brings it back": the row's choices leave it under 500 px).

### Tools
- `AwanTV-textdump` 1.2.0: `-Crops` copies the TV's list of texts that do not fit (each once), `-Clear` empties it.
- `AwanTV-textdump` 1.3.0: the editor's description and the settings file (numbered by place) are matched by their
  text, so a line added near the top no longer lists every one after it (1.18.0d against 1.18.0c: 620 changed lines
  became the 10 that changed and 4 new). 1.3.1: `-Crops` with nothing cut says so plainly.
- `AwanTV-apply-labels.py` 1.0.0: the counts and references around the adhkar and hadiths in each other language
  (tools/labels-<lang>.tsv); `AwanTV-build-hadith` 1.1.0 runs it after building.
- `AwanTV-texts-sheet.py` 1.0.0: the side-by-side sheet of a language (number, key, where it shows, Arabic,
  English, the language, notes), from the texts the release carries.

## [1.17.0]

### The app
- The language from Home (languages plan): a globe button in Home's top bar, between Settings and Screen
  off, opens the list of languages: "TV (its language now)", then each language by its own name, under "اللغة ·
  Language" (the word in the screen's language, then "Language", so one who cannot read the screen still finds it).
  Settings > Display & language opens the same list in place of the row's choices. After a pick the screens change
  at once and ask "Keep this language?": Keep, or Go back; with no answer they go back by themselves after
  10 seconds. In memory only: a restart in between keeps the language picked. In What's new.

## [1.16.0]

### The app
- The language model (languages plan, phase 2), no change on screen: `Languages` is the one list of the screens'
  languages (Settings' row, the settings file, the editor's choices, the import's check); `Text.rtl()` decides the
  direction, `arabicScript()` Amiri's bold, capitals, letter spacing and the comma, `dateLocale()` / `sortLocale()` the
  dates and the order of names; `t.ar` is left choosing the Arabic side of data written in both languages. The
  English counting ("1 minute", "2 minutes"...) is in the resources as one/other pairs chosen in code, so a new
  language brings its own words; the Arabic forms stay in code. The text dump: every line the same.
- A settings file with a language or digits this version does not know (from a newer TV) is no longer refused whole:
  the TV keeps its own, with a note ("Language kept: ur"), and takes the rest. A stored language it does not know
  counts as the TV's language, as Settings already showed it ("TV"); it read as English before. Unknown digits = by
  language.
- The crash screen's fallback (used only when the settings can't be read) no longer reads the resources for the
  comma in its last line: with Eastern digits it would have stopped (since 1.15.0).

### Tools
- `AwanTV-editor` 1.5.2: shows a TV's "Language kept" and "Digits kept" answers in its own words.
- `AwanTV-textdump` 1.1.0: the TV's own settings file ("file|" lines, its language and values) is listed apart in the
  report and never stops the release; its texts are checked through the editor's descriptions ("schema|").

## [1.15.0]

### The app
- Test builds only (a version ending in a letter): `TEXT_DUMP` writes every text the app shows, in Arabic and English
  with both kinds of digits, to text-dump.txt (`TextDump`: every method of `Text` with inputs from the app's own data,
  the remote's buttons, the adhan voices, countries, dates, the order of names, the language decision, the editor's
  descriptions and the settings file). The safety net of the languages plan: the strings move to Android's
  resources only when every line stays the same. A release answers "Unknown action".
- No change on screen: the words of `Text` are in Android's resources now, `res/values/texts.xml` (English) and
  `res/values-ar/texts.xml` (563 strings, 49 arrays; the languages plan, phase 1), read in the language chosen in
  Settings whatever the TV's own (`Text.of`: one Resources per language, kept). Every value is between double quotes
  (Android would merge spaces otherwise) and every part is filled by `Text.fill` (%1$s..., %% a percent sign), not
  String.format, whose numbers in Arabic could be Eastern against the digits setting. Still in code: the crash
  screen's texts (it must open even if the resources can't be read; `new Text(ar, eastern)` serves it alone), the
  Arabic counting forms (phase 2), and the data in both languages. Checked with the text dump: every line the same.
- No change on screen: the remote's button names (`Buttons.name`) and a download's percent (`LibraryActivity`) take
  their words from `Text`, like every other text (the languages plan, phase 1, step 1). Left for the language model
  (phase 2): the data written in both languages (cities, countries, stations, adhan voices, apps' Arabic names) and
  the editor's answers, which stay English.
- No change on screen: the language decision (`Screen.language`), the dates' locale (`Screen.dateLocale`) and the
  order of names (`Apps.collator`) are each in one place now, so the dump checks the very code the screens use.

### Build
- `checkTexts` before every build (app/build.gradle) stops it on a text missing in a language, a value not between
  double quotes or with a bare quote in it, a percent sign but %1$s... or %%, placeholders unlike English's, or an
  array of another length. A language listed in `unannouncedTexts` (gradle property) only warns.

### Tools
- `AwanTV-textdump` 1.0.0: takes the dump from a TV into state\textdump\<version>.txt and compares it with a golden
  one (`-Golden`); a changed or missing golden line = exit code 1, new lines are listed. The texts go to a report
  file, numbers only on the terminal.

## [1.14.2]

### The app
- Saved recitations keep their names after a reinstall: the names were kept only in the app's data, which Android
  wipes with the app, while the recordings stay in Music/Awan TV, so Station > Saved on the TV showed their keys
  ("r30-m30"). A name not kept is now found by its key in mp3quran.net's lists and kept again. The app carries its own
  copy of the lists (assets/mp3quran), used until the TV reads them, so this works without internet too; Music takes
  no file but audio, and a file in Documents left by an old install cannot be read after a reinstall. Without
  internet, the recitations screen also shows the reciters and the surah names from that copy.

### Build
- Every release build reads mp3quran.net's lists again into `app/src/main/assets/mp3quran` and says what changed
  (`app/build.gradle`, `readMp3quran`); without internet, or with an odd answer, the copy stays and the build goes on.
  A debug build reads them only when there is no copy yet.

### Tools
- `AwanTV-release` 1.3.0: refuses an AwanTV.apk without the lists.

## [1.14.1]

### The app
- The colours are Automatic by default (Lapis by day, Charcoal after Maghrib; Omar's choice): a TV that never chose
  turns Charcoal after Maghrib from now on, one that chose keeps its colours. What's new for 1.14.1 says how to keep
  one colour all day.

## [1.14.0]

### The app
- The iqamah countdown over apps is on by default (Omar: off, hardly anyone would find it; on, anyone can turn it
  off): a TV that never chose gets it with the update, one turned off stays off. Its first five times (a prayer each,
  a whole day) the card says where it is turned off (Settings > Iqamah). What's new for 1.14.0 says so too.
- The screensaver's photos are on by default too (they were off, and few found them); turned off, they stay off.
  What's new for 1.14.0 says where they are turned off (Display & language).
- The remote's buttons are in the settings file (`"buttons"`, the last part), marked with the TV (a hash of its
  ANDROID_ID, the same after a reinstall): Reset's copy, `AwanTV-settings -Restore` and an import to the same TV bring
  them back (they were lost with a reset or a reinstall); another TV passes them over ("Buttons kept: another TV's"),
  as each remote has its own codes. A TV older than 1.14.0 refuses a file with them: update it first. The editor
  leaves them out of what it sends.

### Tools
- `AwanTV-editor` 1.5.1: Send brings the remote's buttons back to the TV they came from (its own file, the backup taken
  before the send, bears the same mark, which also says it is 1.14.0 or later); to any other TV they are left out. A
  file opened with Restore a copy and sent to its own TV now restores them, as `AwanTV-settings -Import` did.
- `AwanTV-editor` 1.5.0: Save to file keeps the remote's buttons (with their TV's mark, from a TV from 1.14.0), so the
  file is the whole of the settings shown; they are never in what is sent to TVs. After Get from the TV, the line under
  the source says a full copy is saved in state\settings (each Get keeps the TV's whole file there).
- `AwanTV-settings` 1.1.0: its notes say the buttons come back to their own TV only.

## [1.13.1]

### The app
- Home opens again after any update of the app when it was in front as the update stopped it (the editor, the phone
  installer, AwanTV-install or adb alike): Home keeps whether it is in front (`home_front`, on the TV only), and the
  service, bound again within a minute of the update, opens it; another app the viewer was in is left alone. The TV's
  own home came up meanwhile with no event to say so (the 1.10.1 fix covers a boot only, and Update now has its own).
- The safety net's half hour without Home ends with an update of the app (a new version may fix what stopped the old
  one), and Home's button taken on a source box opens the TV's own home while it lasts: it did nothing before.

### Phone installer
- Find my TV opens Awan TV at the end of an update only when it (its Home or its screensaver) was in front before;
  another app the viewer is in is left alone, and Set up again (nothing stopped) leaves the TV as it is. A first
  install still opens it, for the location question. The timeline's last step says "left as it is".
- The newer-installer card says before the press that the installer app will close after the update and to open it
  again, as Android stops an app it updates (F-Droid and Signal close the same way); the installing line says it too.
- "الهاتف" for the phone throughout the Arabic texts (it was "الجوال" in four).

### Tools
- `AwanTV-editor` 1.4.4: dumpsys's "Broken pipe" note when reading what the TV shows is quieted (as AwanTV-install
  1.2.0 and the phone installer).
- `AwanTV-install` 1.2.0: an update opens Awan TV at the end only when it (its Home or its screensaver) was in front
  before it; another app the viewer is in is left alone, and the very same APK (nothing installed) leaves the TV as it
  is. A first install hands the replaced screensaver over and opens it, as before.

## [1.13.0]

### The app
- A safety net (`Crash`, `CrashActivity`): an error nothing caught is written down (files\crash.txt) and, when one
  of Awan TV's screens was in front, a screen of its own opens in place of Android's "keeps stopping", in its own
  process: Restart, the TV's own home, and Details to report (the version, Android, model, the error and its first
  frames, no address, and a code to the project's new issue page). Two stops within two minutes hold Home off for half
  an hour (HomeGuard leaves the TV's own home up), so a Home that stops as it opens cannot loop; the TV's own home
  chosen there holds it too, Restart lets go. It closes by itself after ten minutes without a key. A stop in the
  background is only written down. About > App status shows the last one. The idea is CustomActivityOnCrash's
  (Ereza, Apache 2.0), written here without the library. Test builds: `TEST_UPDATE --ez crash true`.
- What's new for 1.13.0.

### Phone installer
- My TVs opens Awan TV after an update only when its Home (or its screensaver) was in front before it, as the
  editor 1.4.3 does; never over another app the viewer is in.
- My TVs ticks an up-to-date TV too, to put it right, as the editor's Update TVs: with this very APK on it (by
  SHA-256) nothing is sent and it is only set up again; another file with the same number is installed again, then
  set up. The button, the titles and each result say which was done ("Run on the ticked"). A newer TV has no box.
- Android's answer to "Update the installer" goes to the installer's screen (an activity PendingIntent) rather than a
  broadcast. Meant to open the new installer after it updates itself, it does not: Android gives the install's answer
  no right to open a screen (PackageInstallerSession, on purpose). The installer still closes after updating itself.

### Tools
- `AwanTV-editor` 1.4.3: after an update, Awan TV's Home opens again on a TV where it (or its screensaver) was in front
  before it: the install stops the app, and the TV's own home stayed up until a key. Another app the viewer is in is
  left alone.
- `AwanTV-editor` 1.4.2: opening the editor while adb was not running yet (after the computer started) stopped it with
  "daemon not running; starting now", adb's own note on starting; adb is now started without that ending the editor.
- `AwanTV-editor` 1.4.1: a TV on Wireless debugging whose serial did not answer got a second card under its wireless
  name (adb-<serial>-…._adb-tls-connect._tcp); its serial is now taken from that name, a TV that answers nothing is
  skipped, and such a card saved before is moved to its TV (a name you gave it kept).
- `AwanTV-editor` 1.4.0: Update TVs ticks an up-to-date TV too, to put it right: with this very APK on it (by
  SHA-256, as AwanTV-install) nothing is sent and it is only set up again (permissions, TCL's app ops, the service
  started and checked); another file with the same number (a local build) is installed again, then set up. The button
  and the window then say "Run on the ticked", and each result says which was done. A TV with a newer version still
  has no box.
- `AwanTV-editor` 1.3.3: a TV that went offline while being read no longer gets adb's error text ("device offline",
  PowerShell's NativeCommandError) saved as its model or name: it shows as not connected, what was saved of it stays,
  and text an older editor saved that way is cleared from state\editor\tvs.jsonc on start.
- `AwanTV-editor` 1.3.2: Update the ticked shows its progress in a window that keeps moving: the download with its
  percentage, megabytes and time left, the signature check, then each TV with its step of three (install, permissions,
  the service) and the time taken, ✓ or ✗ with why; Stop after this TV; the result in the same window, with Try again
  for those that failed. The work runs in its own runspace, so the editor no longer looks frozen.
- `AwanTV-editor` 1.3.1: Connect waits a full minute for Allow on the TV, and tells a TV that took the connection but
  stays "offline" (unplug it for a minute) from one that was not allowed; the connection left half-open is closed.
- `AwanTV-editor` 1.3.0: Update TVs, second in the list: each connected TV's Awan TV version against what is sent,
  GitHub's latest release (its AwanTV.apk downloaded to state\editor\apk at the update) or the local build in
  app\release (shown only when signed with the release key), the viewer's choice, kept in state\editor\update.json;
  both checked as signed with the release key and of the version they say. None ticked at first; Update the ticked
  installs over each TV, then the install tool's permissions, app ops and service (bound), without its screensaver
  steps and without opening Awan TV on the TV; each TV's result and Try again for those that failed.
- `AwanTV-editor` 1.3.0: Connect a TV (TVs now, Update TVs): its address (5555 when no port), "Allow" waited for on the
  TV, and Wireless debugging's pairing (port and code) when it does not connect. A TV met before connects from its
  card: its known address, else its new Wireless debugging port heard by adb mdns (the service name carries its serial).

## [1.12.0]

### Phone installer
- My TVs (a second button on the guide): the TVs this phone installed Awan TV on or set up again (kept on the phone,
  saved at Find my TV's confirmation by the TV's serial), each with Awan TV's version on it, read without anything
  shown on the TV (it already trusts this phone: no other TV on the network is asked). Tick those needing an update
  (none at first; "Tick those needing an update" does it) and Update the ticked: one after another, the Awan TV the
  installer carries, as Find my TV's update (install over it, the setup steps), with no question on the TVs, then each
  TV's result and Try again for those that failed. Held, a TV leaves the list. Wireless debugging's port is heard by
  mDNS meanwhile; a TV at another address or that forgot the pairing says to add it again.

## [1.11.0]

### Tools
- `AwanTV-release` 1.2.0: refuses an installer whose version is not the app's (its own update compares that number,
  and installerTestVersion in gradle.properties, for testing that update, must not reach a release).

### Phone installer
- About (the "i" by the version at the top of the guide): the installer's version and the Awan TV it installs, its
  updates (Check now), Developed by OMAR A ALHWAIRTH with awanpro.app, the project and its releases, Afnon's installer
  it is built on, and the licence texts it carries (assets/licenses), each readable.
- Update the installer: a newer installer on GitHub is offered with a button, not only a link. It downloads that
  release's AwanTVInstaller.apk into an Android install session (as Awan TV's Update now), asking once for "Install
  unknown apps" for this app, then Android's own "Update this app?". Find my TV waits while it downloads.
- Its version on every screen, at the top opposite the language button: "Awan TV 1.10.1" is both the installer's
  version and the Awan TV it installs (the two are built with one number). Before, it showed only after connecting.

## [1.10.1]

### The app
- After the TV restarts, Home comes up by itself: the TV's own home screen came up before Awan TV's service started
  and stayed until a key was pressed. Within five minutes of a boot, once per boot, the service's start opens Home
  over it (as Masjid TV does), unless Home is already up or Settings > Awan TV leaves Home off.

## [1.10.0]

### The app
- The editor's TVs now: `SettingsTransfer` answers STATUS (the radio on or off, its station's id and names, whether
  another app plays sound now) and RADIO (`--es do play|stop`). Play starts the radio at its chosen station, but not
  over sound from another app (Android gives sound to one, so a video would stop) unless `--ez force true`; then it
  answers "Busy". Both over adb only, as the settings.

### Tools
- `AwanTV-editor` 1.2.3: TVs now, first in the list: each TV's radio (playing, stopped, other sound, or a version
  before 1.10.0), Play or Stop beside it, and Play / Stop on the TVs ticked there (kept on this computer, none at
  first, so a TV someone else uses is never played by surprise). A TV playing something else asks first (Play anyway);
  on the ticked TVs, those playing another app's sound are listed after the answers, each with its own box, none
  ticked. The page needs no settings shown, and Working… no longer stays on while a question waits.

## [1.9.0]

### The app
- Saved recitations from the settings editor: the export writes `awantv-library.json` beside the settings (storage,
  saved and downloading recordings, the queue, the stations, the kept reciter list; no network), and a file sent may
  carry "downloads" (each recording with its server, names and surahs, so a TV that never read the list takes it,
  only from mp3quran.net's servers), queued as when picked on the TV. Nothing is deleted from the computer.
- The station from the editor: any station this TV can play, a reciter channel in its list or a recording saved here
  (or queued in the same file); otherwise it keeps its own and the answer says why. The answer to an import is now in
  parts (" | "): settings changed, surahs queued, station kept.
  A station sent while the radio plays switches it at once, as when picked on the TV (1.9.0a only renamed it).

### Tools
- `AwanTV-editor` 1.1.1: the station picked as on the TV, its group first (Stations, Reciters, Saved), then one of that
  group: the reciter channels' long list no longer keeps the saved recordings out of reach.
- `AwanTV-editor` 1.1.0: a Saved recitations page after Radio (the source TV's storage, saved recordings, download and
  queue; Add a recitation: reciter, recording, the whole Quran or chosen surahs, sent with Send) and the station as a
  list by group (stations, reciter channels, the source TV's saved recordings, those added to download). A TV before
  1.9.0 gets neither, and the result says so; "Save to file" carries no recordings and no saved station.
- `AwanTV-editor` 1.0.1: a TV on a test build (1.8.0a) no longer stops the list of TVs with "Cannot convert value
  \"0a\" to type System.Int32"; such a version counts as older than its release, as in the app.

## [1.8.0]

### The app
- Morocco: the Ministry of Habous and Islamic Affairs' method («وزارة الأوقاف والشؤون الإسلامية، المملكة المغربية»).
  The ministry publishes a timetable for each of its 191 localities, not its method, so the rules are the ones
  recovered from its tables (Afnon 0.8's `Habous`, which Mihrab's reading agrees with): Fajr 19° rounded down to the
  minute, Isha 17°, Asr one shadow length, Dhuhr five minutes after the transit, sunrise and Maghrib at each
  locality's own horizon, the rest rounded. Each locality's point and horizons are fitted to the ministry's tables by
  `tools/AwanTV-build-habous.py` (new): on 2026-09-26 to 2026-11-11 its published minute for 99.03% of 53,862 times,
  never more than a minute off. The usual method elsewhere (AlAdhan's Morocco: 19°/17°, Maghrib at sunset) gave
  Dhuhr 5 minutes and Maghrib about 6 minutes before the ministry's.
- A place in Morocco (Laayoune and Dakhla too) starts on it and takes the table of the ministry's locality named like
  it near by, else the nearest: Settings > Prayer times > Ministry table (shown only then) lists the ten nearest, with
  their distance, to follow another. Chosen by hand elsewhere, it computes at the place itself with the ministry's
  coastal horizon, so Maghrib never comes before the ministry's would.
- Another place: the method line shrinks to fit its box instead of being cut (Morocco's ministry with "check it after
  saving"), and a method whose name says its country (Oman's, Morocco's) no longer adds "as in <country>".
- Calculation methods are Awan TV's own `Method` (after Afnon 0.8's), not adhan-java's list, so a method adhan-java
  lacks needs no stand-in; saved choices keep their names (OMAN_AWQAF too).

### Tools
- `AwanTV-build-habous.py` 1.0.0 builds `assets/habous/localities.tsv` from a copy of the ministry's tables (see above)
  and refuses to write when under 97% on the minute or anything two minutes off.
- `AwanTV-release` 1.1.0 guards Update now on the TVs already out: it refuses an APK signed with another key than the
  release key, an AwanTV.apk whose versionName or versionCode is not awanVersion's (an older build left in
  app\release), and a version older than the one published on GitHub; it then prints the tag and the file names that
  Update now downloads.

## [1.7.1]

### The app
- After Update now, Home comes back by itself. On a Google TV Streamer the 1.7.0 update went in with no question,
  which ended the old app; the TV's own home screen came up and stayed, since nothing changed on screen for the
  service to see. The install now notes that Home was in front, and the new version's service opens Home when it
  connects (within ten minutes; not when the viewer was in another app). From 1.7.1 on: an update from 1.7.0 still
  leaves the TV's home screen up once (Home's button brings Awan TV back).
- Test builds only: `TEST_UPDATE --ez updated true` marks the TV as just updated from Home.

## [1.7.0]

### The app
- Update now: the new version's window (over Home, and Settings > About's) downloads the release's AwanTV.apk from
  GitHub when pressed and installs it on the TV itself (SelfUpdate, after Afnon's feat/in-app-updates): a progress bar
  (the window may be left; the download carries on, and Home shows it again), Cancel, then Android's install (on
  Android 12+ perhaps with no dialog; otherwise its own "Install?", one OK). Android refuses anything not signed with
  Awan TV's key, cut short or older. A failure offers Try again. Nothing is downloaded unless pressed. Seen on a
  Google TV Streamer (Android 14): Android asks "Update this app?" even so (the app was installed over adb, so it isn't
  its own installer), its focus on Cancel: the window says to move to Update. Back while downloading leaves the window
  closed until Home comes back.
  Android 14 blocks its question while no window of Awan TV shows (the viewer went into another app during the
  download): the question is kept, and the window offers Continue installing when Home comes back.
- Without the install permission (installed by hand), no Update now: the window says to install with the phone
  installer or from Windows (ways 1 and 2) to get every feature.
- What's new lists 1.7.0.
- Test builds only: `TEST_UPDATE --es download 1.6.0` makes Update now download that real release instead.

### The installers
- The phone installer and AwanTV-install 1.1.0 grant the install permission (`REQUEST_INSTALL_PACKAGES` app op), a
  new step "Updates with one press on the TV".

## [1.6.0]

### The app
- TCL's remote buttons: the entry a custom button replaces (TCL's own, or another app's) is kept in the TV's own
  settings (`AwanSavedKey_<code>`, as Afnon's `AfnonSavedKey_` in its #41), no longer in the app's, where it was
  lost when Awan TV was removed some other way than Remove Awan TV or AwanTV-restore: the button then did nothing.
  What older versions kept is moved over once.

### The tools
- AwanTV-restore 1.2.0: puts that saved entry back (copied on the TV itself, so its quotes stay), and only touches
  entries carrying Awan TV's full mark.

## [1.5.0]

### The app
- A newer version is told over Home, as What's new is (after it, never with it, a reminder or the radio card; never
  over the screensaver or the call, which run with no one at the remote): the version, this TV's, why to update, the
  QR code for the release page and how to update. Once for each new version, then a week after it was closed until
  the TV has it, or until Don't remind me of this version (that version only; the next one shows again).
- A gold mark on Home's Settings button and on Settings > About while a newer version is out, until the TV has it.
- The daily question to GitHub is always asked: Settings > About > New versions is gone (an older settings file's
  `update_check` is passed over). Check for updates now still asks at once.
- Settings > About's new-version window says why to update, and its code opens the download page.
- AwanTV-shot 1.0.1: an empty screenshot is said, not kept.
- Test builds only: `adb shell am broadcast -a app.awanpro.tv.TEST_UPDATE --es version 1.9.0 -n
  app.awanpro.tv/.SettingsTransfer` pretends a newer release, to see the notice.
  With `--ei days_ago 8` instead, the notice counts as closed eight days ago (the weekly reminder at once).
- Over What's new and the newer version's notice, the key hints are theirs (OK Choose, Back Close), not Home's.
- What's new lists 1.5.0.

## [1.4.0]

### The app
- Home's radio card: Station, between Play and Screen off, lists the stations, the recitations and the saved
  recordings (as Settings > Radio does) over Home; the choice plays at once.
- Remote buttons: a customised button can also be held (half a second, Android's long press) to do something else:
  nothing, one of Awan TV's screens, or an app. OK on a button's row asks which: its press or its hold. Only a button
  whose press is customised can have a hold (its press taken, what it did before can't be given back). On a TCL TV
  its remapping does both (the press on release, the hold on its long press); elsewhere the key filter times it.
- Two adhan voices: Al-Masjid an-Nabawi, and Ragheb Mustafa Ghalwash (meypod/audio_files). Settings > About > Sources
  and licences names both sources.
- What's new lists 1.4.0.
- THIRD_PARTY.md: Oman's radio and the Saudi Broadcasting Authority's stations (added in 1.3.0), and the new adhan
  recordings.

### The tools
- AwanTV-fetch-adhans (.ps1 and .sh) 1.1.0: fetches the two new voices from meypod/audio_files too.

## [1.3.0]

### The app
- Calculation method «وزارة الأوقاف والشؤون الدينية، سلطنة عُمان» (Afnon 0.7): Fajr and Isha at 18°, the standard Asr,
  five minutes on Dhuhr, Asr and Maghrib, fitted by Afnon to the ministry's timetable. Omani cities and places take
  it (they were on the Muslim World League); Muscat sits where the ministry's Muscat timetable matches.
- Settings > Radio: Oman's Holy Quran Radio, and the Saudi Broadcasting Authority's Quran Radio and Nida Al-Islam
  (Afnon 0.8).
- What's new lists 1.3.0.

### The installer (phone)
- It says when a newer installer is out (one question to GitHub when it opens), with the link to get it.
- A TV that can't be reached gets the reason and what to do: no answer from its debugging port (unplug it a minute,
  or switch USB debugging off and on), refused (Wireless debugging's port changed), or not on this network.
- Install it again, beside Set up again, when the TV has the very version it carries.

## [1.2.0]

### The app
- Settings > Prayer times > Adjust the times and the date: each of the six times up to 30 minutes earlier or later
  (Afnon 0.8's, as in meypod/al-azan), for a masjid a little off the calculation; the adhan, iqamah, reminders and
  screens follow. The Hijri date's adjustment moved onto the same page. In the settings file and the editor too.
- A newer version opens a window in Settings > About (from Source and updates, or when Check for updates now finds
  it): the version, this TV's, a QR code for the release page, how to update, and Open the release page where the TV
  has a browser.
- What's new: once over Home after an update, every version the TV had not seen, until OK or Back (a layer of Home's
  own, so returning to Home never closes it); all of them in Settings > About > What's new. Never on a new install.
- Settings > About fits on the screen again (it had grown past it in 1.0.1): its sources and licences are on a page
  of their own, Sources and licences.
- In Arabic a path reads with an arrow ("حول التطبيق ← حالة التطبيق"): the "›" read as a comma.

### The tools
- `AwanTV-build-qr` (1.1.0) also builds the release page's code.

## [1.1.0]

### The app
- Settings > Device settings > Remote buttons: a button on any remote (the TV's own or a Bluetooth one), pressed
  once, opens an app, the screensaver, Today & Qibla or the radio, or does nothing. Add a button waits ten seconds
  for it; a button the TV keeps for itself says so. Off until the first button, which first says what it costs:
  while one is set every press passes through Awan TV, so holding Volume moves it one step. The arrows, OK, Back,
  Home, Volume and Power are never taken. Reset settings clears them; the settings file leaves them out (remotes
  differ between TVs).
- On a TCL TV the buttons go to TCL's own button remapping, so remote presses do not pass through Awan TV and holding
  Volume works as before (no warning there). TCL's own entries are never emptied; one a button replaced comes back
  when the button goes. Remove Awan TV gives the buttons back to the TV first.

### The tools (Windows)
- `AwanTV-restore` gives a TCL TV's remote buttons back to the TV.
- `AwanTV-release` refuses a test build's version (one with a letter, such as 1.1.0b).

### The installer (phone)
- A test build (a version with a letter, such as 1.1.0b) counts as older than its release, so the release updates it.

## [1.0.2]

### The app
- Check for updates now and New versions are in Settings > About, under Source and updates where a new version shows
  (they were in Settings > Network). The settings file and the editor have New versions under About too.
- Source and updates opens the newest release's page, with its files, when a newer version is out.

### The installer (phone)
- It reads the TV's Awan TV before anything is sent, and the confirmation says what it will do: install (none on the
  TV), update (an older one, its settings and recitations kept), set up again (the same version: the service,
  permissions and screensaver put right in seconds, nothing sent), or nothing (a newer one: get the latest installer).
- Its icon shows what it does, a TV with the arch inside the gold arch, as an adaptive icon that no phone's mask cuts,
  with a monochrome layer for themed icons.

### The tools (Windows)
- `AwanTV-install` sets up again without installing when the TV has this very APK, and stops with a clear message
  when the TV has a newer Awan TV.

## [1.0.1]

### The app
- Settings > Network > Check for updates now: asks GitHub at once, even with New versions off, and says beside it
  whether a newer version is out, this is the latest, or GitHub could not be reached.

### The tools (Windows)
- `AwanTV-release` refuses an AwanTV.apk that carries the spoken reminders: their Microsoft voice is for one's own
  build, never a release.

## [1.0.0]

The first public release.

### The app
- Home over the TV's own home screen (nothing disabled), with the Hijri month by Umm al-Qura, the next prayer and
  a countdown to Ramadan, the two Eids and the Day of Arafah.
- Screensaver with all six times and their iqamah, both dates, the weather, the morning and evening adhkar (Hisn
  al-Muslim), a hadith from al-Bukhari and Muslim, the adhkar after the prayer, and dimming at night.
- Iqamah (the country's, minutes after the adhan, or a fixed time; Ramadan apart), Friday prayer, imsak in
  Ramadan, Fajr's adhan level, and an optional iqamah countdown over other apps.
- The first start asks for the location, offering the TV's own (from its location service, by its coordinates, named
  after the nearest place) or the city of its time zone; until one is set, Home says so in red.
- Any city in the world (GeoNames, Arabic or English), colour themes, background photos, saved recitations from
  mp3quran.net, and what runs: all, Home only, the screensaver only, or off.
- Settings > About: QR codes for awanpro.app and the repository, a newer version when one is out (Settings >
  Network > New versions asks GitHub once a day; nothing is downloaded), and the links open where the TV has a browser.
- Settings > About: App status and Reset settings (two confirmations; the settings kept on the TV first, the last
  five copies).
- Saved recitations go to Music/Awan TV, so they stay when the app is removed and are found again after a
  reinstall; Remove Awan TV asks whether to delete them too.
- From Afnon: offline prayer times, the adhan over any app, reminders (also spoken), Qibla, Quran radio.

### The installer (phone)
- Awan TV Installer: an Android phone app that installs Awan TV on a TV with no computer. It shows the steps on
  the TV (Developer options, debugging), finds the TV on the Wi-Fi (by its own name and model when it
  announces them, as for casting), connects with "Allow debugging?" or a pairing
  code, and runs the same setup as `AwanTV-install`. It carries the Awan TV it installs, so it needs no internet.
  It asks before installing, naming the TV as the TV itself reports it, and names it again at the end.
  Arabic or English whatever the phone's language (a button before connecting); links to the releases page and
  awanpro.app.
  Built from Afnon's installer.

### The tools (Windows)
- `AwanTV-install` / `AwanTV-restore`: install, grant, set the screensaver and turn the service on; and undo it.
  The install takes the signed release APK when there is one, and says what to do when the TV has Awan TV signed
  with another key. `AwanTV-restore -Uninstall` keeps the saved recitations unless `-RemoveRecitations`.
  Double-click `AwanTV-install.cmd` / `AwanTV-restore.cmd`; with no TV connected they ask for
  its address (and a pairing code for Wireless debugging), and with no adb on the computer they offer Google's
  Platform-Tools (after its terms).
- `AwanTV-Windows.zip` on the release page: the app with the install and restore tools in one `AwanTV` folder.
  `AwanTV-release` builds it with the two APKs, whose names stay the same in every version.
- `AwanTV-settings`: every setting in one commented file (`-Export`, `-Import`, `-Restore`), with a backup first.
- `AwanTV-editor`: one window to get, change and send the settings to one or more TVs, in Arabic or English.
- `AwanTV-clean`, `AwanTV-tv-backup`, `AwanTV-diag`, `AwanTV-shot` and the asset builders and fetchers.
- Every tool takes a TV by a part of its serial (a wireless "name (2)" too), and starts adb outside the project
  folder so that folder can always be moved or renamed. `-Import` without `-File` takes that TV's own newest file.

### Signing
- Releases are signed with one release key; its certificate SHA-256 is in README, to check a downloaded APK.

### Adhan recordings
- Not in the source: their collection, abodehq/Athan-MP3, states no licence. `AwanTV-fetch-adhans` downloads them
  before building; the release APK carries them, credited to the collection (also in Settings > About), and a
  recording is removed on request by whoever holds its rights.
