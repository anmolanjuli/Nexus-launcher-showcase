package com.nexus.launcher.theme

data class NexusColorTokens(
    val bg: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val divider: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentMuted: Int,
    val danger: Int,
    val bgTop: Int? = null,
    val bgBottom: Int? = null
) {
    companion object {
        fun computeAccentMuted(accent: Int): Int {
            val alpha = (255 * 0.12f).toInt()
            return (accent and 0x00FFFFFF) or (alpha shl 24)
        }

        val Dark = NexusColorTokens(
            bg = 0xFF121212.toInt(),
            surface = 0xFF1E1E1E.toInt(),
            surfaceRaised = 0xFF2C2C2C.toInt(),
            divider = 0xFF2A2A2A.toInt(),
            textPrimary = 0xFFF0F2F5.toInt(),
            textSecondary = 0xFF8E95A0.toInt(),
            accent = 0xFF4F8DA6.toInt(),
            accentMuted = computeAccentMuted(0xFF4F8DA6.toInt()),
            danger = 0xFFE5484D.toInt()
        )

        val Amoled = NexusColorTokens(
            bg = 0xFF000000.toInt(),
            surface = 0xFF0E0E0E.toInt(),
            surfaceRaised = 0xFF1E1E1E.toInt(),
            divider = 0xFF1A1A1A.toInt(),
            textPrimary = 0xFFF0F2F5.toInt(),
            textSecondary = 0xFF8E95A0.toInt(),
            accent = 0xFF4F8DA6.toInt(),
            accentMuted = computeAccentMuted(0xFF4F8DA6.toInt()),
            danger = 0xFFE5484D.toInt()
        )

        val Light = NexusColorTokens(
            bg = 0xFFEAECEE.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            surfaceRaised = 0xFFE4E9EC.toInt(),
            divider = 0xFFE4E9EC.toInt(),
            textPrimary = 0xFF14181B.toInt(),
            textSecondary = 0xFF5B6770.toInt(),
            accent = 0xFF3E7C9A.toInt(),
            accentMuted = computeAccentMuted(0xFF3E7C9A.toInt()),
            danger = 0xFFC4372C.toInt()
        )

        // 1. Rich Sand (Warm, earthy, desert dusk)
        val CalmRichSand = NexusColorTokens(
            bg = 0xFF1D130B.toInt(),
            bgTop = 0xFF3D2B1F.toInt(),
            bgBottom = 0xFF1D130B.toInt(),
            surface = 0xFF4A372A.toInt(),
            surfaceRaised = 0xFF5C463A.toInt(),
            divider = 0xFF5A4335.toInt(),
            textPrimary = 0xFFF3E9DE.toInt(),
            textSecondary = 0xFFB49E88.toInt(),
            accent = 0xFFD9B48F.toInt(),
            accentMuted = 0x20D9B48F,
            danger = 0xFFE5484D.toInt()
        )

        // 2. Golden Sand (Warm, golden, sunlit)
        val CalmGoldenSand = NexusColorTokens(
            bg = 0xFF2A2411.toInt(),
            bgTop = 0xFF4E4422.toInt(),
            bgBottom = 0xFF2A2411.toInt(),
            surface = 0xFF5C5030.toInt(),
            surfaceRaised = 0xFF6E6040.toInt(),
            divider = 0xFF5C5030.toInt(),
            textPrimary = 0xFFF5EFD8.toInt(),
            textSecondary = 0xFFB5A97A.toInt(),
            accent = 0xFFEAD196.toInt(),
            accentMuted = 0x20EAD196,
            danger = 0xFFE5484D.toInt()
        )

        // 3. Ocean Blue (Original Calm Pilot)
        val CalmOcean = NexusColorTokens(
            bg = 0xFF0B1C28.toInt(),
            bgTop = 0xFF1C3B4A.toInt(),
            bgBottom = 0xFF0B1C28.toInt(),
            surface = 0xFF24515E.toInt(),
            surfaceRaised = 0xFF2F6B7A.toInt(),
            divider = 0xFF24515E.toInt(),
            textPrimary = 0xFFE1F0F8.toInt(),
            textSecondary = 0xFF7FA3B5.toInt(),
            accent = 0xFF8FD8F2.toInt(),
            accentMuted = 0x208FD8F2,
            danger = 0xFFE5484D.toInt()
        )

        // 4. Navy Ink (Bold, classic, midnight)
        val CalmNavyInk = NexusColorTokens(
            bg = 0xFF0C1224.toInt(),
            bgTop = 0xFF1A2B4A.toInt(),
            bgBottom = 0xFF0C1224.toInt(),
            surface = 0xFF24395F.toInt(),
            surfaceRaised = 0xFF314A7A.toInt(),
            divider = 0xFF24395F.toInt(),
            textPrimary = 0xFFE4EBF7.toInt(),
            textSecondary = 0xFF7A8DB0.toInt(),
            accent = 0xFF9BB8E3.toInt(),
            accentMuted = 0x209BB8E3,
            danger = 0xFFE5484D.toInt()
        )

        // 5. Deep Navy (Immersive, rich, abyssal)
        val CalmDeepNavy = NexusColorTokens(
            bg = 0xFF050913.toInt(),
            bgTop = 0xFF0F1F3D.toInt(),
            bgBottom = 0xFF050913.toInt(),
            surface = 0xFF16294D.toInt(),
            surfaceRaised = 0xFF1E3762.toInt(),
            divider = 0xFF16294D.toInt(),
            textPrimary = 0xFFDDE7F2.toInt(),
            textSecondary = 0xFF6B829E.toInt(),
            accent = 0xFF4F8DA6.toInt(),
            accentMuted = 0x204F8DA6,
            danger = 0xFFE5484D.toInt()
        )


        // 6. Ember Red (Deep, warm, glowing coals)
        val CalmEmberRed = NexusColorTokens(
            bg = 0xFF210C0F.toInt(),
            bgTop = 0xFF4A2024.toInt(),
            bgBottom = 0xFF210C0F.toInt(),
            surface = 0xFF5E2C31.toInt(),
            surfaceRaised = 0xFF743A40.toInt(),
            divider = 0xFF5E2C31.toInt(),
            textPrimary = 0xFFF9E7E8.toInt(),
            textSecondary = 0xFFBE9491.toInt(),
            accent = 0xFFF2938F.toInt(),
            accentMuted = 0x20F2938F,
            danger = 0xFFE5484D.toInt()
        )

        // 7. Forest Green (Sheltered, mossy, low light)
        val CalmForestGreen = NexusColorTokens(
            bg = 0xFF0B1D14.toInt(),
            bgTop = 0xFF1F3D2E.toInt(),
            bgBottom = 0xFF0B1D14.toInt(),
            surface = 0xFF285143.toInt(),
            surfaceRaised = 0xFF356A56.toInt(),
            divider = 0xFF285143.toInt(),
            textPrimary = 0xFFE3F3EA.toInt(),
            textSecondary = 0xFF8CB2A0.toInt(),
            accent = 0xFF8FE0B4.toInt(),
            accentMuted = 0x208FE0B4,
            danger = 0xFFE5484D.toInt()
        )

        // 8. Amber Dusk (Yellow — brighter and more saturated than Golden Sand's olive)
        val CalmAmberDusk = NexusColorTokens(
            bg = 0xFF261E05.toInt(),
            bgTop = 0xFF524211.toInt(),
            bgBottom = 0xFF261E05.toInt(),
            surface = 0xFF6B561A.toInt(),
            surfaceRaised = 0xFF876D24.toInt(),
            divider = 0xFF6B561A.toInt(),
            textPrimary = 0xFFFBF2DC.toInt(),
            textSecondary = 0xFFC7AD79.toInt(),
            accent = 0xFFFFD470.toInt(),
            accentMuted = 0x20FFD470,
            danger = 0xFFE5484D.toInt()
        )

        // 9. Burnt Orange (Kiln-fired, earthy, autumnal)
        val CalmBurntOrange = NexusColorTokens(
            bg = 0xFF231206.toInt(),
            bgTop = 0xFF4C2A15.toInt(),
            bgBottom = 0xFF231206.toInt(),
            surface = 0xFF63371B.toInt(),
            surfaceRaised = 0xFF7E4824.toInt(),
            divider = 0xFF63371B.toInt(),
            textPrimary = 0xFFFAEADF.toInt(),
            textSecondary = 0xFFC69B7E.toInt(),
            accent = 0xFFFFA96B.toInt(),
            accentMuted = 0x20FFA96B,
            danger = 0xFFE5484D.toInt()
        )

        // 10. Deep Plum (Dusk violet — the cool counterpart to Ember Red)
        val CalmDeepPlum = NexusColorTokens(
            bg = 0xFF160D1D.toInt(),
            bgTop = 0xFF33213F.toInt(),
            bgBottom = 0xFF160D1D.toInt(),
            surface = 0xFF432C52.toInt(),
            surfaceRaised = 0xFF573A69.toInt(),
            divider = 0xFF432C52.toInt(),
            textPrimary = 0xFFF0E6F7.toInt(),
            textSecondary = 0xFFAE95BE.toInt(),
            accent = 0xFFCFA6EA.toInt(),
            accentMuted = 0x20CFA6EA,
            danger = 0xFFE5484D.toInt()
        )

        // 11. Slate Grey (Hueless — the quietest option, for wallpapers that carry the colour)
        val CalmSlateGrey = NexusColorTokens(
            bg = 0xFF12151A.toInt(),
            bgTop = 0xFF2B3036.toInt(),
            bgBottom = 0xFF12151A.toInt(),
            surface = 0xFF373E46.toInt(),
            surfaceRaised = 0xFF474F59.toInt(),
            divider = 0xFF373E46.toInt(),
            textPrimary = 0xFFEAEEF2.toInt(),
            textSecondary = 0xFF9AA6B2.toInt(),
            accent = 0xFFAFC3D6.toInt(),
            accentMuted = 0x20AFC3D6,
            danger = 0xFFE5484D.toInt()
        )

        fun getCalmTokens(palette: CalmPalette): NexusColorTokens = when (palette) {
            CalmPalette.RICH_SAND -> CalmRichSand
            CalmPalette.GOLDEN_SAND -> CalmGoldenSand
            CalmPalette.OCEAN_BLUE -> CalmOcean
            CalmPalette.NAVY_INK -> CalmNavyInk
            CalmPalette.DEEP_NAVY -> CalmDeepNavy
            CalmPalette.EMBER_RED -> CalmEmberRed
            CalmPalette.FOREST_GREEN -> CalmForestGreen
            CalmPalette.AMBER_DUSK -> CalmAmberDusk
            CalmPalette.BURNT_ORANGE -> CalmBurntOrange
            CalmPalette.DEEP_PLUM -> CalmDeepPlum
            CalmPalette.SLATE_GREY -> CalmSlateGrey
        }
    }
}
