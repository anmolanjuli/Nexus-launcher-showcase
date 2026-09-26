package com.nexus.launcher.ui.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.NexusDesignSystem

/**
 * Banner shown on the Nexus Settings landing hub when Nexus is not set as the default launcher.
 * Disappears automatically once Nexus has been granted default home status.
 */
class SettingsDefaultLauncherBanner(
    private val context: Context
) {
    private val density = context.resources.displayMetrics.density
    val view: LinearLayout = LinearLayout(context)
    private val iconView: ImageView
    private val titleView: TextView
    private val descView: TextView
    private val actionBtn: TextView

    init {
        view.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (16 * density).toInt()
            val padV = (14 * density).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * density).toInt()
                bottomMargin = (6 * density).toInt()
            }
            background = NexusDesignSystem.buildGlassRowBackground(context, density)
        }

        iconView = ImageView(context).apply {
            setImageResource(R.drawable.ic_home)
            val iconSize = (20 * density).toInt()
            val badgeSize = (36 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                marginEnd = (12 * density).toInt()
            }
            val pad = (badgeSize - iconSize) / 2
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
            }
        }
        view.addView(iconView)

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleView = TextView(context).apply {
            text = context.getString(R.string.settings_set_default_home_title)
        }
        descView = TextView(context).apply {
            text = context.getString(R.string.settings_set_default_home_desc)
        }
        textContainer.addView(titleView)
        textContainer.addView(descView)
        view.addView(textContainer)

        actionBtn = TextView(context).apply {
            text = context.getString(R.string.onboarding_default_home_btn)
            gravity = Gravity.CENTER
            val btnPadH = (14 * density).toInt()
            val btnPadV = (8 * density).toInt()
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
        }
        view.addView(actionBtn)

        view.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            requestSetDefault()
        }
        actionBtn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            requestSetDefault()
        }

        refreshVisibility()
    }

    fun applyTokens(tokens: NexusColorTokens) {
        view.background = NexusDesignSystem.buildGlassRowBackground(context, density)
        (iconView.background as? GradientDrawable)?.setColor(tokens.surfaceRaised)
        iconView.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        NexusTypeScale.bodyStrong.bindTo(titleView, tokens.textPrimary)
        NexusTypeScale.labelSmall.bindTo(descView, tokens.textSecondary)
        NexusTypeScale.bodyStrong.bindTo(actionBtn, tokens.textPrimary)
        actionBtn.background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 14 * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    fun refreshVisibility(isSearching: Boolean = false) {
        if (isSearching || isDefaultLauncher(context)) {
            view.visibility = View.GONE
        } else {
            view.visibility = View.VISIBLE
        }
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == context.packageName
    }

    /** Unwraps however many [ContextWrapper]s sit between here and the hosting Activity — a view
     *  built with a themed context is more than one deep, and the single-level unwrap this used
     *  to do returned null there. */
    private fun hostActivity(): Activity? {
        var candidate: Context? = context
        while (candidate is ContextWrapper) {
            if (candidate is Activity) return candidate
            candidate = candidate.baseContext
        }
        return null
    }

    private fun requestSetDefault() {
        val activity = hostActivity()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    // A role request must be started FOR RESULT. Launched with startActivity it
                    // is silently dropped on most devices, which is exactly what this banner did:
                    // tapping it appeared to do nothing at all. Without an Activity to start it
                    // from there is no way to ask for the role, so fall through to the settings
                    // screen rather than firing an intent that will be ignored.
                    if (activity != null) {
                        activity.startActivityForResult(intent, REQUEST_SET_DEFAULT_HOME)
                        return
                    }
                }
            }
        }
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        try {
            activity?.startActivity(intent) ?: context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            val homeIntent = Intent(Settings.ACTION_HOME_SETTINGS)
            activity?.startActivity(homeIntent) ?: context.startActivity(homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private companion object {
        /** Result is ignored — the banner re-checks the role in `onResume` either way. */
        const val REQUEST_SET_DEFAULT_HOME = 4021
    }
}
