package dev.jotnar.materialisland.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import dev.jotnar.materialisland.model.TripInfo
import kotlin.math.PI
import kotlin.math.sin

// Ilustraciones propias de la tarjeta de viajes y pedidos (Canvas, sin imágenes de terceros).

private val Kraft = Color(0xFFD9B48C)
private val KraftDark = Color(0xFFB38B62)
private val KraftLight = Color(0xFFEBCFAE)
private val Tire = Color(0xFF2B2930)
private val Hub = Color(0xFFBDB9C4)
private val Glass = Color(0xFF9FC3E6)
private val Headlight = Color(0xFFFFE08A)

/** Coche de lado: carrocería del color de acento, ruedas que giran y líneas de velocidad detrás. */
private fun DrawScope.car(s: Float, accent: Color, spin: Float, wave: Float, moving: Boolean) {
    val bob = if (moving) sin(wave * 2 * PI).toFloat() * 0.012f * s else 0f
    if (moving) for (i in 0..2) {
        val k = (wave + i / 3f) % 1f
        val y = (0.44f + i * 0.08f) * s
        drawLine(
            accent.copy(alpha = (1f - k) * 0.55f), Offset((0.02f + 0.08f * k) * s, y), Offset((0.14f + 0.08f * k) * s, y),
            strokeWidth = 0.03f * s, cap = StrokeCap.Round,
        )
    }
    translate(top = bob) {
        // Techo
        val roof = Path().apply {
            moveTo(0.33f * s, 0.46f * s); lineTo(0.42f * s, 0.27f * s); lineTo(0.68f * s, 0.27f * s)
            lineTo(0.8f * s, 0.46f * s); close()
        }
        drawPath(roof, lerp(accent, Color.Black, 0.18f))
        // Ventanas
        val w1 = Path().apply { moveTo(0.38f * s, 0.45f * s); lineTo(0.445f * s, 0.31f * s); lineTo(0.54f * s, 0.31f * s); lineTo(0.54f * s, 0.45f * s); close() }
        val w2 = Path().apply { moveTo(0.575f * s, 0.45f * s); lineTo(0.575f * s, 0.31f * s); lineTo(0.66f * s, 0.31f * s); lineTo(0.745f * s, 0.45f * s); close() }
        drawPath(w1, Glass); drawPath(w2, Glass)
        // Carrocería
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.15f), accent, lerp(accent, Color.Black, 0.2f)), startY = 0.44f * s, endY = 0.7f * s),
            topLeft = Offset(0.2f * s, 0.44f * s), size = Size(0.72f * s, 0.24f * s), cornerRadius = CornerRadius(0.08f * s),
        )
        drawCircle(Headlight, radius = 0.028f * s, center = Offset(0.89f * s, 0.52f * s))
        drawRoundRect(Color.White.copy(alpha = 0.5f), topLeft = Offset(0.56f * s, 0.5f * s), size = Size(0.06f * s, 0.016f * s), cornerRadius = CornerRadius(0.01f * s))
    }
    // Ruedas
    for (cx in listOf(0.36f, 0.76f)) {
        val c = Offset(cx * s, 0.69f * s)
        drawCircle(Tire, radius = 0.1f * s, center = c)
        drawCircle(Hub, radius = 0.05f * s, center = c)
        rotate(spin * 360f, pivot = c) {
            for (a in 0..2) rotate(a * 120f, pivot = c) {
                drawLine(Tire.copy(alpha = 0.8f), c, Offset(c.x, c.y - 0.045f * s), strokeWidth = 0.014f * s, cap = StrokeCap.Round)
            }
        }
    }
    drawLine(Color.White.copy(alpha = 0.15f), Offset(0.12f * s, 0.8f * s), Offset(0.95f * s, 0.8f * s), strokeWidth = 0.012f * s, cap = StrokeCap.Round)
}

/** Bolsa de papel de comida para llevar, con asas, sello de color y vapor que sube. */
private fun DrawScope.foodBag(s: Float, accent: Color, wave: Float) {
    // Vapor
    for (i in 0..2) {
        val k = (wave + i / 3f) % 1f
        val x = (0.38f + i * 0.12f) * s
        val path = Path().apply {
            moveTo(x, 0.26f * s - k * 0.12f * s)
            cubicTo(x - 0.04f * s, 0.2f * s - k * 0.12f * s, x + 0.04f * s, 0.14f * s - k * 0.12f * s, x, 0.08f * s - k * 0.12f * s)
        }
        drawPath(path, Color.White.copy(alpha = (1f - k) * 0.55f * (if (k < 0.15f) k / 0.15f else 1f)), style = Stroke(0.03f * s, cap = StrokeCap.Round))
    }
    // Asas
    drawArc(KraftDark, 180f, 180f, false, topLeft = Offset(0.37f * s, 0.24f * s), size = Size(0.26f * s, 0.2f * s), style = Stroke(0.035f * s))
    // Cuerpo (ligeramente trapezoidal)
    val body = Path().apply {
        moveTo(0.24f * s, 0.34f * s); lineTo(0.76f * s, 0.34f * s); lineTo(0.8f * s, 0.88f * s); lineTo(0.2f * s, 0.88f * s); close()
    }
    drawPath(body, Brush.verticalGradient(listOf(KraftLight, Kraft, KraftDark), startY = 0.34f * s, endY = 0.88f * s))
    // Pliegue superior
    drawRect(KraftDark.copy(alpha = 0.6f), topLeft = Offset(0.24f * s, 0.34f * s), size = Size(0.52f * s, 0.05f * s))
    // Sello
    drawCircle(accent, radius = 0.1f * s, center = Offset(0.5f * s, 0.6f * s))
    drawArc(Color.White.copy(alpha = 0.9f), 20f, 140f, false, topLeft = Offset(0.445f * s, 0.55f * s), size = Size(0.11f * s, 0.09f * s), style = Stroke(0.022f * s, cap = StrokeCap.Round))
}

/** Caja de cartón en perspectiva con cinta del color de acento, que da un pequeño salto. */
private fun DrawScope.parcel(s: Float, accent: Color, wave: Float) {
    val hop = -kotlin.math.abs(sin(wave * PI).toFloat()) * 0.05f * s
    // Sombra
    drawOval(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0.24f * s, 0.86f * s), size = Size(0.56f * s, 0.06f * s))
    translate(top = hop) {
        val front = Path().apply { moveTo(0.18f * s, 0.42f * s); lineTo(0.56f * s, 0.5f * s); lineTo(0.56f * s, 0.88f * s); lineTo(0.18f * s, 0.8f * s); close() }
        val side = Path().apply { moveTo(0.56f * s, 0.5f * s); lineTo(0.84f * s, 0.4f * s); lineTo(0.84f * s, 0.78f * s); lineTo(0.56f * s, 0.88f * s); close() }
        val top = Path().apply { moveTo(0.18f * s, 0.42f * s); lineTo(0.46f * s, 0.32f * s); lineTo(0.84f * s, 0.4f * s); lineTo(0.56f * s, 0.5f * s); close() }
        drawPath(front, Kraft); drawPath(side, KraftDark); drawPath(top, KraftLight)
        // Cinta sobre la tapa y bajando por el frente
        val tapeTop = Path().apply { moveTo(0.3f * s, 0.38f * s); lineTo(0.37f * s, 0.355f * s); lineTo(0.75f * s, 0.435f * s); lineTo(0.68f * s, 0.46f * s); close() }
        drawPath(tapeTop, accent)
        val tapeFront = Path().apply { moveTo(0.33f * s, 0.452f * s); lineTo(0.41f * s, 0.469f * s); lineTo(0.41f * s, 0.85f * s); lineTo(0.33f * s, 0.833f * s); close() }
        drawPath(tapeFront, lerp(accent, Color.Black, 0.12f))
        // Etiqueta
        drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(0.63f * s, 0.58f * s), size = Size(0.14f * s, 0.09f * s))
    }
}

/** Ilustración animada del viaje o pedido: coche, bolsa de comida o caja. */
@Composable
internal fun TripArt(type: TripInfo.Type, stage: Int, size: Dp, accent: Color) {
    val t = rememberInfiniteTransition(label = "tripArt")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    val spin by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val moving = stage in 1..2
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        when (type) {
            TripInfo.Type.RIDE -> car(s, accent, if (moving) spin else 0f, wave, moving)
            TripInfo.Type.FOOD -> foodBag(s, accent, wave)
            TripInfo.Type.PACKAGE -> parcel(s, accent, if (moving) wave else 0f)
        }
    }
}
