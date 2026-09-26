package com.nexus.launcher.ui.widgets.music

/**
 * Authentic era-specific color palettes for the four retro music widget styles.
 * Retro styles ignore the global UI Mode and theme color tokens.
 */
object RetroMusicPalette {

    data class WinampColors(
        val bg: Int,
        val bevelLight: Int,
        val bevelMid: Int,
        val bevelDark: Int,
        val bevelShadow: Int,
        val screenBg: Int,
        val screenText: Int,
        val screenTextDim: Int,
        val btnBg: Int,
        val btnIcon: Int
    )

    data class AmplifierColors(
        val faceplateTop: Int,
        val faceplateBottom: Int,
        val displayBg: Int,
        val amberText: Int,
        val amberDim: Int,
        val amberGlow: Int,
        val vuMeterBg: Int,
        val vuNeedle: Int,
        val chromeRing: Int,
        val chromeFace: Int,
        val screwHead: Int
    )

    data class TerminalColors(
        val bg: Int,
        val phosphorGreen: Int,
        val phosphorDim: Int,
        val borderGreen: Int,
        val textOffWhite: Int
    )

    data class CassetteColors(
        val labelBg: Int,
        val labelBorder: Int,
        val stripeRed: Int,
        val stripeBlue: Int,
        val spoolWellBg: Int,
        val spoolTeeth: Int,
        val spoolHub: Int,
        val counterBg: Int,
        val counterText: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val chromeBtn: Int
    )

    fun resolveWinamp(): WinampColors {
        return WinampColors(
            bg = 0xFFC0C0C0.toInt(),
            bevelLight = 0xFFFFFFFF.toInt(),
            bevelMid = 0xFFDFDFDF.toInt(),
            bevelDark = 0xFF808080.toInt(),
            bevelShadow = 0xFF404040.toInt(),
            screenBg = 0xFF000000.toInt(),
            screenText = 0xFF00FF00.toInt(),
            screenTextDim = 0xFF007A00.toInt(),
            btnBg = 0xFFB4B4B4.toInt(),
            btnIcon = 0xFF181818.toInt()
        )
    }

    fun resolveAmplifier(): AmplifierColors {
        return AmplifierColors(
            faceplateTop = 0xFF4A4A4A.toInt(),
            faceplateBottom = 0xFF2E2E2E.toInt(),
            displayBg = 0xFF141008.toInt(),
            amberText = 0xFFFFB000.toInt(),
            amberDim = 0xFF8A5E00.toInt(),
            amberGlow = 0x35FFB000.toInt(),
            vuMeterBg = 0xFF221A0C.toInt(),
            vuNeedle = 0xFFFF3B00.toInt(),
            chromeRing = 0xFFD4D4D4.toInt(),
            chromeFace = 0xFF383838.toInt(),
            screwHead = 0xFF8E8E8E.toInt()
        )
    }

    fun resolveTerminal(): TerminalColors {
        return TerminalColors(
            bg = 0xFF000000.toInt(),
            phosphorGreen = 0xFF5FBF5F.toInt(),
            phosphorDim = 0xFF2E632E.toInt(),
            borderGreen = 0xFF3A783A.toInt(),
            textOffWhite = 0xE6F0F2F5.toInt()
        )
    }

    fun resolveCassette(): CassetteColors {
        return CassetteColors(
            labelBg = 0xFFF0E6D2.toInt(),
            labelBorder = 0xFF706050.toInt(),
            stripeRed = 0xFFC62828.toInt(),
            stripeBlue = 0xFF1565C0.toInt(),
            spoolWellBg = 0xFF24201C.toInt(),
            spoolTeeth = 0xFFF5F0E6.toInt(),
            spoolHub = 0xFFA09688.toInt(),
            counterBg = 0xFF181818.toInt(),
            counterText = 0xFFFFFFFF.toInt(),
            textPrimary = 0xFF2A2018.toInt(),
            textSecondary = 0xFF6A5C50.toInt(),
            chromeBtn = 0xFFD8D8D8.toInt()
        )
    }
}
