package com.nexus.launcher.ui

import android.app.Activity
import android.os.Bundle

/**
 * The app-icon entry point. Draws nothing; hands straight off to the home screen and finishes.
 *
 * MainActivity used to carry the `LAUNCHER` category itself, so opening Nexus from its app icon —
 * from another launcher, from Play Store's Open button, or Android Studio after every install —
 * launched it as an ordinary app, in an ordinary task. The next Home press then needed a *home*
 * task, which Android will not satisfy with an ordinary one, so it created a second MainActivity.
 * See [HomeRoute] for the mechanics.
 *
 * Owning the `LAUNCHER` category here instead means the icon always sends a home intent, which
 * reaches the one existing instance. When Nexus is not the default launcher, the same intent still
 * opens it — restricted to this package, it cannot resolve to anything else.
 *
 * A plain [Activity] with `Theme.NoDisplay` on purpose: no AppCompat, no Hilt, no window. It must
 * finish before `onResume`, which a no-display theme requires.
 */
class LauncherEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(HomeRoute.home(this))
        finish()
    }
}
