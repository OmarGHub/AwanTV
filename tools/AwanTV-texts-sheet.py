#!/usr/bin/env python3
# AwanTV-texts-sheet.py - Version 1.1.1
# The side-by-side sheet given with every release that adds a language (languages plan, phase 4): one row per
# text, numbered, with its key, where it shows, the Arabic, the English, the new language and a notes column, read
# from the very files the release carries: app/src/main/res/values*/texts.xml (strings, then each array item as
# key[i]), then the counts and references around the adhkar and hadiths (assets/adhkar/adhkar.json,
# assets/hadith/hadith.json: countAr/countEn/countXx, refAr/refEn/refXx), each different English text once.
# "Where it shows": the screens whose code calls the Text method that reads the text (SCREENS below).
#
# Usage: python3 tools/AwanTV-texts-sheet.py <language(s), e.g. ur or ur,tr> <version, e.g. 1.18.0b>
#   writes state/texts/AwanTV-texts-<version>-<languages joined by ->.xlsx (needs openpyxl: pip install openpyxl)
# 1.1.0: several languages, a column each after English (for a review of all of them at once).
# 1.1.1: Malay's column named.
import json, os, re, sys
import xml.etree.ElementTree as ET

VERSION = "1.1.1"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
TEXT = os.path.join(ROOT, "app", "src", "main", "java", "app", "awanpro", "tv", "Text.java")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
print("AwanTV-texts-sheet " + VERSION)
if len(sys.argv) != 3:
    sys.exit("usage: AwanTV-texts-sheet.py <language(s), e.g. ur or ur,tr> <version, e.g. 1.18.0b>")
langs, version = [l.strip() for l in sys.argv[1].split(",") if l.strip()], sys.argv[2]
folder_of = lambda lang: {"id": "values-in", "he": "values-iw"}.get(lang, "values-" + lang)
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill


def aapt(raw):
    """A texts.xml value as Android reads it: the quotes taken off, \\n \\t \\uXXXX and \\x read."""
    s = raw or ""
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "u":
                out.append(chr(int(s[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append({"n": "\n", "t": "\t"}.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def read(name):
    path = os.path.join(RES, name, "texts.xml")
    strings, arrays = {}, {}
    if not os.path.exists(path):
        return strings, arrays, False
    for e in ET.parse(path).getroot():
        if e.tag == "string":
            strings[e.get("name")] = aapt(e.text)
        elif e.tag == "string-array":
            arrays[e.get("name")] = [aapt(i.text) for i in e]
    return strings, arrays, True


en_s, en_a, _ = read("values")
ar_s, ar_a, _ = read("values-ar")
XX = []
for lang in langs:
    xs, xa, has = read(folder_of(lang))
    if not has:
        print("No %s/texts.xml yet: its column is empty." % folder_of(lang))
    XX.append((lang, xs, xa, has))

# Where each key shows: the screens whose code calls the Text method that reads it (MainActivity: Home, ...);
# a method called only from other Text methods takes their screens; else Text.java's section comment above it.
SCREENS = [("MainActivity", "Home / الرئيسية"), ("SettingsActivity", "Settings / الإعدادات"),
           ("SaverScreen", "Screensaver / شاشة الاستراحة"), ("NowActivity", "Adhan / الأذان"),
           ("PrayerDayActivity", "Today & Qibla / مواقيت اليوم والقبلة"), ("AllAppsActivity", "All apps / كل التطبيقات"),
           ("LibraryActivity", "Saved recitations / التلاوات المحفوظة"), ("PlaceActivity", "Location / الموقع"),
           ("NewsActivity", "What's new / ما الجديد"), ("UpdateNotice", "New version / إصدار جديد"),
           ("PrayerWatch", "Over other apps / فوق التطبيقات"), ("ScreenOffActivity", "Screen off / إطفاء الشاشة"),
           ("CrashActivity", "Safety screen / شاشة الأمان"), ("SettingsFile", "Settings file, editor / ملف الإعدادات والمحرر"),
           ("ArchView", "Home / الرئيسية"), ("WeatherBadge", "Home, screensaver / الرئيسية والاستراحة"),
           ("Screen", "Every screen / كل الشاشات"), ("ListLayer", "Home / الرئيسية"), ("Buttons", "Settings / الإعدادات"),
           ("Radio", "Radio / الإذاعة"), ("Radios", "Radio / الإذاعة"), ("RemoteKeys", "Remote / الريموت"),
           ("Weather", "Home, screensaver / الرئيسية والاستراحة"), ("Hijri", "Home, screensaver / الرئيسية والاستراحة"),
           ("Library", "Saved recitations / التلاوات المحفوظة"), ("Adhans", "Settings / الإعدادات"),
           ("Places", "Location / الموقع"), ("Apps", "Home / الرئيسية"), ("Iqama", "Settings / الإعدادات"),
           ("Jumuah", "Settings, screensaver / الإعدادات والاستراحة"), ("Photos", "Settings / الإعدادات"),
           ("Reminders", "Reminders / التذكيرات"), ("Announce", "Reminders / التذكيرات")]
SRC = os.path.dirname(TEXT)
calls = {}  # method -> screens
for cls, label in SCREENS:
    path = os.path.join(SRC, cls + ".java")
    if os.path.exists(path):
        for m in set(re.findall(r"\bt\.(\w+)\(", open(path, encoding="utf-8").read())):
            calls.setdefault(m, [])
            if label not in calls[m]:
                calls[m].append(label)
method_keys, inner, section_of, method, section = {}, {}, {}, None, "Home"
for line in open(TEXT, encoding="utf-8"):
    m = re.match(r"    // (?!-)(.+)", line)
    if m:
        section = re.sub(r"\s*\(design/[^)]*\)", "", m.group(1)).strip().rstrip(".")
    m = re.match(r"    (?:static |private |final )*[\w<>\[\], .]+? (\w+)\([^;]*\)\s*\{", line)
    if m:
        method = m.group(1)
    if method:
        for kind, key in re.findall(r"R\.(string|array)\.(\w+)", line):
            method_keys.setdefault(key, [])
            if method not in method_keys[key]:
                method_keys[key].append(method)
            section_of.setdefault(key, section)
        for callee in re.findall(r"(?<![\w.])(\w+)\(", line):
            if callee != method:
                inner.setdefault(callee, set()).add(method)


def screens(m, depth=0):
    out = list(calls.get(m, []))
    if not out and depth < 3:
        for caller in sorted(inner.get(m, ())):
            for s in screens(caller, depth + 1):
                if s not in out:
                    out.append(s)
    return out


where = {}
for key, methods in method_keys.items():
    found = []
    for m in methods:
        for s in screens(m):
            if s not in found:
                found.append(s)
    where[key] = " · ".join(found) if found else section_of.get(key, "")

rows = []
for key in sorted(en_s, key=str.lower):
    rows.append((key, where.get(key, ""), ar_s.get(key, ""), en_s[key], [xs.get(key, "") for _, xs, _, _ in XX]))
for key in sorted(en_a, key=str.lower):
    for i, v in enumerate(en_a[key]):
        a = ar_a.get(key, [])
        xs = [xa.get(key, []) for _, _, xa, _ in XX]
        rows.append(("%s[%d]" % (key, i), where.get(key, ""), a[i] if i < len(a) else "", v, [x[i] if i < len(x) else "" for x in xs]))

# Counts and references around the religious texts (their words stay Arabic): each English text once.
caps = [l[:1].upper() + l[1:] for l in langs]
def items(path):
    d = json.load(open(path, encoding="utf-8"))
    for k, v in d.items():
        if isinstance(v, list):
            for o in v:
                if isinstance(o, dict):
                    yield k, o
seen = set()
for name, path in (("adhkar.json", os.path.join(ASSETS, "adhkar", "adhkar.json")),
                   ("hadith.json", os.path.join(ASSETS, "hadith", "hadith.json"))):
    for part, o in items(path):
        for field in ("count", "ref"):
            en = o.get(field + "En", "")
            if not en or (name, field, en) in seen:
                continue
            seen.add((name, field, en))
            rows.append(("%s %s: %s" % (name, field, en), {"adhkar": "Screensaver, adhkar / الاستراحة: الأذكار", "afterPrayer": "Screensaver, after the prayer / الاستراحة: أذكار بعد الصلاة",
                          "afterAdhan": "Adhan, after it / بعد الأذان"}.get(part if name == "hadith.json" else "adhkar", part),
                         o.get(field + "Ar", ""), en, [o.get(field + c, "") for c in caps]))

wb = Workbook()
ws = wb.active
ws.title = "Texts"
ws.sheet_view.rightToLeft = True
NAMES = {"ur": "اردو / Urdu", "tr": "Türkçe / Turkish", "id": "Bahasa Indonesia / Indonesian", "fr": "Français / French", "ms": "Bahasa Melayu / Malay"}
head = ["#", "المفتاح / Key", "أين يظهر / Where", "العربية / Arabic", "English"] + [NAMES.get(l, l) for l in langs] + ["ملاحظات / Notes"]
ws.append(head)
for c in ws[1]:
    c.font = Font(bold=True)
    c.fill = PatternFill("solid", fgColor="DDE4F0")
missing = {l: 0 for l in langs}
for n, (key, w, ar, en, xxs) in enumerate(rows, 1):
    note = []
    parts = sorted(set(re.findall(r"%\d+\$s", en)))
    if parts:
        note.append("parts: " + " ".join(parts))
    for (lang, _, _, has), xx in zip(XX, xxs):
        if has and not xx and re.search(r"\w", en):  # "%%" alone may be left empty (Turkish "%45")
            note.append(lang + ": missing")
            missing[lang] += 1
        elif has and xx == en and re.search(r"[A-Za-z]{3}", en):
            if w.startswith("Settings file, editor"):
                note.append(lang + ": English, the settings file is Arabic or English only")
            elif key.startswith("news_"):
                note.append(lang + ": English, What's new of an earlier version")
            else:
                note.append(lang + ": same as English")
    ws.append([n, key, w, ar, en] + xxs + ["; ".join(note)])
from openpyxl.utils import get_column_letter
widths = [7, 30, 26, 50, 50] + [50] * len(langs) + [26]
for i, wdt in enumerate(widths, 1):
    ws.column_dimensions[get_column_letter(i)].width = wdt
for row in ws.iter_rows(min_row=2):
    for c in row:
        c.alignment = Alignment(wrap_text=True, vertical="top")
    row[3].alignment = Alignment(wrap_text=True, vertical="top", horizontal="right")
    row[4].alignment = Alignment(wrap_text=True, vertical="top", horizontal="left")
    for j, lang in enumerate(langs):
        rtl = lang in ("ar", "ur", "fa", "ug")
        row[5 + j].alignment = Alignment(wrap_text=True, vertical="top", horizontal="right" if rtl else "left")
ws.freeze_panes = "C2"
ws.auto_filter.ref = ws.dimensions

out_dir = os.path.join(ROOT, "state", "texts")
os.makedirs(out_dir, exist_ok=True)
out = os.path.join(out_dir, "AwanTV-texts-%s-%s.xlsx" % (version, "-".join(langs)))
wb.save(out)
print("Rows: %d   missing: %s" % (len(rows), ", ".join("%s %d" % (l, missing[l] if h else len(rows)) for l, _, _, h in XX)))
print("Saved: " + out)
