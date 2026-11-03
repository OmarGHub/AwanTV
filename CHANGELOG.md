# Changelog

All notable changes to Awan TV. Versions match `awanVersion` in `gradle.properties`.
Awan TV is built on [Afnon](https://github.com/egyjs/afnon-android-tv-launcher) (MIT), up to its 0.5; Afnon's own
history is in its repository.

## [Unreleased]

### The app
- Settings > Prayer times > Adjust the times and the date: each of the six times up to 30 minutes earlier or later
  (Afnon 0.8's, as in meypod/al-azan), for a masjid a little off the calculation; the adhan, iqamah, reminders and
  screens follow. The Hijri date's adjustment moved onto the same page. In the settings file and the editor too.
- A newer version opens a window in Settings > About (from Source and updates, or when Check for updates now finds
  it): the version, this TV's, a QR code for the release page, how to update, and Open the release page where the TV
  has a browser.
- What's new: once over Home after an update, every version the TV had not seen; all of them in Settings > About >
  What's new. Never on a new install.
- Settings > About fits on the screen again (it had grown past it in 1.0.1): its sources and licences are on a page
  of their own, Sources and licences.

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
