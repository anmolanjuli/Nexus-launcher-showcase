package com.nexus.launcher.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/**
 * Open-source and font licenses.
 *
 * The SIL Open Font License requires its text to travel with the font. The OFL files were already
 * bundled under `assets/licenses/fonts/`, but nothing in the app ever displayed them — so the
 * obligation was unmet in every build. This page reads them straight from assets, which means the
 * list cannot drift from what is actually shipped: add a font and its license file and it appears
 * here on its own.
 */
class LicensesFragment : Fragment() {

    private lateinit var content: LinearLayout
    private var isFontsExpanded = true
    private var isLibrariesExpanded = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val dp = resources.displayMetrics.density
        val padH = (10 * dp).toInt()
        val tokens = runCatching { ThemeObserver.currentTokens(requireContext()) }
            .getOrDefault(NexusColorTokens.Dark)

        val scrollView = ScrollView(requireContext()).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(padH, 0, padH, (80 * dp).toInt())
        }

        val mainSection = NexusSection(requireContext()).apply { isTransparentCard = true }

        val fontsGroup = SettingsSectionGroupView(requireContext()).apply {
            fontRows(tokens).forEach { addChildRow(it) }
        }
        mainSection.addRow(
            sectionContainer(
                sectionHeader(
                    R.string.licenses_section_fonts, R.string.licenses_section_fonts_subtitle,
                    R.drawable.ic_fonts, fontsGroup, tokens,
                    { isFontsExpanded }, { isFontsExpanded = it },
                ),
                fontsGroup,
            ),
        )

        val librariesGroup = SettingsSectionGroupView(requireContext()).apply {
            libraryRows(tokens).forEach { addChildRow(it) }
        }
        mainSection.addRow(
            sectionContainer(
                sectionHeader(
                    R.string.licenses_section_libraries, R.string.licenses_section_libraries_subtitle,
                    R.drawable.ic_category, librariesGroup, tokens,
                    { isLibrariesExpanded }, { isLibrariesExpanded = it },
                ),
                librariesGroup,
            ),
        )
        content.addView(mainSection)

        scrollView.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(padH, (4 * dp).toInt(), padH, bars.bottom + (80 * dp).toInt())
            insets
        }
        return scrollView
    }

    /** Driven by what is actually in `assets/licenses/fonts/`, not a hand-maintained list. */
    private fun fontRows(tokens: NexusColorTokens): List<View> {
        val assets = requireContext().assets
        val files = runCatching { assets.list(FONT_LICENSE_DIR)?.sorted().orEmpty() }
            .getOrDefault(emptyList())
        return files.map { file ->
            NexusNavRow(
                requireContext(),
                title = prettyFontName(file),
                subtitle = getString(R.string.licenses_ofl),
                iconRes = R.drawable.ic_fonts,
                showChevron = true,
            ) {
                showLicense(prettyFontName(file), readAsset("$FONT_LICENSE_DIR/$file"))
            }.apply {
                setLandingTitle()
                applyTokens(tokens)
            }
        }
    }

    /**
     * The app's third-party dependencies. Unlike the fonts these have no bundled text to read, so
     * the list is maintained here — keep it in step with `app/build.gradle.kts`.
     */
    private fun libraryRows(tokens: NexusColorTokens): List<View> = LIBRARIES.map { (name, license) ->
        NexusNavRow(
            requireContext(),
            title = name,
            subtitle = license,
            iconRes = R.drawable.ic_info,
            showChevron = false,
        ) {}.apply {
            setLandingTitle()
            applyTokens(tokens)
        }
    }

    private fun readAsset(path: String): String = runCatching {
        requireContext().assets.open(path).bufferedReader().use { it.readText() }
    }.getOrElse { getString(R.string.licenses_unavailable) }

    private fun showLicense(title: String, body: String) {
        FolderAuroraDialogs.showConfirmation(
            context = requireContext(),
            title = title,
            body = body,
            cancelText = getString(R.string.action_close),
            confirmText = getString(R.string.action_close),
            onConfirm = {},
        )
    }

    /** "IBM_PLEX_SANS_OFL.txt" -> "IBM Plex Sans". */
    private fun prettyFontName(fileName: String): String = fileName
        .removeSuffix(".txt")
        .removeSuffix("_OFL")
        .split('_')
        .joinToString(" ") { part ->
            if (part.length <= 3 && part.all { it.isUpperCase() }) part
            else part.lowercase().replaceFirstChar { it.uppercase() }
        }

    private companion object {
        const val FONT_LICENSE_DIR = "licenses/fonts"

        val LIBRARIES = listOf(
            "AndroidX (Core, AppCompat, Activity, Fragment, Lifecycle)" to "Apache License 2.0",
            "AndroidX Compose" to "Apache License 2.0",
            "AndroidX Room" to "Apache License 2.0",
            "AndroidX DataStore" to "Apache License 2.0",
            "AndroidX DynamicAnimation" to "Apache License 2.0",
            "AndroidX DocumentFile" to "Apache License 2.0",
            "Material Components for Android" to "Apache License 2.0",
            "Material Symbols (icons)" to "Apache License 2.0",
            "Dagger Hilt" to "Apache License 2.0",
            "Gson" to "Apache License 2.0",
            "Kotlin Standard Library & Coroutines" to "Apache License 2.0",
        )
    }

    /**
     * Section header in the shape every other settings page uses: a [NexusNavRow] with an icon and
     * a chevron that collapses the group beneath it. These pages were written with
     * `NexusSection.setTitle`, a small-caps caption used nowhere else in the hub, which made them
     * read as a different kind of page.
     */
    private fun sectionHeader(
        titleRes: Int,
        subtitleRes: Int,
        iconRes: Int,
        group: SettingsSectionGroupView,
        tokens: NexusColorTokens,
        expanded: () -> Boolean,
        setExpanded: (Boolean) -> Unit,
    ): NexusNavRow {
        val dp = resources.displayMetrics.density
        lateinit var header: NexusNavRow
        header = NexusNavRow(
            requireContext(),
            title = getString(titleRes),
            subtitle = getString(subtitleRes),
            iconRes = iconRes,
            showChevron = true,
        ) {
            val next = !expanded()
            setExpanded(next)
            group.visibility = if (next) View.VISIBLE else View.GONE
            header.setChevronRotation(if (next) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
            applyTokens(tokens)
        }
        return header
    }

    private fun sectionContainer(header: View, group: View): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(group)
        }
}
