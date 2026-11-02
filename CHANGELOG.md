# Changelog

All notable changes to Awan TV. Versions match `awanVersion` in `gradle.properties`.
Awan TV is built on [Afnon](https://github.com/egyjs/afnon-android-tv-launcher) (MIT), up to its 0.5; Afnon's own
history is in its repository.

## [Unreleased]

## [1.0.2]

### The app
- Check for updates now and New versions are in Settings > About, under Source and updates where a new version shows
  (they were in Settings > Network). The settings file and the editor have New versions under About too.

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
