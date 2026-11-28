package dev.jotnar.materialisland.i18n

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * Traducción de la interfaz. El código está escrito en inglés; si el idioma del teléfono (o el idioma de la
 * app elegido en Ajustes de Android) es español, cada texto que se dibuja pasa por [tr]:
 *  1. coincidencia exacta en [Es.exact];
 *  2. si no, plantillas con huecos `{}` en [Es.templates] (los huecos también se traducen).
 * Lo que no está en el diccionario (nombres de apps, títulos de notificaciones...) se deja igual.
 */
object Tr {
    @Volatile private var lang: String = Locale.getDefault().language
    val spanish: Boolean get() = lang == "es"

    /** Lee el idioma de la configuración de la app (incluye el idioma elegido solo para esta app). */
    fun update(config: android.content.res.Configuration) {
        lang = config.locales.get(0)?.language ?: Locale.getDefault().language
    }

    private val templates: List<Pair<Regex, String>> by lazy {
        Es.templates.map { (en, es) ->
            val parts = en.split("{}")
            Regex("^" + parts.joinToString("(.+?)") { Regex.escape(it) } + "$") to es
        }
    }

    fun tr(s: String): String {
        if (!spanish || s.isEmpty()) return s
        Es.exact[s]?.let { return it }
        for ((re, es) in templates) {
            val m = re.matchEntire(s) ?: continue
            var out = es
            for (g in m.groupValues.drop(1)) out = out.replaceFirst("{}", tr(g))
            return out
        }
        return s
    }
}

fun tr(s: String) = Tr.tr(s)

/** Igual que el Text de Material 3, pero traduce el texto (ver [Tr]). Se importa en lugar del original. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: androidx.compose.foundation.text.TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = tr(text), modifier = modifier, color = color, autoSize = autoSize, fontSize = fontSize, fontStyle = fontStyle,
    fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
    textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
    minLines = minLines, onTextLayout = onTextLayout, style = style,
)

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = LocalTextStyle.current,
 ): Unit = androidx.compose.material3.Text(text, modifier, color, maxLines = maxLines, overflow = overflow, style = style)
