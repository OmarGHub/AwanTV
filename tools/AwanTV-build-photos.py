#!/usr/bin/env python3
# AwanTV-build-photos.py - Version 1.0.0
# Builds app/src/main/assets/photos/ (Photos.java) from tools/photos-list.txt and a folder of the original photos
# (as downloaded from Unsplash, "<photographer>-<photo id>-unsplash.jpg"): each one cut to the screen's 16:9 from its
# middle, 1920x1080, WebP (quality 78), with photos.json: file, kind, where, light (its mean brightness, 0-255, for
# how dark the shade over it is) and the photographer and photo address for the credits.
#
# Usage: python3 tools/AwanTV-build-photos.py <folder with the original photos>
import json, os, re, sys
from PIL import Image, ImageStat

VERSION = "1.0.0"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIST = os.path.join(ROOT, "tools", "photos-list.txt")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "photos")
print("AwanTV-build-photos " + VERSION)
if len(sys.argv) != 2:
    sys.exit("usage: AwanTV-build-photos.py <folder with the original photos>")
SRC = sys.argv[1]
KINDS = {"makkah", "madinah", "aqsa", "mosque", "night", "nature", "quran"}

os.makedirs(OUT, exist_ok=True)
for f in os.listdir(OUT):
    if f.endswith(".webp"):
        os.remove(os.path.join(OUT, f))
items = []
for line in open(LIST, encoding="utf-8"):
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    name, kind, where = [p.strip() for p in line.split("|")]
    if kind not in KINDS or where not in ("all", "saver"):
        sys.exit("bad line: " + line)
    m = re.match(r"(.+)-([A-Za-z0-9_-]{11})-unsplash\.jpg$", name)
    if not m:
        sys.exit("not an Unsplash file name: " + name)
    im = Image.open(os.path.join(SRC, name)).convert("RGB")
    w, h = im.size
    if w < 1920 or h < 1080:
        sys.exit("smaller than the screen: %s (%dx%d)" % (name, w, h))
    if w * 9 >= h * 16:
        cw = h * 16 // 9
        im = im.crop(((w - cw) // 2, 0, (w - cw) // 2 + cw, h))
    else:
        ch = w * 9 // 16
        im = im.crop((0, (h - ch) // 2, w, (h - ch) // 2 + ch))
    im = im.resize((1920, 1080), Image.LANCZOS)
    n = len(items) + 1
    file = "%02d-%s.webp" % (n, kind)
    im.save(os.path.join(OUT, file), "WEBP", quality=78, method=6)
    light = round(ImageStat.Stat(im.convert("L").resize((192, 108))).mean[0])
    by = " ".join(p.capitalize() for p in m.group(1).split("-") if p)
    items.append({"file": file, "kind": kind, "where": where, "light": light, "by": by,
                  "url": "https://unsplash.com/photos/" + m.group(2)})
json.dump({"source": "Unsplash (Unsplash License)", "photos": items},
          open(os.path.join(OUT, "photos.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
size = sum(os.path.getsize(os.path.join(OUT, i["file"])) for i in items)
print("%d photos (%d for Home), %.1f MB, written to %s" % (len(items), sum(i["where"] == "all" for i in items),
      size / 1048576, os.path.relpath(OUT, ROOT)))
