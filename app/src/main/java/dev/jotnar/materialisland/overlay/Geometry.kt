package dev.jotnar.materialisland.overlay

import android.content.Context
import android.graphics.RectF
import android.view.WindowManager

/**
 * Busca el agujero de la cámara frontal usando el camino exacto del recorte de pantalla
 * (en el Pixel 10 Pro es un círculo de 98 px centrado en x=640, y=102).
 */
fun computeIslandGeometry(ctx: Context): IslandGeometry {
    val wm = ctx.getSystemService(WindowManager::class.java)
    val metrics = wm.maximumWindowMetrics
    val bounds = metrics.bounds
    val density = ctx.resources.displayMetrics.density
    var cx = bounds.width() / 2f
    var cy = 14f * density
    var d = 28f * density
    val cutout = metrics.windowInsets.displayCutout
    if (cutout != null) {
        val r = RectF()
        val path = cutout.cutoutPath
        if (path != null && !path.isEmpty) path.computeBounds(r, true)
        else cutout.boundingRectTop.takeIf { !it.isEmpty }?.let { r.set(it) }
        if (!r.isEmpty && r.top < bounds.height() / 4f) {
            cx = r.centerX(); cy = r.centerY(); d = minOf(r.width(), r.height())
        }
    }
    return IslandGeometry(bounds.width() / density, cx / density, cy / density, d / density)
}
