"""Add English strings and their translations from a JSON batch, safely. Run from the repo root.

Batch file:
    {"default_file": "strings_x.xml", "locale_file": "strings_x.xml",
     "entries": [{"key": "k", "en": "only for new keys", "de": "...", "fr": "...", "pt-rBR": "..."}]}

Every entry field other than key / en / nofmt is a language, named by its res folder suffix
(values-<suffix>): "de", "fr", "es", "pt-rBR", "in", "hi", "ar", "iw", "ne".

- A new key ("en" given, key absent in values/) is appended to values/<default_file>.
- An "en" for a key that already exists must match the existing English exactly.
- A translation is skipped when that language already has the key in any of its files.
- Format placeholders (%1$s, %d ...) must match the English per language; set "nofmt": true on an
  entry whose English contains a literal % that is not a placeholder (e.g. "Set to 0% for ...").
- Text is XML-escaped and Android-escaped (apostrophes, quotes, a leading @ or ?). Write plain
  text in the JSON: an apostrophe as ', never \\'.
Run with --dry to validate without writing. Plurals are not handled; write those by hand.
"""
import glob
import io
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

RES = "app/src/main/res/"
FMT = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXc]")
RESERVED = {"key", "en", "nofmt"}


def names_and_values(folder):
    out = {}
    for path in glob.glob(RES + folder + "/strings*.xml"):
        for el in ET.parse(path).getroot():
            if el.get("name"):
                out[el.get("name")] = "".join(el.itertext())
    return out


def android_unescape(s):
    return re.sub(r"\\(['\"@?])", r"\1", s)


def escape(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = re.sub(r"(?<!\\)'", r"\\'", s)
    s = re.sub(r'(?<!\\)"', r'\\"', s)
    if s[:1] in "@?":
        s = "\\" + s
    return s


def placeholders(s):
    return sorted(m.group(0) for m in FMT.finditer(s))


def append(path, lines):
    if os.path.exists(path):
        text = io.open(path, encoding="utf-8", newline="").read()
        nl = "\r\n" if "\r\n" in text else "\n"
        idx = text.rindex("</resources>")
        text = text[:idx] + "".join("    " + l + nl for l in lines) + text[idx:]
    else:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nl = "\n"
        text = '<?xml version="1.0" encoding="utf-8"?>' + nl + "<resources>" + nl
        text += "".join("    " + l + nl for l in lines) + "</resources>" + nl
    io.open(path, "w", encoding="utf-8", newline="").write(text)


def main(batch_path, dry):
    batch = json.load(io.open(batch_path, encoding="utf-8"))
    langs = sorted({k for e in batch["entries"] for k in e} - RESERVED)
    en = names_and_values("values")
    loc = {l: names_and_values("values-" + l) for l in langs}
    new_default, new_locale, errors, skipped, seen = [], {l: [] for l in langs}, [], 0, set()
    for e in batch["entries"]:
        k = e["key"]
        if k in seen:
            errors.append("duplicate key in batch: " + k)
            continue
        seen.add(k)
        if "en" in e:
            if k in en:
                if android_unescape(en[k]) != e["en"]:
                    errors.append("english differs for existing %s: %r vs %r" % (k, en[k], e["en"]))
            else:
                new_default.append('<string name="%s">%s</string>' % (k, escape(e["en"])))
                en[k] = e["en"]
        if k not in en:
            errors.append("no english for " + k)
            continue
        source = placeholders(android_unescape(en[k]))
        for l in langs:
            if l not in e:
                errors.append("missing %s for %s" % (l, k))
                continue
            if k in loc[l]:
                skipped += 1
                continue
            if not e.get("nofmt") and placeholders(e[l]) != source:
                errors.append("placeholders differ %s/%s: %s vs %s" % (l, k, source, placeholders(e[l])))
                continue
            new_locale[l].append('<string name="%s">%s</string>' % (k, escape(e[l])))
    if errors:
        print("ERRORS:")
        for x in errors:
            print("  " + x)
        sys.exit(1)
    print("new english: %d; new translations: %s; already translated (skipped): %d"
          % (len(new_default), {l: len(v) for l, v in new_locale.items()}, skipped))
    if dry:
        return
    if new_default:
        append(RES + "values/" + batch["default_file"], new_default)
    for l, lines in new_locale.items():
        if lines:
            append(RES + "values-%s/%s" % (l, batch["locale_file"]), lines)
    print("written")


if __name__ == "__main__":
    main(sys.argv[1], "--dry" in sys.argv)
