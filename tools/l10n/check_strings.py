"""Read-only audit of every res/values*/strings*.xml. Changes nothing."""
import glob
import os
import re
import xml.etree.ElementTree as ET
from collections import defaultdict

RES = "app/src/main/res"
FMT = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc]")

parse_errors = []
by_folder = defaultdict(lambda: defaultdict(list))   # folder -> name -> [files]
text = defaultdict(dict)                             # folder -> name -> value
apostrophes = []

for path in sorted(glob.glob(os.path.join(RES, "values*", "strings*.xml"))):
    folder = os.path.basename(os.path.dirname(path))
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        parse_errors.append((path, str(e)))
        continue
    for el in root:
        if el.tag not in ("string", "plurals", "string-array"):
            continue
        name = el.get("name")
        by_folder[folder][name].append(os.path.basename(path))
        if el.tag == "string":
            value = "".join(el.itertext())
            text[folder][name] = value
            raw = value.strip()
            quoted = len(raw) >= 2 and raw[0] == '"' and raw[-1] == '"'
            if not quoted and re.search(r"(?<!\\)'", value):
                apostrophes.append((folder, name))

print("== XML parse errors:", len(parse_errors))
for p, e in parse_errors:
    print("  ", p, e)

print("== Duplicate names within one language folder:")
dup_total = 0
for folder, names in sorted(by_folder.items()):
    for name, files in sorted(names.items()):
        if len(files) > 1:
            dup_total += 1
            print("  ", folder, name, files)
print("   total:", dup_total)

print("== Unescaped apostrophes (aapt2 build error):", len(apostrophes))
for f, n in apostrophes[:30]:
    print("  ", f, n)

default = set(by_folder.get("values", {}))
print("== Translations with no default-locale string (ExtraTranslation):")
extra_total = 0
for folder in sorted(by_folder):
    if folder == "values" or not re.match(r"values-[a-z]{2}(-r[A-Z]{2})?$", folder):
        continue
    extra = sorted(set(by_folder[folder]) - default)
    if extra:
        extra_total += len(extra)
        print("  ", folder, len(extra), extra[:12])
print("   total:", extra_total)

print("== Format placeholders that differ from the default (StringFormatMatches):")
fmt_total = 0
for folder in sorted(text):
    if folder == "values" or not folder.startswith("values-"):
        continue
    for name, value in text[folder].items():
        if name in text["values"]:
            a = sorted(m.group(0)[-1] for m in FMT.finditer(text["values"][name]))
            b = sorted(m.group(0)[-1] for m in FMT.finditer(value))
            if a != b:
                fmt_total += 1
                if fmt_total <= 20:
                    print("  ", folder, name, "default", a, "translated", b)
print("   total:", fmt_total)

print("== Default strings missing per language (MissingTranslation, not build-blocking):")
for folder in sorted(by_folder):
    if folder == "values" or not re.match(r"values-[a-z]{2}(-r[A-Z]{2})?$", folder):
        continue
    missing = default - set(by_folder[folder])
    print("  ", folder, "missing", len(missing), "of", len(default))
