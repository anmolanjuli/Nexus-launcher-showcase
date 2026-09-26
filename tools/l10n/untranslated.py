"""Strings and plurals the app actually references (Kotlin R.string / R.plurals, XML @string) that
a language is missing. Run from the repo root.

    python tools/l10n/untranslated.py              # summary for every values-<lang> folder
    python tools/l10n/untranslated.py de --keys    # the missing keys for one language, one per line

Unreferenced strings are ignored on purpose: translating them is wasted work (see unused_strings.py).
"""
import glob
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

RES = "app/src/main/res/"
SRC = "app/src/main/java/"
LANG_FOLDER = re.compile(r"values-([a-z]{2}(?:-r[A-Z]{2})?)$")


def load(folder, tag):
    out = set()
    for path in glob.glob(RES + folder + "/strings*.xml"):
        for el in ET.parse(path).getroot():
            if el.tag == tag and el.get("name") and el.get("translatable") != "false":
                out.add(el.get("name"))
    return out


def referenced():
    used_s, used_p = set(), set()
    for path in glob.glob(SRC + "**/*.kt", recursive=True):
        text = io.open(path, encoding="utf-8").read()
        used_s.update(re.findall(r"R\.string\.([A-Za-z0-9_]+)", text))
        used_p.update(re.findall(r"R\.plurals\.([A-Za-z0-9_]+)", text))
    for path in glob.glob(RES + "**/*.xml", recursive=True) + ["app/src/main/AndroidManifest.xml"]:
        if re.search(r"[\\/]values", path):
            continue
        used_s.update(re.findall(r"@string/([A-Za-z0-9_]+)", io.open(path, encoding="utf-8").read()))
    return used_s, used_p


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    langs = args or sorted(m.group(1) for d in os.listdir(RES) for m in [LANG_FOLDER.match(d)] if m)
    used_s, used_p = referenced()
    en_s, en_p = load("values", "string"), load("values", "plurals")
    for lang in langs:
        folder = "values-" + lang
        miss_s = sorted(k for k in used_s & en_s if k not in load(folder, "string"))
        miss_p = sorted(k for k in used_p & en_p if k not in load(folder, "plurals"))
        if "--keys" in sys.argv:
            print("\n".join(miss_s + miss_p))
        else:
            print("%-12s strings missing: %4d   plurals missing: %d" % (lang, len(miss_s), len(miss_p)))


if __name__ == "__main__":
    main()
