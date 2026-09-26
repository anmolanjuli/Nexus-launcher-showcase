package com.nexus.launcher.ui.onboarding

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.ui.folder.FolderBlurCoordinator

/**
 * Controller for the pilot onboarding permissions and default launcher flow.
 * Coordinates Dark Glass overlay presentation, backdrop blur, and state persistence.
 */
object OnboardingPilotController {

    private const val PREFS_NAME = "nexus_prefs"
    private const val KEY_COMPLETED = "pilot_permissions_completed"
    private const val IS_ENABLED = false

    private var activeSheet: OnboardingPermissionsSheet? = null
    private var activeScrim: View? = null

    fun isShowing(): Boolean = activeSheet != null

    fun checkAndShow(activity: Activity) {
        if (!IS_ENABLED) return
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_COMPLETED, false)) {
            return
        }

        if (activeSheet != null) {
            activeSheet?.refreshPermissionStates()
            return
        }

        val mainContainer = activity.findViewById<FrameLayout>(R.id.main_container) ?: return
        val density = activity.resources.displayMetrics.density

        FolderBlurCoordinator.setWorkspaceBlur(activity, true)

        val scrim = View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#80000000"))
            alpha = 0f
        }
        mainContainer.addView(scrim)
        scrim.animate().alpha(1f).setDuration(220).start()
        activeScrim = scrim

        val sheet = OnboardingPermissionsSheet(activity) {
            dismiss(activity)
        }
        val marginH = (20 * density).toInt()
        sheet.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            setMargins(marginH, 0, marginH, 0)
        }
        sheet.alpha = 0f
        sheet.scaleX = 0.94f
        sheet.scaleY = 0.94f
        sheet.translationY = 30f * density

        mainContainer.addView(sheet)
        sheet.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()

        activeSheet = sheet
    }

    fun onResume() {
        activeSheet?.refreshPermissionStates()
    }

    fun dismiss(activity: Activity) {
        val sheet = activeSheet ?: return
        val scrim = activeScrim
        val mainContainer = activity.findViewById<FrameLayout>(R.id.main_container)

        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, true)
            .apply()

        sheet.animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .translationY(20f * activity.resources.displayMetrics.density)
            .setDuration(200)
            .withEndAction { mainContainer?.removeView(sheet) }
            .start()

        scrim?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction { mainContainer?.removeView(scrim) }
            ?.start()

        activeSheet = null
        activeScrim = null

        FolderBlurCoordinator.setWorkspaceBlur(activity, false)
    }
}
