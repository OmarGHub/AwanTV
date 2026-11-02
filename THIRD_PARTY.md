# Third-party material

The MIT licence in `LICENSE` covers the code: Awan TV's changes, Afnon (its base) and the parts that follow Masjid TV.
These parts belong to others.

| What | Where | Licence |
|---|---|---|
| [adhan-java](https://github.com/batoulapps/adhan-java) by Batoul Apps | Gradle dependency | MIT, Copyright (c) 2016 Batoul Apps: `app/src/main/assets/licenses/adhan-java.txt` |
| [Amiri](https://github.com/aliftype/amiri) by Khaled Hosny | `app/src/main/res/font/amiri_*.ttf` | SIL Open Font Licence 1.1: `app/src/main/assets/licenses/OFL.txt` |
| [Alexandria](https://fonts.google.com/specimen/Alexandria) by Mohamed Gaber and Julieta Ulanovsky | `app/src/main/res/font/alexandria_*.ttf` | SIL Open Font Licence 1.1: `app/src/main/assets/licenses/OFL.txt` |
| Adhan recordings from [abodehq/Athan-MP3](https://github.com/abodehq/Athan-MP3), which gathered them from [islamweb.net](https://www.islamweb.net) and [TvQuran.com](https://www.tvquran.com) (named in the files' own tags) | Not in the source. `tools/AwanTV-fetch-adhans.ps1` (or `.sh`) downloads them before building; the release APK carries them | None stated. Credited here and in Settings > About; any recording is removed on request from its rights holder. |
| Morning and evening adhkar: the text of Hisn al-Muslim by Sa'id al-Qahtani (hisnmuslim.com), as published in [Seen-Arabic/Morning-And-Evening-Adhkar-DB](https://github.com/Seen-Arabic/Morning-And-Evening-Adhkar-DB), commit 29d7623 | `app/src/main/assets/adhkar/adhkar.json` (text unchanged; short source lines and the night flag added, and the basmala before al-Ikhlas, al-Falaq and an-Nas, as Hisn al-Muslim prints them) | MIT, Copyright (c) 2024 Seen Arabic: `app/src/main/assets/licenses/Seen-Arabic.txt` |
| Hadiths, the supplication after the adhan and the adhkar after the prayer: the Arabic text of Sahih al-Bukhari and Sahih Muslim from [fawazahmed0/hadith-api](https://github.com/fawazahmed0/hadith-api) (editions ara-bukhari, ara-muslim) | `app/src/main/assets/hadith/hadith.json`, built by `tools/AwanTV-build-hadith.py` from `tools/hadith-list.txt` (the books' text, vocalised; only stray full stops and the spelling of a final ى with a vowel touched) | The Unlicense (public domain) |
| City list from [GeoNames](https://www.geonames.org) (cities15000), as packaged in [geonamescache](https://github.com/yaph/geonamescache) 3.0.2 | `app/src/main/assets/cities/cities.tsv`, built by `tools/AwanTV-build-cities.py` (names, coordinates, country, population; Arabic names from the hand-written city list or GeoNames' alternate names) | CC BY 4.0 (GeoNames); geonamescache is MIT. Credited in Settings > About. |
| Saved recitations from [mp3quran.net](https://mp3quran.net) | Downloaded by the viewer to the TV (Settings > Radio > Saved recitations), never in the source or the APK | Its rights notice lets any developer use its material and links (mp3quran.net/ar/privacy). The recitations remain their reciters'. Removed on request from a rights holder. |
| Weather data from [Open-Meteo](https://open-meteo.com) | Fetched while Home or the screensaver is on screen, for the chosen place's coordinates; the last reading kept on the TV | CC BY 4.0, credited in Settings > About. Free for non-commercial use. |
| Spoken reminder lines (`tools/announce-lines.txt`) voiced by Microsoft's ar-SA-HamedNeural voice through [edge-tts](https://github.com/rany2/edge-tts) | Not in the source and not in the release APK. `tools/AwanTV-fetch-announce.ps1` (or `tools/fetch-announce.sh`) records them for your own build only | Microsoft's speech service terms. Without them the reminders show without a voice. |
| Quran radio stations: إذاعة القرآن الكريم in Cairo, Nablus, Qatar (two channels) and Jordan, and Shabab FM Quran (Nablus) | Played from each station's public stream, never stored | Belongs to each station. Awan TV and Afnon are not affiliated with any of them. |
| Reciter channels from [mp3quran.net](https://mp3quran.net) (`backup.qurango.net`) | Played from the public stream, never stored | Belongs to mp3quran.net and the reciters. Awan TV and Afnon are not affiliated with it. |
| Background photos from [Unsplash](https://unsplash.com) by A Miah, Aaron Burden, Ali Arif Soydas, Ali Rizvi, Alkhatab Al Saqri, Djonk Creative, Ekrem Osmanoglu, Haidan, Hakam Magdea Fardana Ansie, Hongbin, Husien Bisky, Ian Taylor, Jan Corba, Jessica Favaro, Jordan Whitt, Katerina Kerdi, Mohammad Dadkhah, Niilo Isotalo, Salah Regouane, Sam Riz, Sebastian Unrau, Sergey Shmidt, Simon Infanger, Stacey Franco, Tolga Deniz Aran, Yuval Zukerman | `app/src/main/assets/photos/` (cut to 16:9, 1920x1080 WebP by `tools/AwanTV-build-photos.py` from `tools/photos-list.txt`; each photo's photographer and address in `photos.json`) | Unsplash License: free to use, no permission or credit needed; credited here anyway |
| TV clean lists from [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (examples/tcl-c755-google-tv, tcl-qm6k-google-tv, google-tv-streamer-4k-kirkwood, tcl-beyondtv2-android-11, google-chromecast-gtv-4k, google-chromecast-gtv-hd) | `tools/clean/tcl-smart-tv-pro.txt` (the lines both TCL lists share in their tracking, promo, demo and preload parts), `tools/clean/google-tv-streamer.txt`, `tools/clean/tcl-beyondtv-android11.txt` and `tools/clean/google-chromecast-gtv.txt`, used by `tools/AwanTV-clean.ps1` | MIT, Copyright (c) 2026 Gathin: `tools/clean/LICENSE-CleanYourTV.txt` |

## Awan TV Installer (`installer/`, the phone app)

Started from Afnon's installer (MIT, in `LICENSE`). Its full source is here, so it can be rebuilt with modified
versions of these libraries. The licence texts travel inside it, in `installer/src/main/assets/licenses/` (an index
in `README.txt` there).

| What | Where | Licence |
|---|---|---|
| [libadb-android](https://github.com/MuntashirAkon/libadb-android) 3.1.1 by Muntashir Al-Islam | Gradle dependency (JitPack) | GPL-3.0-or-later or Apache-2.0; used under Apache-2.0: `Apache-2.0.txt` |
| [spake2-java](https://github.com/MuntashirAkon/spake2-java) 2.2.1 by Muntashir Al-Islam, brought by libadb-android | Gradle dependency (JitPack) | LGPL-3.0: `LGPL-3.0.txt` with `GPL-3.0.txt` |
| [Bouncy Castle](https://www.bouncycastle.org) (bcprov), brought by libadb-android | Gradle dependency | MIT: `BouncyCastle.txt` |
| AndroidX Annotation, brought by libadb-android | Gradle dependency | Apache-2.0: `Apache-2.0.txt` |
| Kotlin standard library and JetBrains annotations, brought by the libraries above | Gradle dependency | Apache-2.0: `Apache-2.0.txt` |
| [Conscrypt](https://github.com/google/conscrypt) 2.5.3, with BoringSSL built in | Gradle dependency | Apache-2.0: `Apache-2.0.txt`, `Conscrypt-NOTICE.txt`, with its Netty and Apache Harmony parts in `LICENSE.netty.txt` and `LICENSE.harmony.txt`; BoringSSL: the OpenSSL, SSLeay and ISC licences in `BoringSSL.txt` |
| Alexandria and Amiri | `installer/src/main/res/font/` (the app's own) | SIL Open Font Licence 1.1: `OFL.txt` |

The stream addresses are in `Radio.STATIONS`. A station or reciter is removed on request from its rights holder.

The licence texts travel inside the app, in `app/src/main/assets/licenses/`: `AwanTV.txt` (a copy of `LICENSE`:
Awan TV, Afnon and Masjid TV), `adhan-java.txt`, `OFL.txt` (the two fonts), `GeoNames.txt` (the city list, CC BY
4.0, with the changes made) and `Seen-Arabic.txt` (the adhkar). Settings > About names Afnon and Masjid TV, the weather
and the city list with their licences. The fonts are bundled unchanged; under the OFL they may be bundled with
software, but not sold on their own.
