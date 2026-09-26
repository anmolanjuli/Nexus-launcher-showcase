package com.nexus.launcher.ui.dock.settings

enum class DockBackgroundMode {
    TRANSPARENT,
    FROSTED,
    SOLID,
    NEUMORPHIC;

    companion object {
        fun fromStored(value: String?): DockBackgroundMode = when (value) {
            FROSTED.name, "BLURRED" -> FROSTED
            SOLID.name -> SOLID
            NEUMORPHIC.name, "SOFT_UI" -> NEUMORPHIC
            else -> TRANSPARENT
        }
    }
}
