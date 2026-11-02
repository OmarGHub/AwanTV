# Afnon — Mihrab home screen · implementation handoff

Paste this into Claude Code with the `design/` folder in the repo.

## Target
Android TV launcher (HOME intent) for a TCL 55P7K: 1920×1080, ~1.8 GB RAM, weak GPU, D-pad remote only
(Up/Down/Left/Right/OK/Back/Home). Fully offline. Arabic (RTL, default) and English (LTR).

## Source of truth
`design/*.dc.html` are the screens, written in a templating format (`{{holes}}`, `<sc-for>`, `<sc-if>`,
`<dc-import>`) that needs the design canvas runtime, so they won't render on their own in a browser. Read them
as spec: layout and inline styles are in the markup; data, strings (tables `t`) and focus states are in the
`renderVals()` block at the bottom of each file. The rendered canvas is the visual reference.
- Core screens (both languages via a `locale` prop): `Home`, `AllApps`, `PrayerDay`, `ApproachVideo`,
  `Screensaver`, `Inputs`, `Settings`.
- `Home` also takes `mode` = normal | approach | now | friday | ramadan and `focus` = panel | app.
- `*_EN.dc.html` / `*_AR.dc.html` are thin wrappers that set those props.
- Spec boards: `Tokens`, `Components`, `FocusMap`, `RTLNotes`. `canvas.json` is the board index.
- `Main`, `B-Mihrab`, `C-DayLine` are the early exploration directions — ignore for implementation.

## Tokens (px at 1080p)
Colours: lapis-900 #0C1B33 ground · lapis-800 #12264A surface · lapis-700 #1A3360 raised ·
gold-400 #D8B46A accent/focus fill · gold-200 #F0D79C approaching · plaster-100 #EFE7D6 text/ring/now ·
mist-400 #9FB0CC secondary. Screensaver: #000, text #A69F90.
Type: Marcellus (EN display), Amiri 700 (AR display), Amiri 400 (sacred text, gold), Alexandria 300–600 (UI).
Sizes 150/96/88/64/52/40/30/26/22/20; minimum 20. No letter-spacing on Arabic.
Spacing 8·12·16·24·32·48·64·96. Safe area 96 inline / 56 block. Radii 8/14/18/24/pill.
Focus: gold fill + 4 px plaster ring, 6 px gap, scale 1.06 (rows 1.04). 120 ms cubic-bezier(.2,.8,.2,1).
Motion: transform/opacity only; no blur, no video, no shadows beyond a solid ring.

## Behaviour
- Arch (600×900, start side) always shows the next prayer + countdown; its crown holds Qur'an 4:103 and
  is never covered or focusable. OK on the arch → Prayer day.
- Focus map and Back/Home/hold-OK rules: see `FocusMap.dc.html`. In RTL all Left/Right swap.
- Reminder N min before (10/15/off): toast on Home (takes focus); over other apps a non-focusable
  overlay banner top-end, 12 s, hold Home opens Afnon (needs SYSTEM_ALERT_WINDOW / overlay permission).
- At prayer time: full call screen, "Play adhan" focused, Back dismisses, auto-close after 10 min.
- Friday: Dhuhr becomes Jumu'ah + Al-Kahf line. Ramadan: Iftar countdown in arch, Suhoor line.
- Screensaver after idle: black, dim arch outline + clock + next prayer, drifts every 60 s.
- Prayer times computed on-device (e.g. Adhan library): location, method (default Egyptian General
  Authority of Survey), Asr school, Hijri adjustment, digits (Western/Eastern) independent of language, 12/24 h.
- Example data: Fajr 04:38 · Sunrise 06:03 · Dhuhr 11:38 · Asr 14:48 · Maghrib 17:13 · Isha 18:31 · Cairo · Qibla 136°.

## Apps
Favourites in order: YouTube, Shahid, Claude Code TV, BrowseHere. Others: YouTube Music, Beach Buggy
Racing, Google Play Games, Google Play Store, File Manager +, MagiConnect, Media Player, Salaty, Settings.
Tiles show the app's own banner/icon at runtime; the mock uses neutral glyphs.
