#!/usr/bin/env python3
# AwanTV-build-cities.py - Version 1.0.0
# Builds app/src/main/assets/cities/cities.tsv, the world city list behind Settings > Location > search, from
# GeoNames' cities15000 (every city of 15,000 people or more; GeoNames.org, CC BY 4.0) as packaged in the
# geonamescache Python package (MIT): pip download geonamescache --no-deps, then pass the .whl or its
# geonamescache/data/cities15000.json.
#
# Columns (tab-separated, one city per line, largest first):
#   name, Arabic name (the hand-written one of Prefs.CITIES where the city is there, else a best guess from
#   GeoNames' alternate names; may be empty), latitude, longitude,
#   country code, population, search key (normalised names joined by "|", see key() and Places.java)
#
# Usage: python3 tools/AwanTV-build-cities.py <geonamescache .whl or cities15000.json>
import collections, json, os, re, sys, unicodedata, zipfile

VERSION = "1.0.0"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "cities", "cities.tsv")

HARAKAT = re.compile("[ً-ْٰـ]")
ARABIC_LETTERS = set("ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي -'")
NOISE = ("مدينة ", "ولاية ", "إمارة ", "امارة ", "جزيرة ", "محافظة ", "منطقة ", "بلدية ", "قضاء ", "مقاطعة ", "مركز ")
ARAB = {"SA", "AE", "KW", "QA", "BH", "OM", "YE", "IQ", "SY", "LB", "JO", "PS", "EG", "LY", "TN", "DZ", "MA", "MR",
        "SD", "SO", "DJ", "KM"}


def key(s):
    """The search key: what Places.normalise() does at run time, kept identical."""
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(ch for ch in s if unicodedata.category(ch) != "Mn")
    s = HARAKAT.sub("", s)
    for a, b in (("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ٱ", "ا"), ("ة", "ه"), ("ى", "ي"), ("ؤ", "و"), ("ئ", "ي"),
                 ("ی", "ي"), ("ک", "ك"), ("-", " "), ("'", " "), ("’", " ")):
        s = s.replace(a, b)
    s = " ".join(w[2:] if w.startswith("ال") and len(w) > 3 else w for w in s.split())
    return s.strip()


def arabic_name(alts, cc):
    """The likeliest Arabic name: the most attested spelling family, written the Arabic way (ة, ي, أ, ال)."""
    cands = []
    for a in alts:
        n = HARAKAT.sub("", a).strip()
        if not n or not all(ch in ARABIC_LETTERS for ch in n) or len(n.split()) > 3:
            continue
        if any(n.startswith(w) for w in NOISE) or n.endswith(" القديمة"):
            continue
        words = n.split()
        # A ة inside a word, or أ after a word's first letter (not after ال), is not Arabic spelling.
        if any("ة" in w[:-1] for w in words):
            continue
        if any("أ" in (w[3:] if w.startswith("الأ") else w[1:]) for w in words):
            continue
        cands.append(n)
    if not cands:
        return ""
    groups = collections.defaultdict(list)
    for n in cands:
        groups[key(n).replace(" ", "")].append(n)

    def gscore(k):
        g = groups[k]
        emphatic = sum(ch in "طصضظقع" for ch in g[0])
        return (len(g), any(("ة" in n or "أ" in n or "إ" in n) for n in g), emphatic, -len(k))

    g = groups[max(groups, key=gscore)]

    def vscore(n):
        v = g.count(n)
        if n.startswith("ال"):
            v += 3 if cc in ARAB else -1
        if "ة" in n:
            v += 2
        if n.endswith("ي"):
            v += 1
        if "أ" in n or "إ" in n:
            v += 1
        return (v, -len(n))

    return max(set(g), key=vscore)


def curated():
    """Prefs.CITIES: (Arabic name, latitude, longitude) of the cities whose names were written by hand."""
    src = open(os.path.join(ROOT, "app", "src", "main", "java", "app", "awanpro", "tv", "Prefs.java"), encoding="utf-8").read()
    return [(ar, float(lat), float(lng)) for ar, lat, lng in
            re.findall(r'new City\("\w+", "([^"]+)", "[^"]+", "[^"]+", "[^"]+", (-?[\d.]+), (-?[\d.]+)', src)]


def load(path):
    if path.endswith(".whl"):
        with zipfile.ZipFile(path) as z:
            return json.loads(z.read("geonamescache/data/cities15000.json"))
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def main():
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 tools/AwanTV-build-cities.py <geonamescache .whl or cities15000.json>")
    print("AwanTV-build-cities v%s" % VERSION)
    cities = sorted(load(sys.argv[1]).values(), key=lambda c: -c["population"])
    # The hand-written Arabic names win: each goes to the largest city within about 15 km of its coordinates.
    named = {}
    for ar, lat, lng in curated():
        near = [c for c in cities if (c["latitude"] - lat) ** 2 + (c["longitude"] - lng) ** 2 < 0.02]
        if near:
            named.setdefault(near[0]["geonameid"], ar)
    rows = []
    for c in cities:
        alts = c.get("alternatenames", [])
        # Every Latin alternate name (up to 40), so the English names a viewer might type are found too (Mecca,
        # Jidda, Bombay, Kiev); and up to 12 Arabic-script ones.
        latin = sorted({a for a in alts if a.isascii() and 2 < len(a) < 40 and not a.isupper()}, key=len)[:40]
        arab = sorted({HARAKAT.sub("", a) for a in alts if any("ء" <= ch <= "ي" for ch in a)}, key=len)[:12]
        keys = []
        for n in [c["name"], c.get("asciiname", "")] + latin + arab:
            k = key(n)
            if k and k not in keys:
                keys.append(k)
        name = c["name"].replace("\t", " ")
        ar = named.get(c["geonameid"]) or arabic_name(alts, c["countrycode"])
        rows.append("\t".join([name, ar, "%.4f" % c["latitude"],
                               "%.4f" % c["longitude"], c["countrycode"], str(c["population"]), "|".join(keys)]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(rows) + "\n")
    print("Wrote %d cities to %s (%d KB)" % (len(rows), OUT, os.path.getsize(OUT) // 1024))


if __name__ == "__main__":
    main()
