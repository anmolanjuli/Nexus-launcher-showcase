package com.nexus.launcher.util

/**
 * One switch for the launcher's temporary diagnostic logging.
 *
 * The diagnostic objects — `HomeGridOverlapDiag`, `HomeGridDropDiag`,
 * `HomeGridPlacementDiag`, `HomeEditBlurDiagnostics`, `DrawerPhysicsLog` — sit on layout, draw
 * and gesture paths. Several already de-duplicate their output by comparing a key against the
 * last one logged, which stops the *logging* but not the work: the key is a concatenation of a
 * dozen interpolated values, and it is built on every call whether or not anything is emitted.
 * `DrawerPhysicsLog` had already found this the hard way and gated itself; it was the only one.
 *
 * Gating on a `const val` matters. The Kotlin compiler folds `if (!NexusDiag.ENABLED) return`
 * against a compile-time constant and drops the rest of the function body, so a disabled
 * diagnostic costs the call and nothing else — no string building, no map lookup. A `var`, or a
 * value read from `BuildConfig`, would leave all of that in place.
 *
 * Flip to true to re-arm every diagnostic at once.
 */
object NexusDiag {
    const val ENABLED = false
}
