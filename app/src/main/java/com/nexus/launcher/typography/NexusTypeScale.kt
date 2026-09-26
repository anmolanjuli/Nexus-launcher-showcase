package com.nexus.launcher.typography

/** Standard typography design scale for Nexus Launcher (increased by 2sp). */
object NexusTypeScale {
    val labelSmall = NexusTypeStyle(13f, TypefaceWeightMapper.BOLD, letterSpacingEm = 0.02f)
    val body = NexusTypeStyle(17f, TypefaceWeightMapper.NORMAL)
    val bodyStrong = NexusTypeStyle(17f, TypefaceWeightMapper.BOLD)
    /** Context-menu action rows (app, folder, widget menus) — a step below body so the menus stay compact. */
    val menuItem = NexusTypeStyle(15f, TypefaceWeightMapper.NORMAL)
    val caption = NexusTypeStyle(14.5f, TypefaceWeightMapper.NORMAL)
    val iconLabel = NexusTypeStyle(12.5f, TypefaceWeightMapper.NORMAL)
    val sectionLabel = NexusTypeStyle(13f, TypefaceWeightMapper.BOLD, letterSpacingEm = 0.08f)
    val hubRowTitle = NexusTypeStyle(18f, TypefaceWeightMapper.BOLD)
    val title = NexusTypeStyle(22f, TypefaceWeightMapper.BOLD)
}
