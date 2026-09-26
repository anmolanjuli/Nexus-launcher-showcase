package com.nexus.launcher.typography

import android.graphics.Typeface

/** Interface for resolving Typefaces by OpenType weight (400/700), mapping legacy style bits. */
interface FontProvider {
    fun getTypeface(weight: Int): Typeface
}
