# Nexus Launcher — Agent Prompt Template

## Header (always include)
IMPORTANT: Do NOT run gradlew commands.
Developer runs builds manually.
Read docs/ai-workflow/AGENTS.md first.
Do not break any working feature.

## Files to Read First
Read these files before changing anything:
Only list files genuinely needed for this task.
Do not list files already in the agent's recent
context unless they may have drifted.
1. [primary file being modified]
2. [secondary file if needed]

## Context (optional, for complex tasks)
Brief explanation of what this task connects to
and why it matters.

## Constraints
Do not touch: [list files to leave alone]
Do not run gradlew.

## Task

TASK NAME: [short descriptive name]
Phase: Current

### What to Build
[Clear description of what needs to be built
or fixed. Be specific about behavior, not
implementation. Let the agent choose implementation
details unless a specific approach is required.
For surgical fixes, provide the exact old code
block and exact replacement so str_replace can
be used — this prevents partial application.]

### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

### File Size Limits
- [FileName.kt]: max [X] lines (currently N)
- Split into [HelperFile.kt] if needed

### If File Exceeds 400 Lines
Extract [specific logic] into [NewFile.kt]
Keep [specific logic] in original file.

## Completion Report
When done output:
1. Every file created or modified with line count
2. Confirm no file exceeds 400 lines
3. Confirm architectural invariants preserved
4. List any assumptions made
5. Flag any file approaching 400 lines (over 350)

## MANDATORY VERIFICATION STEP
This step is required. Do not skip it.

For every function you modified, print the
complete function body exactly as it now
exists in the file — not a summary, not
pseudocode, the actual committed code.

If the task involved a str_replace and it
failed to find the target string, report the
failure immediately. Do not attempt a
workaround. Do not claim success.

Do not fabricate logcat output. If the task
requires runtime verification (logcat, crash
trace), state clearly that device output is
needed and provide the exact adb command to
run. Never paste "expected" output as if it
were real.

## Test Checklist
After building, test these in order and report
exact pass/fail for each — no guessing:
- [ ] Test item 1
- [ ] Test item 2
- [ ] Test item 3

---

## Example Filled Prompt

IMPORTANT: Do NOT run gradlew commands.
Developer runs builds manually.
Read docs/ai-workflow/AGENTS.md first.
Do not break any working feature.

## Files to Read First
1. FolderSheetWallpaperDecor.kt

## Context
The dock visibility is being restored by
spurious callers while the sheet is still open.
The counter approach failed because 3 separate
callers each call setDockHidden(false) on dismiss.

## Constraints
Do not touch: DockItemMover.kt, HomeScreenViewModel.kt
Do not run gradlew.

## Task

TASK NAME: Dock Hide Flag Fix
Phase: Current

### What to Build
Replace the openSheetCount counter in
FolderSheetWallpaperDecor with a boolean flag.

Exact replacement — use str_replace:

OLD:
    private var openSheetCount = 0

    fun setDockHidden(context: Context, hidden: Boolean) {
        ...
        if (hidden) openSheetCount++
        else openSheetCount = (openSheetCount - 1)
            .coerceAtLeast(0)
        if (openSheetCount > 0) { hide dock }
        else { restore dock }
    }

NEW:
    @Volatile private var dockHiddenBySheet = false

    fun setDockHidden(context: Context, hidden: Boolean) {
        if (hidden) {
            dockHiddenBySheet = true
            // slide dock off screen
        } else {
            if (dockHiddenBySheet) {
                Log.d(TAG, "BLOCKED by sheet flag")
                return
            }
            // restore dock
        }
    }

    fun releaseDockHide(context: Context) {
        dockHiddenBySheet = false
        // restore dock directly
    }

### Acceptance Criteria
- [ ] setDockHidden(false) is blocked while sheet open
- [ ] releaseDockHide() is the only restore path
- [ ] Dock hidden when sheet opens
- [ ] Dock restored when sheet dismisses

### File Size Limits
- FolderSheetWallpaperDecor.kt: max 400 lines

## Completion Report
1. Line count of modified file
2. Confirm no file exceeds 400 lines
3. Confirm architectural invariants preserved
4. List assumptions

## MANDATORY VERIFICATION STEP
Print the complete setDockHidden() and
releaseDockHide() functions exactly as they
exist in the file after your changes.

## Test Checklist
- [ ] Open edit sheet — dock hidden
- [ ] While sheet open — dock stays hidden
- [ ] Dismiss sheet — dock restores
- [ ] Run: adb logcat -s FolderSheetDecor
      Paste actual output, not expected output
