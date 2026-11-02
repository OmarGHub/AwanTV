#!/usr/bin/env python3
# AwanTV-build-qr.py - Version 1.0.0
# Builds the two QR codes in Settings > About (SettingsActivity.qrPanel): awanpro.app and the repository, as
# app/src/main/res/drawable-nodpi/qr_site.png and qr_repo.png. One pixel per module and no border: the app draws
# each on a light rounded tile whose padding is the quiet zone, scaled up without smoothing, so it stays sharp on
# any screen. Dark ink on a light ground (phones read that way round best), the same on every colour theme. Level H
# (about 30% may be lost), since a logo covers the middle: AwanPro's on its site, Awan TV's arch on the repository.
# Run again when an address changes (Updates.SITE / Updates.PAGE in the app).
#
# Needs segno (BSD): pip install segno. The app carries only the images, not the library.
# Usage: python3 tools/AwanTV-build-qr.py
import os, sys

try:
    import segno
except ImportError:
    sys.exit("segno not found: pip install segno")

VERSION = "1.0.0"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
CODES = {
    "qr_site.png": "https://awanpro.app",
    "qr_repo.png": "https://github.com/OmarGHub/AwanTV",
}
print("AwanTV-build-qr " + VERSION)
os.makedirs(OUT, exist_ok=True)
for name, url in CODES.items():
    q = segno.make(url, error="h", micro=False)
    path = os.path.join(OUT, name)
    q.save(path, scale=1, border=0, dark="#0C1B33", light="#EFE7D6")
    print("  %s  %s  (version %s, %d modules)" % (name, url, q.version, q.symbol_size(scale=1, border=0)[0]))
