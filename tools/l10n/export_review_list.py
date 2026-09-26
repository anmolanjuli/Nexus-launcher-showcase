"""Export a comprehensive review list of English and all translated languages:
- TRANSLATIONS_REVIEW.md (Markdown organized by feature component)
- TRANSLATIONS_REVIEW.csv (Spreadsheet matrix for Excel/Google Sheets)
- TRANSLATIONS_REVIEW.html (Interactive side-by-side search & review tool)
Run from repo root.
"""
import glob
import io
import csv
import json
import os
import re
import html
import xml.etree.ElementTree as ET

RES = "app/src/main/res"

LANG_ORDER = [
    ("es", "Spanish (Español)"),
    ("pt-rBR", "Portuguese (Português - Brasil)"),
    ("de", "German (Deutsch)"),
    ("fr", "French (Français)"),
    ("in", "Indonesian (Bahasa Indonesia)"),
    ("hi", "Hindi (हिन्दी)"),
    ("ar", "Arabic (العربية)"),
    ("iw", "Hebrew (עבריत)"),
    ("ne", "Nepali (नेपाली)")
]

CATEGORY_TITLES = {
    "strings_dock.xml": "Dock & Bottom Bar",
    "strings_drawer.xml": "App Drawer (Core)",
    "strings_drawer_extra.xml": "App Drawer (Search & Layout)",
    "strings_folder.xml": "Folder Shapes & Menus",
    "strings_edit_sheets.xml": "Icon & App Editing Sheets",
    "strings_edit_sheets_extra.xml": "Folder & Mosaic Sheet Actions",
    "strings_home.xml": "Home Screen Pages & Grid",
    "strings_home_settings.xml": "Home Screen Transitions & Appearance",
    "strings_gestures.xml": "Gestures & Navigation",
    "strings_search_notifications.xml": "Search Engine & Notification Badges",
    "strings_theme.xml": "Themes, Typography & Locales",
    "strings_feed.xml": "News & Content Feed",
    "strings_premium.xml": "Nexus Premium Features & Showcase",
    "strings_settings_hub.xml": "Settings Hub & Paywall Transcreation",
    "strings_settings_extra.xml": "System Settings, Wallpaper & Telemetry",
    "strings_widgets.xml": "Widget Core & Selection",
    "strings_widgets_extra.xml": "Widgets (Clock, Battery, Weather, Notes, Mosaic, Progress)",
    "strings_ui_extra.xml": "Categories, Selection Bar & Count Plurals",
    "strings_l10n_a.xml": "Document Reader (PDF/EPUB), Shortcuts & Urgency",
    "strings_l10n_b.xml": "Onboarding Permissions, Backup & Accessibility",
    "strings.xml": "Core Dialogs, Actions & Formatting"
}

def unescape_for_display(s):
    if not s:
        return ""
    # Android unescape
    s = re.sub(r"\\(['\"@?])", r"\1", s)
    s = s.replace("\\n", "\n")
    return s

def load_lang_data(folder):
    strings = {}
    plurals = {}
    for p in sorted(glob.glob(os.path.join(RES, folder, "strings*.xml"))):
        fname = os.path.basename(p)
        try:
            root = ET.parse(p).getroot()
        except Exception:
            continue
        for el in root:
            name = el.get("name")
            if not name:
                continue
            if el.tag == "string":
                val = unescape_for_display("".join(el.itertext()))
                strings[name] = (fname, val)
            elif el.tag == "plurals":
                items = {}
                for item in el:
                    qty = item.get("quantity")
                    items[qty] = unescape_for_display("".join(item.itertext()))
                plurals[name] = (fname, items)
    return strings, plurals

def format_plural_str(items):
    return " | ".join(f"[{k}] {v}" for k, v in items.items())

def build_data():
    en_s, en_p = load_lang_data("values")
    lang_data = {}
    for code, _ in LANG_ORDER:
        lang_data[code] = load_lang_data("values-" + code)
    
    # Collect all items grouped by template file
    template_files = sorted(CATEGORY_TITLES.keys())
    all_by_file = {fname: [] for fname in template_files}
    
    # We use values-ar as reference for the 21 modular files
    ref_s, ref_p = lang_data["ar"]
    all_keys = set(ref_s.keys()) | set(ref_p.keys())
    
    for key in sorted(all_keys):
        if key in ref_s:
            fname = ref_s[key][0]
            tag = "string"
            en_val = en_s.get(key, ("", "")) [1] if key in en_s else ""
        else:
            fname = ref_p[key][0]
            tag = "plurals"
            en_val = en_p.get(key, ("", {})) [1] if key in en_p else {}
            
        if fname not in all_by_file:
            all_by_file[fname] = []
        all_by_file[fname].append((tag, key, en_val))
        
    return all_by_file, lang_data

def generate_markdown(all_by_file, lang_data):
    md = []
    md.append("# Nexus Launcher — Translation Review List")
    md.append("")
    md.append("This document contains all English strings and their translated versions across all **9 localized languages**:")
    for code, label in LANG_ORDER:
        md.append(f"- **{code}**: {label}")
    md.append("")
    md.append("---")
    md.append("")
    
    total_items = 0
    for fname, items in all_by_file.items():
        title = CATEGORY_TITLES.get(fname, fname)
        md.append(f"## {title} (`{fname}` — {len(items)} strings)")
        md.append("")
        for tag, key, en_val in items:
            total_items += 1
            md.append(f"### `{key}`")
            if tag == "string":
                md.append(f"- **EN (English)**: {en_val}")
                for code, label in LANG_ORDER:
                    trans_s, _ = lang_data[code]
                    val = trans_s.get(key, ("", ""))[1]
                    md.append(f"- **{code} ({label.split(' ')[0]})**: {val}")
            else:
                md.append(f"- **EN (English)** [Plural]: {format_plural_str(en_val)}")
                for code, label in LANG_ORDER:
                    _, trans_p = lang_data[code]
                    val = trans_p.get(key, ("", {}))[1]
                    md.append(f"- **{code} ({label.split(' ')[0]})** [Plural]: {format_plural_str(val)}")
            md.append("")
        md.append("---")
        md.append("")
        
    print(f"Generated Markdown with {total_items} items.")
    return "\n".join(md)

def generate_csv(all_by_file, lang_data):
    output = io.StringIO()
    writer = csv.writer(output)
    
    header = ["Component", "Type", "String Key", "English (Base)"]
    for code, label in LANG_ORDER:
        header.append(f"{label} [{code}]")
    writer.writerow(header)
    
    for fname, items in all_by_file.items():
        comp_title = CATEGORY_TITLES.get(fname, fname)
        for tag, key, en_val in items:
            row = [comp_title, tag, key]
            if tag == "string":
                row.append(en_val)
                for code, _ in LANG_ORDER:
                    trans_s, _ = lang_data[code]
                    row.append(trans_s.get(key, ("", ""))[1])
            else:
                row.append(format_plural_str(en_val))
                for code, _ in LANG_ORDER:
                    _, trans_p = lang_data[code]
                    row.append(format_plural_str(trans_p.get(key, ("", {}))[1]))
            writer.writerow(row)
            
    return output.getvalue()

def generate_html(all_by_file, lang_data):
    items_json = []
    for fname, items in all_by_file.items():
        comp_title = CATEGORY_TITLES.get(fname, fname)
        for tag, key, en_val in items:
            item_entry = {
                "file": fname,
                "category": comp_title,
                "type": tag,
                "key": key,
                "en": en_val if tag == "string" else format_plural_str(en_val),
                "translations": {}
            }
            for code, _ in LANG_ORDER:
                if tag == "string":
                    val = lang_data[code][0].get(key, ("", ""))[1]
                else:
                    val = format_plural_str(lang_data[code][1].get(key, ("", {}))[1])
                item_entry["translations"][code] = val
            items_json.append(item_entry)

    langs_json = [{"code": code, "name": label} for code, label in LANG_ORDER]

    html_content = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Nexus Launcher — Translation Review</title>
<style>
  :root {{
    --bg: #0d1117;
    --surface: #161b22;
    --surface-elevated: #21262d;
    --border: #30363d;
    --accent: #58a6ff;
    --accent-hover: #79c0ff;
    --text: #c9d1d9;
    --text-muted: #8b949e;
    --text-bright: #f0f6fc;
    --tag-bg: #1f242c;
    --code-bg: rgba(110, 118, 129, 0.15);
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.5;
    padding: 24px;
  }}
  header {{
    max-width: 1400px;
    margin: 0 auto 20px auto;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--border);
  }}
  h1 {{
    color: var(--text-bright);
    font-size: 24px;
    font-weight: 600;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 12px;
  }}
  .badge {{
    font-size: 12px;
    background: rgba(56, 139, 253, 0.15);
    color: var(--accent);
    padding: 2px 10px;
    border-radius: 20px;
    border: 1px solid rgba(56, 139, 253, 0.3);
  }}
  .controls {{
    max-width: 1400px;
    margin: 0 auto 20px auto;
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    align-items: center;
  }}
  .search-input {{
    flex: 1;
    min-width: 280px;
    background: var(--surface);
    border: 1px solid var(--border);
    color: var(--text-bright);
    padding: 10px 14px;
    border-radius: 8px;
    font-size: 14px;
    outline: none;
    transition: border-color 0.2s;
  }}
  .search-input:focus {{
    border-color: var(--accent);
  }}
  .select-filter {{
    background: var(--surface);
    border: 1px solid var(--border);
    color: var(--text-bright);
    padding: 10px 14px;
    border-radius: 8px;
    font-size: 14px;
    cursor: pointer;
  }}
  .lang-tabs {{
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    width: 100%;
    margin-top: 8px;
  }}
  .lang-tab {{
    background: var(--surface);
    border: 1px solid var(--border);
    color: var(--text-muted);
    padding: 6px 14px;
    border-radius: 6px;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s;
    user-select: none;
  }}
  .lang-tab:hover {{
    color: var(--text-bright);
    border-color: var(--text-muted);
  }}
  .lang-tab.active {{
    background: var(--accent);
    color: #0d1117;
    font-weight: 600;
    border-color: var(--accent);
  }}
  .stats {{
    font-size: 13px;
    color: var(--text-muted);
    margin-left: auto;
  }}
  .container {{
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }}
  .card {{
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    transition: border-color 0.15s;
  }}
  .card:hover {{
    border-color: #484f58;
  }}
  .card-header {{
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    color: var(--text-muted);
  }}
  .key-name {{
    font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
    color: var(--accent);
    font-weight: 600;
    background: var(--code-bg);
    padding: 2px 6px;
    border-radius: 4px;
  }}
  .category-tag {{
    background: var(--tag-bg);
    padding: 2px 8px;
    border-radius: 4px;
    border: 1px solid var(--border);
  }}
  .content-grid {{
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }}
  @media (max-width: 800px) {{
    .content-grid {{ grid-template-columns: 1fr; }}
  }}
  .content-box {{
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 12px;
  }}
  .box-label {{
    font-size: 11px;
    text-transform: uppercase;
    font-weight: 700;
    letter-spacing: 0.5px;
    color: var(--text-muted);
    margin-bottom: 6px;
    display: flex;
    justify-content: space-between;
  }}
  .box-text {{
    color: var(--text-bright);
    font-size: 14px;
    white-space: pre-wrap;
    word-break: break-word;
  }}
  .rtl {{
    direction: rtl;
    text-align: right;
  }}
</style>
</head>
<body>

<header>
  <h1>Nexus Launcher — All Translations Review <span class="badge">1,203 Strings × 9 Languages</span></h1>
  <p style="color: var(--text-muted); font-size: 14px;">Instant side-by-side review tool covering base English and all localized language resources.</p>
</header>

<div class="controls">
  <input type="text" id="searchInput" class="search-input" placeholder="Search by key name, English text, or translation...">
  
  <select id="categorySelect" class="select-filter">
    <option value="all">All Components (All 21 Files)</option>
  </select>

  <div class="stats" id="stats">Showing 1203 items</div>

  <div class="lang-tabs" id="langTabs"></div>
</div>

<div class="container" id="cardsContainer"></div>

<script>
  const ITEMS = {json.dumps(items_json)};
  const LANGS = {json.dumps(langs_json)};
  let currentLang = "es";

  // Init Category filter
  const catSelect = document.getElementById("categorySelect");
  const categories = [...new Set(ITEMS.map(i => i.category))];
  categories.forEach(c => {{
    const opt = document.createElement("option");
    opt.value = c;
    opt.textContent = c;
    catSelect.appendChild(opt);
  }});

  // Init Language Tabs
  const tabsContainer = document.getElementById("langTabs");
  LANGS.forEach(l => {{
    const tab = document.createElement("div");
    tab.className = "lang-tab" + (l.code === currentLang ? " active" : "");
    tab.textContent = l.name;
    tab.onclick = () => {{
      document.querySelectorAll(".lang-tab").forEach(t => t.classList.remove("active"));
      tab.classList.add("active");
      currentLang = l.code;
      render();
    }};
    tabsContainer.appendChild(tab);
  }});

  function render() {{
    const query = document.getElementById("searchInput").value.toLowerCase();
    const cat = catSelect.value;
    const container = document.getElementById("cardsContainer");
    container.innerHTML = "";

    const filtered = ITEMS.filter(item => {{
      if (cat !== "all" && item.category !== cat) return false;
      if (!query) return true;
      const trans = (item.translations[currentLang] || "").toLowerCase();
      return item.key.toLowerCase().includes(query) ||
             item.en.toLowerCase().includes(query) ||
             trans.includes(query);
    }});

    document.getElementById("stats").textContent = `Showing ${{filtered.length}} of ${{ITEMS.length}} items`;

    const isRtl = currentLang === "ar" || currentLang === "iw";
    const currentLangObj = LANGS.find(l => l.code === currentLang);

    filtered.forEach(item => {{
      const card = document.createElement("div");
      card.className = "card";

      const transVal = item.translations[currentLang] || "";

      card.innerHTML = `
        <div class="card-header">
          <div><span class="key-name">${{item.key}}</span> ${{item.type === "plurals" ? '<span style="color:#d29922; font-size:11px; margin-left:6px;">[PLURAL]</span>' : ''}}</div>
          <div class="category-tag">${{item.category}}</div>
        </div>
        <div class="content-grid">
          <div class="content-box">
            <div class="box-label">English (Base)</div>
            <div class="box-text">${{escapeHtml(item.en)}}</div>
          </div>
          <div class="content-box">
            <div class="box-label"><span>${{currentLangObj.name}}</span><span>${{currentLang}}</span></div>
            <div class="box-text ${{isRtl ? 'rtl' : ''}}">${{escapeHtml(transVal)}}</div>
          </div>
        </div>
      `;
      container.appendChild(card);
    }});
  }}

  function escapeHtml(str) {{
    if (!str) return "";
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }}

  document.getElementById("searchInput").addEventListener("input", render);
  catSelect.addEventListener("change", render);

  render();
</script>

</body>
</html>
"""
    return html_content

def main():
    print("Loading all strings from resources...")
    all_by_file, lang_data = build_data()
    
    print("Writing TRANSLATIONS_REVIEW.md...")
    md_content = generate_markdown(all_by_file, lang_data)
    with open("TRANSLATIONS_REVIEW.md", "w", encoding="utf-8") as f:
        f.write(md_content)
    print("Wrote TRANSLATIONS_REVIEW.md")
    
    print("Writing TRANSLATIONS_REVIEW.csv...")
    csv_content = generate_csv(all_by_file, lang_data)
    with open("TRANSLATIONS_REVIEW.csv", "w", encoding="utf-8") as f:
        f.write(csv_content)
    print("Wrote TRANSLATIONS_REVIEW.csv")
    
    print("Writing TRANSLATIONS_REVIEW.html...")
    html_content = generate_html(all_by_file, lang_data)
    with open("TRANSLATIONS_REVIEW.html", "w", encoding="utf-8") as f:
        f.write(html_content)
    print("Wrote TRANSLATIONS_REVIEW.html")
    
    print("All review files successfully generated!")

if __name__ == "__main__":
    main()
