package com.nexus.launcher.ui.folder

/** Easter-egg app counts — numeral system rotates per folder id. */
object FolderCountEasterEgg {

    fun format(count: Int, folderId: Int): String {
        if (count <= 0) return ""
        if (count > 99) return overflow(folderId)
        return when ((folderId % 4 + 4) % 4) {
            0 -> toRoman(count)
            1 -> Integer.toBinaryString(count)
            2 -> count.toString(16).uppercase()
            else -> toCircled(count)
        }
    }

    private fun overflow(folderId: Int): String = when ((folderId % 4 + 4) % 4) {
        0 -> "C+"
        1 -> "1111111"
        2 -> "FF+"
        else -> "⑨⑨"
    }

    private fun toRoman(n: Int): String {
        val pairs = arrayOf(
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var left = n
        val out = StringBuilder()
        for ((value, numeral) in pairs) {
            while (left >= value) {
                out.append(numeral)
                left -= value
            }
        }
        return out.toString()
    }

    private fun toCircled(n: Int): String {
        val base = '①'.code - 1
        return buildString {
            for (ch in n.toString()) {
                val digit = ch.digitToInt()
                append(Char(base + digit))
            }
        }
    }
}
