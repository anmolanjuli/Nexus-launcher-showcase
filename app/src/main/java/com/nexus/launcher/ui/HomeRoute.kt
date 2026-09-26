package com.nexus.launcher.ui

import android.content.Context
import android.content.Intent

/**
 * The only correct way to bring the launcher forward from anywhere else — a widget, a
 * notification, Settings, the app icon.
 *
 * ## Why a plain `Intent(context, MainActivity::class.java)` is wrong here
 *
 * Android files every activity under an *activity type*, and a home screen lives in a task of type
 * HOME. It will only reuse that task for a launch that is itself typed HOME, and it types a launch
 * HOME only when the intent is a home intent — `ACTION_MAIN` with `CATEGORY_HOME` as its sole
 * category — *and* either names no component or comes from nowhere in particular. An explicit
 * component started from inside the app fails that test, is typed STANDARD, cannot join the home
 * task, and so — `singleTask` notwithstanding — gets a brand-new task with a second MainActivity
 * in it.
 *
 * That second instance is what made the drawer come up empty after using another app: each
 * instance has its own view model, and a fresh one starts with no app list. It also doubles the
 * launcher's memory for as long as both live — two canvases, two widget hosts, two icon caches.
 *
 * So every route in is an *implicit* home intent restricted to this package with [setPackage]: no
 * component, so Android types it HOME and hands it to the existing instance via `onNewIntent`.
 *
 * ## Carrying the real request
 *
 * A home intent's action has to be `ACTION_MAIN`, so the caller's own action rides along in an
 * extra, and [unwrap] puts it back before MainActivity reads the intent. That ordering matters:
 * MainActivity treats any `MAIN` + `HOME` intent as the Home button, which closes the drawer and
 * returns to the default page. A widget tap unwrapped too late would do all of that first.
 *
 * ## PendingIntent identity
 *
 * Every routed intent now compares equal under `Intent.filterEquals` — same action, category and
 * package — and `filterEquals` ignores extras. Two PendingIntents built from them are therefore
 * the *same* PendingIntent unless their request codes differ, and `FLAG_UPDATE_CURRENT` would let
 * one widget's tap carry another's extras. Each caller keeps its own request-code range for that
 * reason (search 0, calendar 0x100000+, weather 0x300000+, notes 0x400000+).
 */
object HomeRoute {

    private const val EXTRA_ROUTED = "com.nexus.launcher.extra.HOME_ROUTED"
    private const val EXTRA_ACTION = "com.nexus.launcher.extra.HOME_ROUTED_ACTION"

    /**
     * A plain Home: brings the existing launcher forward and behaves exactly like pressing the
     * Home button. What the app icon uses.
     */
    fun home(context: Context): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Brings the existing launcher forward to handle [action] (or only the extras, when null),
     * without the Home-button side effects. Add extras to the returned intent as usual.
     */
    fun to(context: Context, action: String? = null): Intent =
        home(context).apply {
            putExtra(EXTRA_ROUTED, true)
            if (action != null) putExtra(EXTRA_ACTION, action)
        }

    /**
     * Restores a routed intent to what its sender meant. Must run before anything inspects the
     * intent — see the note on this object about the Home-button check.
     *
     * A no-op on anything that was not built by [to], including a real Home press.
     */
    fun unwrap(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ROUTED, false) != true) return
        intent.action = intent.getStringExtra(EXTRA_ACTION)
        intent.removeCategory(Intent.CATEGORY_HOME)
        intent.removeExtra(EXTRA_ROUTED)
        intent.removeExtra(EXTRA_ACTION)
    }
}
