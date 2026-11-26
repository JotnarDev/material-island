package dev.jotnar.materialisland.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Tipografía de las apps de Google: Google Sans para títulos y etiquetas, Google Sans Text para el texto.
 * No se incluye ninguna fuente en la app: se piden al sistema por nombre (los Pixel las traen). En otros
 * teléfonos no existen y se queda la fuente del sistema.
 */
object PixelFonts {
    private fun system(vararg names: String): FontFamily? = names.firstNotNullOfOrNull { n ->
        Typeface.create(n, Typeface.NORMAL).takeIf { it != Typeface.DEFAULT }?.let { FontFamily(it) }
    }

    val display: FontFamily? by lazy { system("google-sans-flex", "google-sans") }
    val sans: FontFamily? by lazy { system("google-sans") }
    val text: FontFamily? by lazy { system("google-sans-text", "google-sans") }
    val available get() = sans != null

    fun typography(): Typography {
        val t = Typography()
        if (!available) return t
        fun TextStyle.f(ff: FontFamily?) = if (ff != null) copy(fontFamily = ff) else this
        return t.copy(
            displayLarge = t.displayLarge.f(display), displayMedium = t.displayMedium.f(display), displaySmall = t.displaySmall.f(display),
            headlineLarge = t.headlineLarge.f(display), headlineMedium = t.headlineMedium.f(display), headlineSmall = t.headlineSmall.f(display),
            titleLarge = t.titleLarge.f(sans), titleMedium = t.titleMedium.f(sans), titleSmall = t.titleSmall.f(sans),
            bodyLarge = t.bodyLarge.f(text), bodyMedium = t.bodyMedium.f(text), bodySmall = t.bodySmall.f(text),
            labelLarge = t.labelLarge.f(sans), labelMedium = t.labelMedium.f(sans), labelSmall = t.labelSmall.f(sans),
        )
    }
}
