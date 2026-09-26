package com.nexus.launcher.feed

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

class NexusFeedArticleMenuSheet : BottomSheetDialogFragment() {

    private val dp get() = resources.displayMetrics.density
    private lateinit var tokens: NexusColorTokens
    var article: FeedArticle? = null
    var isBookmarked: Boolean = false
    var onBookmarkToggle: (() -> Unit)? = null
    var onHideArticle: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext()).apply {
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0.72f)
                // Blurs only in Frosted Glass; the dim above carries the separation otherwise.
                com.nexus.launcher.ui.glass.FloatingSurfaces.applyBlurBehind(win)
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            setOnShowListener { d ->
                val sheet = (d as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                sheet?.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val art = article ?: return View(requireContext())
        tokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val outerRoot = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            // Was an opaque `surface` slab even under Frosted Glass, over a blur it could not show.
            background = com.nexus.launcher.ui.glass.FloatingSurfaces.sheetCard(tokens, 24 * dp, dp)
            setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        root.addView(View(requireContext()).apply {
            background = GradientDrawable().apply { setColor(tokens.divider); cornerRadius = 2 * dp }
            layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = (16 * dp).toInt()
            }
        })

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())
            addView(TextView(requireContext()).apply {
                text = art.sourceName
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
            addView(TextView(requireContext()).apply {
                text = art.title
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                maxLines = 2
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * dp).toInt()
                }
            })
        }
        root.addView(header)

        root.addView(View(requireContext()).apply {
            background = ColorDrawable(tokens.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()).apply {
                bottomMargin = (12 * dp).toInt()
            }
        })

        val bookmarkLabel = if (isBookmarked) getString(R.string.nexus_feed_remove_from_saved) else getString(R.string.nexus_feed_save_story)
        val bookmarkIcon = if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
        root.addView(createMenuItem(bookmarkLabel, bookmarkIcon) {
            onBookmarkToggle?.invoke()
            dismiss()
        })

        root.addView(createMenuItem(getString(R.string.nexus_feed_share_story), R.drawable.ic_share) {
            shareArticle(art)
            dismiss()
        })

        root.addView(createMenuItem(getString(R.string.nexus_feed_hide_story), R.drawable.ic_visibility_off) {
            onHideArticle?.invoke()
            dismiss()
        })

        outerRoot.addView(root)
        ViewCompat.setOnApplyWindowInsetsListener(outerRoot) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            (root.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = navBars.bottom + (8 * dp).toInt()
                root.layoutParams = lp
            }
            insets
        }

        return outerRoot
    }

    private fun createMenuItem(label: String, iconRes: Int, onClick: () -> Unit): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                onClick()
            }

            addView(ImageView(requireContext()).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                    marginEnd = (14 * dp).toInt()
                }
            })

            addView(TextView(requireContext()).apply {
                text = label
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
        }
    }

    private fun shareArticle(art: FeedArticle) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, art.title)
                putExtra(Intent.EXTRA_TEXT, "${art.title}\n\n${art.link}")
            }
            startActivity(Intent.createChooser(intent, getString(R.string.nexus_feed_share_via)))
        } catch (_: Exception) {}
    }

    companion object {
        const val TAG = "NexusFeedArticleMenuSheet"
    }
}
