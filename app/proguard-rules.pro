# ============================================================================
# Nexus Launcher — R8 configuration
#
# Release builds run R8 (isMinifyEnabled = true). R8 deletes code nothing
# calls and renames what is left. It decides that by reading the code, so
# anything found by NAME at runtime — reflection — is invisible to it and has
# to be kept explicitly here. Everything below exists for a specific reason;
# don't trim a rule without checking what reads that name at runtime.
# ============================================================================

# --- Readable crash reports -------------------------------------------------
# Without these every stack trace from a release build is unusable. R8 writes
# app/build/outputs/mapping/release/mapping.txt on each build; keep the copy
# that matches a build you actually installed, or its traces can't be decoded.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Gson -------------------------------------------------------------------
# Gson maps a JSON key to a field of the same name, by reflection. If R8
# renames the field, the key silently stops matching: no crash, no warning,
# just a default value where the saved one used to be.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses

# FolderConfig is the serious one. It is serialised into the folderConfigJson
# COLUMN of the home_screen_items table (FolderConfigCodec), so its field names
# are baked into data already on the device. Rename them and every existing
# folder's shape, colour, gestures and opacity silently revert to defaults on
# first launch — and get rewritten under the new names, so rolling back doesn't
# recover them.
-keep class com.nexus.launcher.data.FolderConfig { *; }

# NexusSettingsData is rebuilt from the backup's settings snapshot by name
# (BackupSnapshotRestorer). Renaming its 52 fields makes every restore produce
# a wholly default settings object without failing.
-keep class com.nexus.launcher.data.prefs.NexusSettingsData { *; }

# Room entities and the rest of the persistence models. Room itself generates
# code rather than reflecting, but these are cheap to keep and are exactly the
# shapes that also travel through Gson and the backup format.
-keep class com.nexus.launcher.data.** { *; }

# --- Enums stored by name ---------------------------------------------------
# ThemeMode, CalmPalette, ColorBlindMode, BackgroundLayerMode, DockBackgroundMode,
# LabelVisibility and friends are written to DataStore as `.name` and read back
# with `valueOf(stored)`. The constant names are the storage format.
-keepclassmembers enum com.nexus.launcher.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Hilt entry points ------------------------------------------------------
# Fetched as `EntryPointAccessors.fromApplication(ctx, SomeEntryPoint::class.java)`
# from plain objects and views all over the UI layer. Hilt ships most of its own
# rules; this covers the interfaces those call sites name directly.
-keep @dagger.hilt.EntryPoint interface * { *; }

# --- Strip debug logging from release --------------------------------------
# The counterpart to NexusDiag: that constant silences the diagnostics we own,
# this removes the ~100 event-driven Log.d calls in backup import, drop handling
# and sheet lifecycle without editing any of them. Log.w / Log.e / Log.i stay —
# they are what makes a real crash report legible.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
