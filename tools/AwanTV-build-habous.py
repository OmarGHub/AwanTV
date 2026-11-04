#!/usr/bin/env python3
"""AwanTV-build-habous.py - Version 1.0.0

Builds app/src/main/assets/habous/localities.tsv: Morocco's Ministry of Habous and Islamic Affairs publishes a
timetable for each of its localities (191 in 2026), not how it computes them. This fits, for each locality, the point
and the two horizons that give its published times under the ministry's rules (Habous.java):

  Fajr 19 deg (rounded down to the minute), Isha 17 deg, Asr one shadow length, Dhuhr five minutes after the transit,
  sunrise and Maghrib at the locality's own horizon (arc minutes below the horizon, east and west), the rest rounded.

The times are read from schawki/habous-prayer-times-data (MIT; the times themselves are the ministry's), a copy of the
ministry's tables kept up to date on GitHub. Clone it with its history for more days:

  git clone https://github.com/schawki/habous-prayer-times-data
  python3 tools/AwanTV-build-habous.py <path to habous-prayer-times-data>

Only the fitted numbers go into the app, never the ministry's tables. The solar positions are adhan-java's (Meeus,
Astronomical Algorithms), the same formulas Habous.java runs on, so the fit holds on the TV. Nothing is written when
the fit falls short (see CHECK below). Output in English only.
"""

import json
import math
import os
import subprocess
import sys

VERSION = "1.0.0"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "habous", "localities.tsv")
FROM = "2026-09-21"  # Morocco's legal time is GMT from 2026-09-20 (decree 2.26.530): days before are another offset.
ASR_LATER, ISHA_LATER = 12, 3  # seconds, as Habous.java
# The fit must give the published minute for at least this share of all times, and never be two minutes off.
CHECK_EXACT = 0.97

# Latin names where the data gives the locality only in Arabic, or a namesake's (transliterated; the Arabic is the
# ministry's).
LATIN = {
    309: "Agoudal Imilchil",
    30: "Bouskour", 115: "Tameslouht", 116: "Kettara", 125: "Foum Lahsen", 131: "Tendit", 140: "Haskoura",
    155: "Lemsid", 169: "Bir Anzarane", 301: "Khemis Sidi Abdeljalil", 307: "Tafnatane", 308: "Ait Lqaq",
    310: "Agoudal Imilchil Midelt", 311: "Agaiouar", 313: "Askine", 315: "Zaouiat Moulay Brahim", 316: "Toulkoult",
    317: "Iks", 318: "Kerss", 319: "Tissint", 322: "Imin Tlat",
}

# ---------------------------------------------------------------- adhan-java's solar positions (internal package)


def unwind(v):
    return v - 360 * math.floor(v / 360)


def closest_angle(a):
    if -180 <= a <= 180:
        return a
    return a - 360 * java_round(a / 360)


def java_round(x):
    return math.floor(x + 0.5)


def julian_day(y, m, d):
    Y = y if m > 2 else y - 1
    M = m if m > 2 else m + 12
    A = int(Y / 100)
    B = 2 - A + int(A / 4)
    return int(365.25 * (Y + 4716)) + int(30.6001 * (M + 1)) + d + B - 1524.5


class Solar:
    """SolarCoordinates for a Julian day."""

    def __init__(self, jd):
        T = (jd - 2451545.0) / 36525
        L0 = unwind(280.4664567 + 36000.76983 * T + 0.0003032 * T ** 2)
        Lp = unwind(218.3165 + 481267.8813 * T)
        Om = unwind(125.04452 - 1934.136261 * T + 0.0020708 * T ** 2 + T ** 3 / 450000)
        M = unwind(357.52911 + 35999.05029 * T - 0.0001537 * T ** 2)
        Mr = math.radians(M)
        C = ((1.914602 - 0.004817 * T - 0.000014 * T ** 2) * math.sin(Mr) + (0.019993 - 0.000101 * T) * math.sin(2 * Mr)
             + 0.000289 * math.sin(3 * Mr))
        O = 125.04 - 1934.136 * T
        lam = math.radians(unwind(L0 + C - 0.00569 - 0.00478 * math.sin(math.radians(O))))
        JD = T * 36525 + 2451545.0
        theta0 = unwind(280.46061837 + 360.98564736629 * (JD - 2451545) + 0.000387933 * T ** 2 - T ** 3 / 38710000)
        dpsi = ((-17.2 / 3600) * math.sin(math.radians(Om)) - (1.32 / 3600) * math.sin(2 * math.radians(L0))
                - (0.23 / 3600) * math.sin(2 * math.radians(Lp)) + (0.21 / 3600) * math.sin(2 * math.radians(Om)))
        deps = ((9.2 / 3600) * math.cos(math.radians(Om)) + (0.57 / 3600) * math.cos(2 * math.radians(L0))
                + (0.10 / 3600) * math.cos(2 * math.radians(Lp)) - (0.09 / 3600) * math.cos(2 * math.radians(Om)))
        e0 = 23.439291 - 0.013004167 * T - 0.0000001639 * T ** 2 + 0.0000005036 * T ** 3
        eapp = math.radians(e0 + 0.00256 * math.cos(math.radians(O)))
        self.dec = math.degrees(math.asin(math.sin(eapp) * math.sin(lam)))
        self.ra = unwind(math.degrees(math.atan2(math.cos(eapp) * math.sin(lam), math.cos(lam))))
        self.ast = theta0 + ((dpsi * 3600) * math.cos(math.radians(e0 + deps))) / 3600


def interp(y2, y1, y3, n):
    a, b = y2 - y1, y3 - y2
    return y2 + (n / 2) * (a + b + n * (b - a))


def interp_angles(y2, y1, y3, n):
    a, b = unwind(y2 - y1), unwind(y3 - y2)
    return y2 + (n / 2) * (a + b + n * (b - a))


class Day:
    """A date's three SolarCoordinates (the day before, the day, the day after): they don't depend on the place."""

    def __init__(self, y, m, d):
        self.y, self.m, self.d = y, m, d
        self.jd = julian_day(y, m, d)
        self.prev, self.sol, self.next = Solar(self.jd - 1), Solar(self.jd), Solar(self.jd + 1)


class Place:
    """SolarTime for a date and a point: the transit and hourAngle()."""

    def __init__(self, day, lat, lon):
        self.day, self.lat, self.lon = day, lat, lon
        s = day.sol
        self.m0 = (s.ra - lon - s.ast) / 360
        self.m0 -= math.floor(self.m0)
        th = unwind(s.ast + 360.985647 * self.m0)
        a = unwind(interp_angles(s.ra, day.prev.ra, day.next.ra, self.m0))
        H = closest_angle(th + lon - a)
        self.transit = (self.m0 - H / 360) * 24

    def hour_angle(self, h0, after):
        s, dy, lat = self.day.sol, self.day, self.lat
        t1 = math.sin(math.radians(h0)) - math.sin(math.radians(lat)) * math.sin(math.radians(s.dec))
        t2 = math.cos(math.radians(lat)) * math.cos(math.radians(s.dec))
        x = t1 / t2
        if x < -1 or x > 1:
            return float("nan")
        H0 = math.degrees(math.acos(x))
        m = self.m0 + H0 / 360 if after else self.m0 - H0 / 360
        th = unwind(s.ast + 360.985647 * m)
        a = unwind(interp_angles(s.ra, dy.prev.ra, dy.next.ra, m))
        d = interp(s.dec, dy.prev.dec, dy.next.dec, m)
        H = th + self.lon - a
        h = math.degrees(math.asin(math.sin(math.radians(lat)) * math.sin(math.radians(d))
                                   + math.cos(math.radians(lat)) * math.cos(math.radians(d)) * math.cos(math.radians(H))))
        dm = (h - h0) / (360 * math.cos(math.radians(d)) * math.cos(math.radians(lat)) * math.sin(math.radians(H)))
        return (m + dm) * 24


def declination(jd):
    """Habous.declination: the sun's apparent declination at a Julian date (for Asr at Asr itself)."""
    t = (jd - 2451545.0) / 36525
    l0 = unwind(280.4664567 + 36000.76983 * t + 0.0003032 * t * t)
    mr = math.radians(unwind(357.52911 + 35999.05029 * t - 0.0001537 * t * t))
    center = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * math.sin(mr) + (0.019993 - 0.000101 * t) * math.sin(2 * mr)
              + 0.000289 * math.sin(3 * mr))
    omega = 125.04 - 1934.136 * t
    lam = math.radians(unwind(l0 + center - 0.00569 - 0.00478 * math.sin(math.radians(omega))))
    e0 = 23.439291 - 0.013004167 * t - 0.0000001639 * t * t + 0.0000005036 * t * t * t
    e = math.radians(e0 + 0.00256 * math.cos(math.radians(omega)))
    return math.degrees(math.asin(math.sin(e) * math.sin(lam)))


def minute(hours, fajr=False):
    """Habous.times' rounding: milliseconds rounded, then Fajr down to its minute and the rest to the nearest."""
    if hours != hours:
        return None
    ms = java_round(hours * 3_600_000)
    return (ms if fajr else ms + 30_000) // 60_000


def fajr(p):
    return minute(p.hour_angle(-19.0, False), True)


def isha(p):
    return minute(p.hour_angle(-17.0, True) + ISHA_LATER / 3600.0)


def dhuhr(p):
    return minute(p.transit + 5 / 60.0)


def asr(p):
    h = p.transit + 3
    for _ in range(3):
        tangent = abs(p.lat - declination(p.day.jd + h / 24))
        h = p.hour_angle(math.degrees(math.atan(1.0 / (1 + math.tan(math.radians(tangent))))), True)
        if h != h:
            return None
    return minute(h + ASR_LATER / 3600.0)


def sunrise(p, arcmin):
    return minute(p.hour_angle(-arcmin / 60.0, False))


def maghrib(p, arcmin):
    return minute(p.hour_angle(-arcmin / 60.0, True))


# ---------------------------------------------------------------- the fit


def best(lo, hi, step, score):
    """The middle of the longest run of steps with the best score (not the middle of all of them: two runs apart)."""
    xs, ss = [], []
    x = lo
    while x <= hi + 1e-9:
        xs.append(x)
        ss.append(score(x))
        x += step
    top = max(ss)
    run, longest = [], []
    for x, s in zip(xs, ss):
        if s == top:
            run.append(x)
            if len(run) > len(longest):
                longest = list(run)
        else:
            run = []
    return longest[len(longest) // 2], top


def fit(days, table, lat0, lon0):
    """{lat, lon, sunrise, maghrib} for one locality: longitude from Dhuhr, latitude from Fajr, Asr and Isha, then each
    horizon from its own time."""
    n = len(days)

    def count(fn, k, lat, lon, *extra):
        return sum(1 for i in range(n) if fn(Place(days[i], lat, lon), *extra) == table[i][k])

    seeded = lat0 is not None
    lat_s = lat0 if seeded else 31.5
    lo, hi = (lon0 - 1, lon0 + 1) if seeded else (-17.5, -1.0)
    lon, _ = best(lo, hi, 0.02, lambda x: count(dhuhr, 2, lat_s, x))
    lon, _ = best(lon - 0.03, lon + 0.03, 0.001, lambda x: count(dhuhr, 2, lat_s, x))
    lo, hi = (lat0 - 1, lat0 + 1) if seeded else (20.5, 36.0)
    both = lambda x: count(fajr, 0, x, lon) + count(isha, 5, x, lon)
    lat, top = best(lo, hi, 0.02, both)
    if top < 1.8 * n:
        # The data's point is elsewhere (a namesake found by its geocoding): the whole country.
        lat, top = best(20.5, 36.0, 0.02, both)
    lat, _ = best(lat - 0.03, lat + 0.03, 0.001, lambda x: both(x) + count(asr, 3, x, lon))
    places = [Place(d, lat, lon) for d in days]
    hs, _ = best(0, 220, 1, lambda a: sum(1 for i in range(n) if sunrise(places[i], a) == table[i][1]))
    hm, _ = best(0, 220, 1, lambda a: sum(1 for i in range(n) if maghrib(places[i], a) == table[i][4]))
    return round(lat, 4), round(lon, 4), int(hs), int(hm)


def times(day, lat, lon, hs, hm):
    p = Place(day, lat, lon)
    return [fajr(p), sunrise(p, hs), dhuhr(p), asr(p), maghrib(p, hm), isha(p)]


# ---------------------------------------------------------------- the data


def read_days(data_dir):
    """{city id: {date: [six minutes after 00:00 GMT]}} from data/times, and from every earlier commit when the copy
    has its git history (more days; later commits win)."""
    out = {}

    def add(doc):
        offsets = doc.get("offsets", {})
        for date, t in doc["days"].items():
            if date < FROM:
                continue
            off = offsets.get(date, doc.get("utc_offset", "+00:00"))
            if off != "+00:00":
                raise SystemExit(f"City {doc['city_id']}, {date}: legal time {off}, not GMT. Update FROM and the offsets.")
            row = []
            for k in ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"):
                h, m = t[k].split(":")
                row.append(int(h) * 60 + int(m))
            out.setdefault(int(doc["city_id"]), {})[date] = row

    git = os.path.isdir(os.path.join(data_dir, ".git"))
    if git:
        log = subprocess.run(["git", "-C", data_dir, "log", "--format=%H", "--reverse", "--", "data/times"],
                             capture_output=True, text=True).stdout.split()
        for c in log:
            names = subprocess.run(["git", "-C", data_dir, "ls-tree", "--name-only", c, "data/times/"],
                                   capture_output=True, text=True).stdout.split()
            for f in names:
                blob = subprocess.run(["git", "-C", data_dir, "show", f"{c}:{f}"], capture_output=True, text=True).stdout
                try:
                    add(json.loads(blob))
                except ValueError:
                    pass
    folder = os.path.join(data_dir, "data", "times")
    for f in sorted(os.listdir(folder)):
        if f.endswith(".json"):
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                add(json.load(fh))
    return out, git


def main():
    print(f"AwanTV-build-habous v{VERSION}")
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 tools/AwanTV-build-habous.py <path to habous-prayer-times-data>")
    data_dir = sys.argv[1]
    with open(os.path.join(data_dir, "data", "cities.json"), encoding="utf-8") as fh:
        cities = {int(c["id"]): c for c in json.load(fh)["cities"]}
    days_by_city, git = read_days(data_dir)
    if not git:
        print("No git history in the data folder: today's files only (clone it with its history for more days).")
    missing = sorted(set(cities) - set(days_by_city))
    if missing:
        raise SystemExit(f"No times for localities {missing}.")
    dates = sorted({d for v in days_by_city.values() for d in v})
    cache = {}
    exact = [0] * 6
    total, worst, rows = 0, 0, []
    for cid in sorted(cities):
        c = cities[cid]
        table_d = days_by_city[cid]
        ds = sorted(table_d)
        days = []
        for d in ds:
            if d not in cache:
                y, m, dd = map(int, d.split("-"))
                cache[d] = Day(y, m, dd)
            days.append(cache[d])
        table = [table_d[d] for d in ds]
        lat, lon, hs, hm = fit(days, table, c.get("lat"), c.get("lon"))
        miss = []
        for day, want in zip(days, table):
            got = times(day, lat, lon, hs, hm)
            total += 1
            for k in range(6):
                diff = abs(want[k] - got[k]) if got[k] is not None else 99
                if diff == 0:
                    exact[k] += 1
                worst = max(worst, diff)
                if diff > 0:
                    miss.append(diff)
        name_fr = LATIN.get(cid) or c.get("name_fr")
        if not name_fr:
            raise SystemExit(f"Locality {cid} has no Latin name: add it to LATIN.")
        rows.append((cid, c["name_ar"].strip(), name_fr.strip(), lat, lon, hs, hm))
        if max(miss, default=0) > 1 or len(miss) > 6 * len(days) // 10:
            print(f"  locality {cid}: {len(miss)} of {6 * len(days)} times off (worst {max(miss)} min)")
    share = sum(exact) / (6 * total)
    names = ("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    print(f"{len(rows)} localities, {len(dates)} days ({dates[0]} to {dates[-1]}), {6 * total} times")
    print("On the minute: " + ", ".join(f"{n} {100 * e / total:.1f}%" for n, e in zip(names, exact))
          + f"; all {100 * share:.2f}%; worst {worst} min")
    if share < CHECK_EXACT or worst > 1:
        raise SystemExit("The fit falls short of the check: nothing written.")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        out.write(f"# Morocco's Ministry of Habous: each locality's point and horizons (arc minutes below the horizon at "
                  f"sunrise, at Maghrib), fitted to its timetables {dates[0]} to {dates[-1]} by "
                  f"tools/AwanTV-build-habous.py {VERSION} ({100 * share:.2f}% on the minute, worst {worst} min)\n")
        out.write("# id\tname_ar\tname_latin\tlat\tlon\tsunrise\tmaghrib\n")
        for r in rows:
            out.write("\t".join(str(x) for x in r) + "\n")
    print(f"Written: {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
