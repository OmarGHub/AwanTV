#!/usr/bin/env python3
# AwanTV-apply-labels.py - Version 1.0.0
# Writes the counts and references around the adhkar and hadiths in the screens' other languages (languages plan):
# for each tools/labels-<lang>.tsv ("English<tab>translation", # for comments), every item of
# app/src/main/assets/adhkar/adhkar.json and assets/hadith/hadith.json whose countEn / refEn is in the table gets
# count<Lang> / ref<Lang> (Languages.fields reads them; a missing one shows English). The words of the adhkar and
# hadiths are never touched. Run it after AwanTV-build-hadith.py (which calls it itself) and after editing a table.
# An English text with no translation is listed and stops the run, so no item is left half done.
#
# Usage: python3 tools/AwanTV-apply-labels.py
import glob, json, os, sys

VERSION = "1.0.0"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = [os.path.join(ROOT, "app", "src", "main", "assets", "adhkar", "adhkar.json"),
         os.path.join(ROOT, "app", "src", "main", "assets", "hadith", "hadith.json")]


def tables():
    out = {}
    for path in sorted(glob.glob(os.path.join(ROOT, "tools", "labels-*.tsv"))):
        lang = os.path.basename(path)[len("labels-"):-len(".tsv")]
        t = {}
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            en, tr = line.split("\t")
            t[en] = tr
        out[lang] = t
    return out


def main():
    print("AwanTV-apply-labels " + VERSION)
    langs = tables()
    missing = set()
    for path in FILES:
        data = json.load(open(path, encoding="utf-8"))
        n = 0
        for part in data.values():
            if not isinstance(part, list):
                continue
            for o in part:
                if not isinstance(o, dict):
                    continue
                for field in ("count", "ref"):
                    en = o.get(field + "En", "")
                    for lang, t in langs.items():
                        key = field + lang[:1].upper() + lang[1:]
                        if not en:
                            o.pop(key, None)
                        elif en in t:
                            o[key] = t[en]
                            n += 1
                        else:
                            missing.add((lang, en))
        if missing:
            continue
        text = json.dumps(data, ensure_ascii=False, indent=1)
        open(path, "w", encoding="utf-8").write(text)
        print("%s: %d labels" % (os.path.relpath(path, ROOT), n))
    if missing:
        for lang, en in sorted(missing):
            print("No %s for: %s" % (lang, en))
        sys.exit("Add them to tools/labels-<lang>.tsv; nothing was written.")


if __name__ == "__main__":
    main()
