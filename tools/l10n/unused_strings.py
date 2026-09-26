"""Default-locale strings and plurals nothing references. Run from the repo root.

    python tools/l10n/unused_strings.py            # list them with their file
    python tools/l10n/unused_strings.py --remove   # delete them from values/ and every values-<lang>/

"Referenced" means R.string.x / R.plurals.x in Kotlin, or @string/x in any non-values XML
(layouts, xml/, the manifest). Nothing in the app loads strings by name at runtime
(getIdentifier is only used for system dimens), so this is safe; check that is still true
(grep getIdentifier) before running --remove. Build after removing: lint catches anything missed.
"""
import glob
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

RES = "app/src/main/res/"
SRC = "app/src/main/java/"


def referenced():
    names = set()
    for path in glob.glob(SRC + "**/*.kt", recursive=True):
        names.update(re.findall(r"R\.(?:string|plurals)\.([A-Za-z0-9_]+)", io.open(path, encoding="utf-8").read()))
    for path in glob.glob(RES + "**/*.xml", recursive=True) + ["app/src/main/AndroidManifest.xml"]:
        if re.search(r"[\\/]values", path):
            continue
        names.update(re.findall(r"@string/([A-Za-z0-9_]+)", io.open(path, encoding="utf-8").read()))
    # A string can reference another string.
    for path in glob.glob(RES + "values/strings*.xml"):
        names.update(re.findall(r"@string/([A-Za-z0-9_]+)", io.open(path, encoding="utf-8").read()))
    return names


def main():
    used = referenced()
    unused = {}
    for path in sorted(glob.glob(RES + "values/strings*.xml")):
        for el in ET.parse(path).getroot():
            name = el.get("name")
            if el.tag in ("string", "plurals") and name and name not in used:
                unused[name] = os.path.basename(path)
    if "--remove" not in sys.argv:
        for name, f in sorted(unused.items(), key=lambda kv: (kv[1], kv[0])):
            print("%-34s %s" % (f, name))
        print("unused:", len(unused))
        return
    removed = 0
    block = re.compile(r'[ \t]*<(string|plurals)\s+name="(%s)"[^>]*>.*?</\1>[ \t]*\r?\n' %
                       "|".join(map(re.escape, unused)), re.S)
    for path in glob.glob(RES + "values*/strings*.xml"):
        text = io.open(path, encoding="utf-8", newline="").read()
        new, n = block.subn("", text)
        if n:
            io.open(path, "w", encoding="utf-8", newline="").write(new)
            removed += n
    print("removed %d entries across all languages (%d names)" % (removed, len(unused)))


if __name__ == "__main__":
    main()
