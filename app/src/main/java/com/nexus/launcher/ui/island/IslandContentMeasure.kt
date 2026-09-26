package com.nexus.launcher.ui.island

/**
 * How tall the opened island needs to be for what it is about to draw.
 *
 * The card used to be a fixed 108–128dp whatever was in it, which is why a widget row came out
 * cramped and a call card had space going spare. Each page states the height its own drawing
 * needs, and the card takes the largest it could need for the page on screen.
 *
 * The numbers below mirror the drawing code: change a row height there, change it here.
 */
object IslandContentMeasure {

    /** Card padding, top and bottom, plus the page dots along the bottom edge. */
    private const val CARD_PAD_DP = 10f
    private const val PAGE_DOTS_DP = 16f

    /** Album art and the two lines beside it ([IslandContentDraw]). */
    private const val MEDIA_ROW_DP = 64f

    /** The transport row under it: play, previous, next. */
    private const val TRANSPORT_ROW_DP = 40f

    /** Caller line plus the answer and decline buttons ([IslandCallDraw]). */
    private const val CALL_BODY_DP = 84f

    /** One line of title and subtitle for everything else. */
    private const val SIMPLE_BODY_DP = 52f

    /** A row of glance tiles ([IslandGlanceDraw]). */
    private const val GLANCE_ROW_DP = 56f

    /** A live activity: two lines, its progress bar, and the app's own buttons. */
    private const val ACTIVITY_BODY_DP = 54f
    private const val ACTIVITY_BAR_DP = 14f
    private const val ACTIVITY_ACTIONS_DP = 34f

    fun heightDp(page: IslandPage, payload: IslandPayload?, pageCount: Int): Float {
        val body = when (page) {
            IslandPage.ACTIVITY -> activityBodyDp(payload)
            IslandPage.GLANCE -> GLANCE_ROW_DP
        }
        val dots = if (pageCount > 1) PAGE_DOTS_DP else 0f
        return CARD_PAD_DP * 2f + body + dots
    }

    private fun activityBodyDp(payload: IslandPayload?): Float {
        payload ?: return SIMPLE_BODY_DP
        // A live activity is as tall as it has things to say. A timer with the clock app's own
        // buttons is drawn the same way, so it is measured the same way.
        if (payload.usesActivityCard) {
            return ACTIVITY_BODY_DP +
                (if (payload.progress >= 0f || payload.busy) ACTIVITY_BAR_DP else 0f) +
                (if (payload.actions.isNotEmpty()) ACTIVITY_ACTIONS_DP else 0f)
        }
        return when (payload.kind) {
            IslandKind.CALL -> CALL_BODY_DP
            IslandKind.MUSIC -> MEDIA_ROW_DP + TRANSPORT_ROW_DP
            IslandKind.TIMER, IslandKind.STOPWATCH -> MEDIA_ROW_DP
            else -> SIMPLE_BODY_DP
        }
    }
}
