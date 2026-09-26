package com.nexus.launcher.ui.settings

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.nexus.launcher.ui.icons.IconResolver
import com.nexus.launcher.ui.icons.IconShapeMasker

object IconEditPreviewHelper {

    fun effectivePreviewShape(pendingShapeId: Int): Int =
        if (pendingShapeId == IconShapeTileRow.FOLLOW_GLOBAL) {
            IconResolver.currentShape
        } else {
            pendingShapeId
        }

    fun shapedDrawable(raw: Drawable, shape: Int): Drawable {
        return if (shape == -1) raw
        else IconShapeMasker(raw, shape)
    }

    fun resolveCustomDrawable(
        context: Context,
        resources: Resources,
        custom: String,
        shape: Int
    ): Drawable? {
        if (custom.startsWith("/")) {
            val bitmap = BitmapFactory.decodeFile(custom) ?: return null
            val drawable = BitmapDrawable(resources, bitmap)
            return shapedDrawable(drawable, shape)
        }
        if (custom.contains("::")) {
            val parts = custom.split("::")
            if (parts.size == 2) {
                try {
                    val packContext = context.createPackageContext(parts[0], 0)
                    val resId = packContext.resources.getIdentifier(parts[1], "drawable", parts[0])
                    if (resId != 0) {
                        val packIcon = ResourcesCompat.getDrawable(packContext.resources, resId, null)
                        if (packIcon != null) {
                            return shapedDrawable(packIcon, shape)
                        }
                    }
                } catch (_: Exception) { }
            }
        }
        return null
    }
}
