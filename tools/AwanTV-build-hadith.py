#!/usr/bin/env python3
# AwanTV-build-hadith.py - Version 1.0.0
# Builds app/src/main/assets/hadith/hadith.json (Hadith.java) from tools/hadith-list.txt and the Arabic Sahih
# al-Bukhari and Sahih Muslim of github.com/fawazahmed0/hadith-api (The Unlicense, public domain):
# editions/ara-bukhari.json and editions/ara-muslim.json from its branch "1". Three lists: the screensaver's
# hadiths, the supplication after the adhan (the call screen) and the adhkar after the prayer (the screensaver).
#
# hadith-list.txt: sections [hadiths], [after_adhan], [after_prayer]; one text a line,
#   "words|al-Bukhari number|Muslim number|count in Arabic|count in English" (numbers and counts may be empty).
# The words are typed without vowel marks; the tool finds them in that very hadith of each book and takes the
# book's own vocalised text for them (al-Bukhari's when both are given), so nothing typed by hand reaches the
# screen. A line not found in a book it names stops the build. Only spelling is touched: stray full stops and
# direction marks go, and a final alif maqsura carrying a vowel is written as ya (عَلَىَّ -> عَلَيَّ).
#   "@adhkar <order>|source in Arabic|source in English|count in Arabic|count in English" takes the text of that
# entry of assets/adhkar/adhkar.json unchanged (Hisn al-Muslim: the Quranic ones, Ayat al-Kursi and the last three
# surahs, read after the prayer too).
#
# Usage: python3 tools/AwanTV-build-hadith.py <folder with ara-bukhari.json and ara-muslim.json>
import json, os, re, sys

VERSION = "1.0.0"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIST = os.path.join(ROOT, "tools", "hadith-list.txt")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "hadith", "hadith.json")
ADHKAR = os.path.join(ROOT, "app", "src", "main", "assets", "adhkar", "adhkar.json")
print("AwanTV-build-hadith " + VERSION)
if len(sys.argv) != 2:
    sys.exit("usage: AwanTV-build-hadith.py <folder with ara-bukhari.json and ara-muslim.json>")


SRC = sys.argv[1]
MARKS = re.compile('[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED\u0640\u200f\u200e]')

def norm_char(ch):
    return {'أ': 'ا', 'إ': 'ا', 'آ': 'ا', 'ٱ': 'ا', 'ى': 'ي', 'ة': 'ه', 'ؤ': 'و', 'ئ': 'ي'}.get(ch, ch)

def index(text):
    """Normalised text plus, for each of its characters, the index in the original."""
    out, pos = [], []
    for i, ch in enumerate(text):
        if MARKS.match(ch):
            continue
        if ch in '،,.:;"«»\'!؟?؛()-ـ':
            ch = ' '
        if ch in ' \u00a0':
            if out and out[-1] == ' ':
                continue
            ch = ' '
        out.append(norm_char(ch)); pos.append(i)
    return ''.join(out), pos

def nkey(k):
    return index(k)[0].strip()

books = {}
for name, file in (('b', 'ara-bukhari.json'), ('m', 'ara-muslim.json')):
    rows = []
    for h in json.load(open(f'{SRC}/{file}'))['hadiths']:
        if not h['text']:
            continue
        n, p = index(h['text'])
        # al-Bukhari as in Fath al-Bari; Muslim by Abd al-Baqi's number (arabicnumber, "52.10" = 52), and the few
        # Muslim entries without one are left out rather than guessed.
        num = h.get('arabicnumber') if name == 'm' else h['hadithnumber']
        if not num:
            continue
        rows.append((int(float(num)), h['text'], n, p))
    books[name] = rows

def exact(key, book, num):
    """The book's own text for the words of key in hadith num (Muslim: the whole number of Abd al-Baqi's), or None."""
    k = nkey(key)
    for n_, text, n, p in books[book]:
        if n_ != num:
            continue
        i = n.find(k)
        if i >= 0:
            end = p[i + len(k) - 1] + 1
            while end < len(text) and MARKS.match(text[end]) and text[end] not in '\u200f\u200e':
                end += 1
            return text[p[i]:end]
    return None

def clean(text):
    text = text.replace('\u200f', '').replace('\u200e', '')
    text = re.sub(r'\s*\.\s*', ' ', text)           # a stray full stop inside a saying
    text = re.sub('\u0649(?=[\u064B-\u0652])', '\u064a', text)  # عَلَىَّ -> عَلَيَّ (spelling only)
    return re.sub(r'\s+', ' ', text).strip()

adhkar = {d['order']: d for d in json.load(open(ADHKAR, encoding='utf-8'))['adhkar']}
lists = {'hadiths': [], 'after_adhan': [], 'after_prayer': []}
section = 'hadiths'
for line in open(LIST, encoding='utf-8'):
    line = line.strip()
    if not line or line.startswith('#'):
        continue
    if line.startswith('[') and line.endswith(']'):
        section = line[1:-1]
        if section not in lists:
            sys.exit('unknown section: ' + section)
        continue
    parts = line.split('|') + [''] * 5
    key, b, m, count_ar, count_en = parts[:5]
    if key.startswith('@adhkar '):
        d = adhkar.get(int(key.split()[1]))
        if d is None:
            sys.exit('no such adhkar entry: ' + key)
        lists[section].append({'text': d['text'], 'bukhari': 0, 'muslim': 0, 'refAr': b, 'refEn': m,
                               'countAr': count_ar, 'countEn': count_en})
        continue
    b = int(b) if b else None; m = int(m) if m else None
    tb = exact(key, 'b', b) if b else None
    tm = exact(key, 'm', m) if m else None
    if (b and not tb) or (m and not tm):
        sys.exit('not found: %s (al-Bukhari %s: %s, Muslim %s: %s)' % (key.encode('unicode_escape').decode(), b, tb is not None, m, tm is not None))
    item = {'text': clean(tb or tm), 'bukhari': b or 0, 'muslim': m or 0}
    if count_ar or count_en:
        item['countAr'] = count_ar
        item['countEn'] = count_en
    lists[section].append(item)
json.dump({'source': 'fawazahmed0/hadith-api (The Unlicense, public domain): ara-bukhari, ara-muslim; '
                     'Quranic entries from assets/adhkar/adhkar.json',
           'hadiths': lists['hadiths'], 'afterAdhan': lists['after_adhan'], 'afterPrayer': lists['after_prayer']},
          open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('%d hadiths, %d after the adhan, %d after the prayer written to %s'
      % (len(lists['hadiths']), len(lists['after_adhan']), len(lists['after_prayer']), os.path.relpath(OUT, ROOT)))
