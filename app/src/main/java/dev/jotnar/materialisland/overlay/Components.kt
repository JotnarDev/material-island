package dev.jotnar.materialisland.overlay

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ── Formas Material Expressive ─────────────────────────────────────────

/** Forma que interpola entre dos formas de Material (p. ej. galleta ↔ cuadrado) y además puede girar. */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = morph.toPath(progress, android.graphics.Path())
        val m = Matrix()
        // Las formas de MaterialShapes vienen normalizadas a un cuadrado unidad.
        m.setRectToRect(RectF(0f, 0f, 1f, 1f), RectF(0f, 0f, size.width, size.height), Matrix.ScaleToFit.FILL)
        if (rotation != 0f) m.postRotate(rotation, size.width / 2f, size.height / 2f)
        p.transform(m)
        return Outline.Generic(p.asComposePath())
    }
}

private val artMorph by lazy { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Square) }
private val iconMorph by lazy { Morph(MaterialShapes.Cookie7Sided, MaterialShapes.Circle) }

/**
 * Carátula al estilo del reproductor del Pixel: galleta de 9 lados que gira despacio mientras suena
 * y se transforma en cuadrado redondeado al pausar.
 */
@Composable
fun MorphingArt(art: Bitmap?, playing: Boolean, size: Dp, enabled: Boolean, fallback: Color, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (playing && enabled) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 120f), label = "artMorph",
    )
    // La transición infinita solo existe mientras suena: en pausa no se redibuja nada (batería).
    val rotation = if (enabled && playing) {
        val t = rememberInfiniteTransition(label = "artSpin")
        val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
        r
    } else 0f
    val shape = remember(progress, rotation) { MorphShape(artMorph, progress, rotation) }
    Box(modifier.size(size).clip(if (enabled) shape else androidx.compose.foundation.shape.RoundedCornerShape(size / 4)).background(fallback)) {
        if (art != null) {
            Image(art.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            androidx.compose.material3.Icon(
                Icons.Rounded.MusicNote, null, tint = Color.White,
                modifier = Modifier.size(size * 0.5f).align(Alignment.Center),
            )
        }
    }
}

/** Icono dentro de una forma "galleta" que respira suavemente (para llamadas entrantes, grabación...). */
@Composable
fun PulsingShapeIcon(icon: ImageVector, container: Color, tint: Color, size: Dp, pulsing: Boolean) {
    var p = 1f
    var r = 0f
    if (pulsing) {
        val t = rememberInfiniteTransition(label = "pulse")
        p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p").value
        r = t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r").value
    }
    val shape = remember(p, r) { MorphShape(iconMorph, p, r) }
    Box(Modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

// ── Barra ondulada (seek bar del reproductor de Android) ─────────────────

@Composable
fun WavyProgress(
    progress: Float,
    animate: Boolean,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    // La versión más reciente: al cambiar de canción, la búsqueda usa la duración nueva.
    val seek by androidx.compose.runtime.rememberUpdatedState(onSeek)
    val amp by animateFloatAsState(if (animate && !dragging) 1f else 0f, spring(stiffness = 200f), label = "amp")
    val phase = if (animate) {
        val t = rememberInfiniteTransition(label = "wave")
        t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase").value
    } else 0f
    val shown = if (dragging) dragFrac else progress.coerceIn(0f, 1f)

    val gestures = if (onSeek != null) Modifier
        .pointerInput(Unit) {
            detectTapGestures { o -> seek?.invoke((o.x / size.width).coerceIn(0f, 1f)) }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { dragging = false; seek?.invoke(dragFrac) },
                onDragCancel = { dragging = false },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                },
            )
        } else Modifier

    Canvas(modifier.fillMaxWidth().height(28.dp).then(gestures)) {
        val stroke = 4.dp.toPx()
        val cy = size.height / 2
        val x = shown * size.width
        val wl = 26.dp.toPx()
        val a = 3.2.dp.toPx() * amp
        val gap = 6.dp.toPx()
        // Pista restante (recta)
        if (x + gap < size.width) {
            drawLine(track, Offset(x + gap, cy), Offset(size.width - stroke / 2, cy), stroke, StrokeCap.Round)
        }
        // Parte reproducida (ondulada)
        if (x > stroke) {
            val path = Path()
            var px = stroke / 2
            path.moveTo(px, cy + a * sin(2 * PI * px / wl - phase).toFloat())
            while (px < x) {
                px = minOf(px + 2f, x)
                // La onda se aplana cerca del pulgar para que no "salte".
                val fade = ((x - px) / (wl / 2)).coerceIn(0f, 1f)
                path.lineTo(px, cy + a * fade * sin(2 * PI * px / wl - phase).toFloat())
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // Pulgar vertical estilo Material 3 Expressive
        val tw = 4.dp.toPx()
        val th = if (dragging) 22.dp.toPx() else 16.dp.toPx()
        drawRoundRect(color, Offset(x - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw / 2))
    }
}

// ── Ecualizador animado ───────────────────────────────────────────────

@Composable
fun Visualizer(playing: Boolean, color: Color, modifier: Modifier = Modifier, bars: Int = 4, height: Dp = 16.dp) {
    val durations = listOf(420, 560, 380, 640, 500)
    val values: List<Float> = if (playing) {
        val t = rememberInfiniteTransition(label = "viz")
        (0 until bars).map { i ->
            t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(durations[i % durations.size], easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$i",
            ).value
        }
    } else List(bars) { 0.22f }
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        values.forEach { v ->
            val f = v
            Box(Modifier.width(3.dp).fillMaxHeight(f.coerceIn(0.2f, 1f)).clip(CircleShape).background(color))
        }
    }
}

// ── Tiempo ────────────────────────────────────────────────────────────

@Composable
fun rememberNow(intervalMs: Long = 1000L, active: Boolean = true): Long {
    val now by produceState(System.currentTimeMillis(), intervalMs, active) {
        while (active) {
            value = System.currentTimeMillis()
            delay(intervalMs - (value % intervalMs).coerceAtMost(intervalMs - 1))
        }
    }
    return now
}

fun formatDuration(ms: Long): String {
    val total = abs(ms) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val sign = if (ms < 0) "-" else ""
    return if (h > 0) "%s%d:%02d:%02d".format(sign, h, m, s) else "%s%d:%02d".format(sign, m, s)
}

/** Texto del cronómetro de una actividad, o null si no tiene. */
fun chronoText(item: IslandItem, now: Long): String? {
    if (item.chronoBase <= 0L) return null
    return if (item.countDown) formatDuration((item.chronoBase - now).coerceAtLeast(0))
    else formatDuration(now - item.chronoBase)
}

// ── Iconos por tipo ───────────────────────────────────────────────────

fun kindIcon(item: IslandItem): ImageVector = when (item.kind) {
    Kind.CALL -> Icons.Rounded.Call
    Kind.NAVIGATION -> Icons.Rounded.Navigation
    Kind.TRIP -> dev.jotnar.materialisland.overlay.tripIcon(item.trip)
    Kind.TIMER -> Icons.Rounded.Timer
    Kind.RECORDING -> Icons.Rounded.FiberManualRecord
    Kind.LIVE -> Icons.Rounded.Bolt
    Kind.MEDIA -> Icons.Rounded.MusicNote
    Kind.PROGRESS -> Icons.Rounded.Download
    Kind.NOTIFICATION -> Icons.Rounded.Notifications
    Kind.OTP -> Icons.Rounded.Key
    Kind.PAYMENT -> Icons.Rounded.CreditCard
    Kind.CHARGING -> if (item.compactText == "off") Icons.Rounded.PowerOff else Icons.Rounded.BatteryChargingFull
    Kind.BATTERY -> Icons.Rounded.BatteryAlert
    Kind.BLUETOOTH -> if (item.compactText == "off") Icons.Rounded.BluetoothDisabled else Icons.Rounded.BluetoothConnected
    Kind.RINGER -> when (item.compactText) {
        "silent" -> Icons.Rounded.NotificationsOff
        "vibrate" -> Icons.Rounded.Vibration
        else -> Icons.Rounded.NotificationsActive
    }
    Kind.AIRPLANE -> if (item.compactText == "off") Icons.Rounded.AirplanemodeInactive else Icons.Rounded.AirplanemodeActive
    Kind.HEADPHONES -> Icons.Rounded.Headphones
    Kind.INFO -> Icons.Rounded.Info
}

fun kindLabel(kind: Kind): String = when (kind) {
    Kind.CALL -> "Call"
    Kind.NAVIGATION -> "Navigation"
    Kind.TRIP -> "Trip"
    Kind.TIMER -> "Timer"
    Kind.RECORDING -> "Recording"
    Kind.LIVE -> "Live"
    Kind.MEDIA -> "Now playing"
    Kind.PROGRESS -> "Progress"
    Kind.NOTIFICATION -> "Notification"
    Kind.OTP -> "Code"
    Kind.PAYMENT -> "Payment"
    else -> "System"
}
