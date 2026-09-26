package com.nexus.launcher.ui.onboarding

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.NexusDesignSystem

/**
 * Dark Glass Setup & Permissions Sheet for Nexus Launcher onboarding.
 * Consolidates Default Launcher, Notification Badges, and Wallpaper Blur into one interface.
 */
class OnboardingPermissionsSheet(
    context: Context,
    private val onCompleted: () -> Unit
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    private lateinit var defaultHomeStatusView: TextView
    private lateinit var notificationsStatusView: TextView
    private lateinit var wallpaperStatusView: TextView

    init {
        orientation = VERTICAL
        val padH = (20 * density).toInt()
        val padV = (24 * density).toInt()
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#E60D1117"))
            cornerRadius = 24 * density
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }

        buildHeader()
        buildDivider()
        buildPermissionRows()
        buildFooter()
        refreshPermissionStates()
    }

    private fun buildHeader() {
        val badge = TextView(context).apply {
            text = context.getString(R.string.onboarding_pilot_brand_tag)
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 10 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            val bPadH = (12 * density).toInt()
            val bPadV = (4 * density).toInt()
            setPadding(bPadH, bPadV, bPadH, bPadV)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (12 * density).toInt()
            }
        }
        addView(badge)

        val title = TextView(context).apply {
            text = context.getString(R.string.onboarding_pilot_title)
            gravity = Gravity.CENTER
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * density).toInt()
            }
        }
        addView(title)

        val subtitle = TextView(context).apply {
            text = context.getString(R.string.onboarding_pilot_subtitle)
            gravity = Gravity.CENTER
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (20 * density).toInt()
            }
        }
        addView(subtitle)
    }

    private fun buildDivider() {
        val divider = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (1 * density).toInt().coerceAtLeast(1)).apply {
                bottomMargin = (16 * density).toInt()
            }
            setBackgroundColor(tokens.divider)
        }
        addView(divider)
    }

    private fun buildPermissionRows() {
        defaultHomeStatusView = createRow(
            iconRes = R.drawable.ic_home,
            title = context.getString(R.string.onboarding_default_home_title),
            desc = context.getString(R.string.onboarding_default_home_desc),
            actionLabel = context.getString(R.string.onboarding_default_home_btn)
        ) { requestDefaultLauncher() }

        notificationsStatusView = createRow(
            iconRes = R.drawable.ic_notification,
            title = context.getString(R.string.onboarding_notifications_title),
            desc = context.getString(R.string.onboarding_notifications_desc),
            actionLabel = context.getString(R.string.onboarding_notifications_btn)
        ) { requestNotificationAccess() }

        wallpaperStatusView = createRow(
            iconRes = R.drawable.ic_palette_theme,
            title = context.getString(R.string.onboarding_wallpaper_blur_title),
            desc = context.getString(R.string.onboarding_wallpaper_blur_desc),
            actionLabel = context.getString(R.string.onboarding_wallpaper_blur_btn)
        ) { requestStoragePermission() }
    }

    private fun createRow(
        iconRes: Int,
        title: String,
        desc: String,
        actionLabel: String,
        onClick: () -> Unit
    ): TextView {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * density).toInt()
            }
            val rPadH = (14 * density).toInt()
            val rPadV = (12 * density).toInt()
            setPadding(rPadH, rPadV, rPadH, rPadV)
            background = NexusDesignSystem.buildGlassRowBackground(context, density)
        }

        val iconBadge = ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            val iconSize = (20 * density).toInt()
            val badgeSize = (38 * density).toInt()
            layoutParams = LayoutParams(badgeSize, badgeSize).apply { marginEnd = (12 * density).toInt() }
            val pad = (badgeSize - iconSize) / 2
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surfaceRaised)
            }
        }
        row.addView(iconBadge)

        val textContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(context).apply {
            text = title
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
        }
        val descView = TextView(context).apply {
            text = desc
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
        }
        textContainer.addView(titleView)
        textContainer.addView(descView)
        row.addView(textContainer)

        val actionBtn = TextView(context).apply {
            text = actionLabel
            gravity = Gravity.CENTER
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            val btnPadH = (14 * density).toInt()
            val btnPadV = (8 * density).toInt()
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 14 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * density).toInt()
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
        row.addView(actionBtn)
        addView(row)
        return actionBtn
    }

    private fun buildFooter() {
        val continueBtn = TextView(context).apply {
            text = context.getString(R.string.onboarding_pilot_continue)
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 16 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            val btnHeight = (48 * density).toInt()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, btnHeight).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (8 * density).toInt()
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onCompleted()
            }
        }
        addView(continueBtn)

        val skipBtn = TextView(context).apply {
            text = context.getString(R.string.onboarding_pilot_skip)
            gravity = Gravity.CENTER
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            val btnHeight = (36 * density).toInt()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, btnHeight)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCompleted()
            }
        }
        addView(skipBtn)
    }

    fun refreshPermissionStates() {
        val isDefault = isDefaultLauncher()
        updatePillState(
            defaultHomeStatusView,
            isGranted = isDefault,
            grantedText = context.getString(R.string.onboarding_default_home_granted),
            pendingText = context.getString(R.string.onboarding_default_home_btn)
        )

        val isNotifGranted = isNotificationListenerGranted()
        updatePillState(
            notificationsStatusView,
            isGranted = isNotifGranted,
            grantedText = context.getString(R.string.onboarding_notifications_granted),
            pendingText = context.getString(R.string.onboarding_notifications_btn)
        )

        val isStorageGranted = isStoragePermissionGranted()
        updatePillState(
            wallpaperStatusView,
            isGranted = isStorageGranted,
            grantedText = context.getString(R.string.onboarding_wallpaper_blur_granted),
            pendingText = context.getString(R.string.onboarding_wallpaper_blur_btn)
        )
    }

    private fun updatePillState(view: TextView, isGranted: Boolean, grantedText: String, pendingText: String) {
        if (isGranted) {
            view.text = "✓ $grantedText"
            view.setTextColor(tokens.textPrimary)
            view.background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            view.isClickable = false
        } else {
            view.text = pendingText
            view.setTextColor(tokens.textPrimary)
            view.background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 14 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            view.isClickable = true
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == context.packageName
    }

    private fun isNotificationListenerGranted(): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(context.packageName) == true
    }

    private fun isStoragePermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestDefaultLauncher() {
        val activity = context as? Activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    activity.startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            }
        }
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun requestNotificationAccess() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun requestStoragePermission() {
        val activity = context as? Activity ?: return
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        activity.requestPermissions(arrayOf(permission), 123)
    }
}
