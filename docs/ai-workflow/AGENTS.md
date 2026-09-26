# AGENTS.md — Nexus Launcher

## Agent Guidelines & Engineering Standards

## Hard Rules
- No file over 400 lines — split proactively, BEFORE hitting the limit, not after (if a task would push a file past ~370 lines, extract first)
- No XML over 200 lines
- One class per file
- No GlobalScope
- All DB/IO on Dispatchers.IO
- Never run gradlew
- No new dependencies without approval
- No hardcoded hex colors or pixel sizes
- No Paint allocation inside onDraw()
- No RecyclerView, no Compose, no PopupWindow
- No Fragments except SettingsFragment
- Never reorder LauncherDrawEngine.onDraw() layers
- Widget host context: never use Activity or AppCompat-themed context for widget view creation.
- Any API requiring 31+ (RenderEffect, setRenderEffect, etc.) MUST be guarded with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` — minSdk is 26, ungated calls compile but crash/no-op on real devices below 31.
- All user-facing strings must be defined in strings.xml — use `<plurals>` for count-dependent strings, format strings (`%1$s`, `%1$d`) for dynamic parameters, never hardcode UI copy in layouts, Kotlin dialogs, or Canvas drawText calls (brand text like "NEXUS" marked `translatable="false"`).

## Single Source of Truth — No Duplicate Geometry/Logic
`getShapePath()` in `FolderIconShapeDraw.kt` is the ONLY place folder shape geometry is defined. Every consumer (resize outline, flip mirroring, long-press highlight, preview-icon clipping, folder-open morph animation, scrim punch-out) MUST call it — never reimplement shape math independently.

**Whenever a new folder shape is added, or an existing one is changed, explicitly verify all consumers before considering it done.**

This principle generalizes beyond folder shapes: if the same calculation (sizing, positioning, geometry) is needed in two places, extract it to one function and call it from both. Never let a "preview" or "settings mockup" version of a calculation exist independently from the "real" version — they will drift, and drift is very hard to notice until a user reports it.

## Page-Type Awareness
`HomeScreenItem.column`/`row` mean different things on different page types (grid coordinates vs. custom page layouts). Any code that reads these fields for positional math (occupancy, drag-drop collision, resize, hit-testing) MUST explicitly check the current page's type first and branch or reject accordingly — never assume every page is a grid page.

## No Silent Fallbacks
Avoid `when` branches or `coerceIn(...)` clamps with a generic `else -> <simple shape/behavior>` for anything that's supposed to represent a specific, distinct case. A silent fallback produces something that visually "works" well enough to ship unnoticed, and then costs many rounds to trace later. If a case isn't implemented yet, prefer a loud, obvious placeholder or an explicit TODO over a fallback that looks plausible.

## Blur / RenderEffect Reliability
A single, instantaneous `view.setRenderEffect(...)` call can have its first-frame invalidation silently dropped if it coincides with concurrent view-hierarchy mutation. Prefer animating the effect in over a short duration (ValueAnimator, similar to `FolderBlurCoordinator`'s proven pattern) rather than a single-shot call, especially for anything applied immediately on a screen's first open.

## New Persisted Fields / Config
Any new field added to a persisted config (FolderConfig, etc.) must have a safe default and a confirmed-working migration path for data saved BEFORE the field existed — verify this explicitly (what happens when old JSON/prefs lacking the key is loaded), don't just assume serialization handles it silently correctly.

## RTL & Spatial Canvas Architecture (Fixed Spatial Canvas)
Home screen canvas coordinate system (icon grid, widget placement) is fixed and independent of layout direction — it never mirrors. RTL mirroring applies only to reading-direction UI: drawer rail position, text alignment, navigation chevrons, settings/dialogs. All overlay containers, context menus, resize overlays, and widget host layouts MUST lock their coordinate space to `LAYOUT_DIRECTION_LTR` and `Gravity.LEFT` so overlay math matches Canvas absolute screen coordinates 1:1 in all locales.

## Verification Standard
"Verified via code inspection only" and "confirmed working" are NOT the same claim — always state explicitly which one applies. Code inspection can confirm a fix is *present*; it cannot confirm a fix *works* on a real device. Do not report something as fixed/working based on reading code alone if the task's acceptance criteria require an observable behavior — say "implemented, not yet device-tested" instead.

## Completion Report — Required Every Task
1. Files modified with line counts
2. Confirm no file over 400 lines
3. Confirm architectural invariants preserved
4. Assumptions made
5. Files approaching 400 lines
6. Explicitly state verification method: device-tested, or code-inspection-only
