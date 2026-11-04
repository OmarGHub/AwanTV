#!/usr/bin/env python3
# AwanTV-build-qr.py - Version 1.2.0
# Builds the QR codes in Settings > About: awanpro.app and the repository (SettingsActivity.qrPanel), as
# app/src/main/res/drawable-nodpi/qr_site.png and qr_repo.png, and the newest release's page (the new version's
# window, SettingsActivity.openUpdate) as qr_release.png: releases/latest, so one image serves every version; and a new
# issue on the repository as qr_issue.png (the crash screen's Details to report). One pixel per module and no border: the app draws
# each on a light rounded tile whose padding is the quiet zone, scaled up without smoothing, so it stays sharp on
# any screen. Dark ink on a light ground (phones read that way round best), the same on every colour theme. Level H
# (about 30% may be lost), since a logo covers the middle: AwanPro's on its site, Awan TV's arch on the repository.
# Run again when an address changes (Updates.SITE / Updates.PAGE / Updates.RELEASE in the app).
#
# Needs segno (BSD): pip install segno. The app carries only the images, not the library.
# Usage: python3 tools/AwanTV-build-qr.py
import os, sys

try:
    import segno
except ImportError:
    sys.exit("segno not found: pip install segno")

VERSION = "1.2.0"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
CODES = {
    "qr_site.png": "https://awanpro.app",
    "qr_repo.png": "https://github.com/OmarGHub/AwanTV",
    "qr_release.png": "https://github.com/OmarGHub/AwanTV/releases/latest",
    # The safety net's Details to report (CrashActivity, 1.13.0).
    "qr_issue.png": "https://github.com/OmarGHub/AwanTV/issues/new",
}
print("AwanTV-build-qr " + VERSION)
os.makedirs(OUT, exist_ok=True)
for name, url in CODES.items():
    q = segno.make(url, error="h", micro=False)
    path = os.path.join(OUT, name)
    q.save(path, scale=1, border=0, dark="#0C1B33", light="#EFE7D6")
    print("  %s  %s  (version %s, %d modules)" % (name, url, q.version, q.symbol_size(scale=1, border=0)[0]))
