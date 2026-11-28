package dev.jotnar.materialisland.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jotnar.materialisland.model.BudsInfo
import dev.jotnar.materialisland.model.IslandItem
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import kotlin.math.PI
import kotlin.math.sin

// ── Ilustraciones propias (dibujadas con Canvas, sin imágenes de terceros) ──────────────

private val BudWhite = Color(0xFFF4F3F7)
private val BudShade = Color(0xFFC9C7D1)
private val BatteryGreen = Color(0xFF6DD58C)
private val BatteryRed = Color(0xFFFFB4AB)

internal fun batteryColor(level: Int?, p: IslandPalette): Color = when {
    level == null -> p.variant
    level <= dev.jotnar.materialisland.core.Buds.LOW -> BatteryRed
    else -> BatteryGreen
}

/** Un auricular de botón visto de frente: cabeza redonda, almohadilla de color y patilla. dir = 1 izquierdo, -1 derecho. */
private fun DrawScope.bud(cx: Float, cy: Float, s: Float, dir: Float, tip: Color) {
    rotate(degrees = 14f * dir, pivot = Offset(cx, cy)) {
        // Patilla
        drawRoundRect(
            Brush.horizontalGradient(listOf(BudWhite, BudShade), startX = cx - 0.08f * s, endX = cx + 0.08f * s),
            topLeft = Offset(cx - 0.075f * s, cy), size = Size(0.15f * s, 0.46f * s),
            cornerRadius = CornerRadius(0.075f * s),
        )
        // Micrófono al final de la patilla
        drawRoundRect(
            BudShade.copy(alpha = 0.9f), topLeft = Offset(cx - 0.045f * s, cy + 0.38f * s), size = Size(0.09f * s, 0.05f * s),
            cornerRadius = CornerRadius(0.025f * s),
        )
        // Cabeza
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, BudWhite, BudShade), center = Offset(cx - 0.06f * s * dir, cy - 0.1f * s), radius = 0.3f * s,
            ),
            radius = 0.21f * s, center = Offset(cx, cy - 0.04f * s),
        )
        // Almohadilla (hacia dentro), del color de acento
        drawOval(
            tip, topLeft = Offset(cx + dir * 0.1f * s - 0.075f * s, cy - 0.16f * s), size = Size(0.15f * s, 0.2f * s),
        )
        // Brillo
        drawCircle(Color.White.copy(alpha = 0.7f), radius = 0.035f * s, center = Offset(cx - 0.09f * s * dir, cy - 0.13f * s))
    }
}

private fun DrawScope.case(cx: Float, cy: Float, s: Float, led: Color) {
    val w = 0.78f * s; val h = 0.56f * s
    val tl = Offset(cx - w / 2, cy - h / 2)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White, BudWhite, BudShade), startY = tl.y, endY = tl.y + h),
        topLeft = tl, size = Size(w, h), cornerRadius = CornerRadius(0.24f * s),
    )
    // Línea de la tapa
    drawLine(BudShade, Offset(tl.x + 0.06f * s, tl.y + h * 0.36f), Offset(tl.x + w - 0.06f * s, tl.y + h * 0.36f), strokeWidth = 0.018f * s)
    // Luz de estado
    drawCircle(led, radius = 0.03f * s, center = Offset(cx, tl.y + h * 0.62f))
}

private fun DrawScope.headphones(s: Float, cushion: Color, wave: Float) {
    val c = Offset(0.5f * s, 0.52f * s)
    // Diadema
    drawArc(
        Brush.verticalGradient(listOf(Color.White, BudShade)), startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(0.17f * s, 0.14f * s), size = Size(0.66f * s, 0.7f * s),
        style = Stroke(width = 0.07f * s, cap = StrokeCap.Round),
    )
    // Copas
    for (dir in listOf(-1f, 1f)) {
        val x = c.x + dir * 0.33f * s
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White, BudWhite, BudShade)),
            topLeft = Offset(x - 0.11f * s, c.y - 0.06f * s), size = Size(0.22f * s, 0.36f * s), cornerRadius = CornerRadius(0.11f * s),
        )
        drawRoundRect(
            cushion, topLeft = Offset(x - 0.055f * s - dir * 0.04f * s, c.y - 0.02f * s), size = Size(0.11f * s, 0.28f * s),
            cornerRadius = CornerRadius(0.055f * s),
        )
        // Ondas de sonido hacia fuera
        for (i in 0..1) {
            val k = ((wave + i * 0.5f) % 1f)
            val r = (0.08f + 0.1f * k) * s
            drawArc(
                cushion.copy(alpha = (1f - k) * 0.8f), startAngle = if (dir < 0) 135f else -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(x + dir * 0.13f * s - r, c.y + 0.12f * s - r), size = Size(2 * r, 2 * r),
                style = Stroke(width = 0.022f * s, cap = StrokeCap.Round),
            )
        }
    }
}

/** Parlante portátil en forma de cápsula: rejilla, radiador pasivo que late y ondas de sonido a los lados. */
private fun DrawScope.speaker(s: Float, accent: Color, wave: Float) {
    val beat = sin(wave * 4 * PI).toFloat().let { if (it > 0f) it else 0f }
    val bodyL = 0.16f * s; val bodyT = 0.34f * s; val bodyW = 0.68f * s; val bodyH = 0.36f * s
    // Sombra
    drawOval(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0.24f * s, 0.74f * s), size = Size(0.52f * s, 0.06f * s))
    // Ondas a cada lado
    for (dir in listOf(-1f, 1f)) for (i in 0..1) {
        val k = (wave * 2 + i * 0.5f) % 1f
        val r = (0.06f + 0.09f * k) * s
        val cx = if (dir < 0) bodyL - 0.01f * s else bodyL + bodyW + 0.01f * s
        drawArc(
            accent.copy(alpha = (1f - k) * 0.8f), startAngle = if (dir < 0) 135f else -45f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(cx - r, 0.52f * s - r), size = Size(2 * r, 2 * r), style = Stroke(0.025f * s, cap = StrokeCap.Round),
        )
    }
    // Cuerpo con tela del color de acento
    drawRoundRect(
        Brush.verticalGradient(listOf(accent, accent.copy(red = accent.red * 0.75f, green = accent.green * 0.75f, blue = accent.blue * 0.75f)), startY = bodyT, endY = bodyT + bodyH),
        topLeft = Offset(bodyL, bodyT), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(bodyH / 2),
    )
    // Rejilla en toda la tela (puntitos)
    val step = 0.04f * s
    var y = bodyT + 0.06f * s
    while (y < bodyT + bodyH - 0.04f * s) {
        var x = bodyL + bodyH * 0.3f
        while (x < bodyL + bodyW - bodyH * 0.25f) {
            drawCircle(Color.Black.copy(alpha = 0.2f), radius = 0.0075f * s, center = Offset(x, y)); x += step
        }
        y += step
    }
    // Extremos: tapas de goma y el radiador pasivo visto de canto, que se ensancha con el ritmo
    for ((cx, dir) in listOf(bodyL + 0.035f * s to -1f, bodyL + bodyW - 0.035f * s to 1f)) {
        val rw = 0.05f * s + 0.018f * s * beat
        drawOval(BudShade, topLeft = Offset(cx - rw / 2 + dir * 0.01f * s, bodyT + 0.02f * s), size = Size(rw, bodyH - 0.04f * s))
        drawOval(Color.White.copy(alpha = 0.8f), topLeft = Offset(cx - rw * 0.25f + dir * 0.012f * s, bodyT + bodyH * 0.25f), size = Size(rw * 0.5f, bodyH * 0.5f))
    }
    // Placa del logo en el centro
    drawRoundRect(Color.White.copy(alpha = 0.92f), topLeft = Offset(0.5f * s - 0.06f * s, bodyT + bodyH / 2 - 0.028f * s), size = Size(0.12f * s, 0.056f * s), cornerRadius = CornerRadius(0.028f * s))
    // Correa
    drawRoundRect(BudShade, topLeft = Offset(bodyL + bodyW * 0.42f, bodyT - 0.05f * s), size = Size(bodyW * 0.16f, 0.07f * s), cornerRadius = CornerRadius(0.03f * s))
}

/** Portátil abierto: la pantalla (del color de acento) se abre al entrar y muestra el símbolo de Bluetooth latiendo. */
private fun DrawScope.laptop(s: Float, accent: Color, open: Float, wave: Float) {
    val hingeY = 0.66f * s
    // Sombra y base (teclado en perspectiva)
    drawOval(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0.12f * s, 0.74f * s), size = Size(0.76f * s, 0.06f * s))
    val base = androidx.compose.ui.graphics.Path().apply {
        moveTo(0.2f * s, hingeY); lineTo(0.8f * s, hingeY); lineTo(0.9f * s, 0.75f * s); lineTo(0.1f * s, 0.75f * s); close()
    }
    drawPath(base, Brush.verticalGradient(listOf(BudWhite, BudShade), startY = hingeY, endY = 0.75f * s))
    for (r in 0..1) drawLine(BudShade.copy(alpha = 0.9f), Offset((0.24f - r * 0.04f) * s, (0.69f + r * 0.03f) * s), Offset((0.76f + r * 0.04f) * s, (0.69f + r * 0.03f) * s), strokeWidth = 0.012f * s)
    drawRoundRect(BudShade, topLeft = Offset(0.43f * s, 0.725f * s), size = Size(0.14f * s, 0.018f * s), cornerRadius = CornerRadius(0.01f * s))
    // Pantalla: se "levanta" desde la bisagra (escala vertical)
    val h = 0.44f * s * open
    if (h > 1f) {
        drawRoundRect(Color(0xFF2B2930), topLeft = Offset(0.2f * s, hingeY - h), size = Size(0.6f * s, h), cornerRadius = CornerRadius(0.03f * s))
        val inset = 0.025f * s
        if (h > 2 * inset) {
            drawRoundRect(
                Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)), start = Offset(0.2f * s, hingeY - h), end = Offset(0.8f * s, hingeY)),
                topLeft = Offset(0.2f * s + inset, hingeY - h + inset), size = Size(0.6f * s - 2 * inset, h - 2 * inset), cornerRadius = CornerRadius(0.015f * s),
            )
            // Símbolo de Bluetooth que late
            if (open > 0.9f) {
                val pulse = 1f + 0.08f * sin(wave * 2 * PI).toFloat()
                val cx = 0.5f * s; val cy = hingeY - h / 2; val u = 0.075f * s * pulse
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - u * 0.7f, cy - u * 0.55f); lineTo(cx + u * 0.6f, cy + u * 0.5f); lineTo(cx, cy + u)
                    lineTo(cx, cy - u); lineTo(cx + u * 0.6f, cy - u * 0.5f); lineTo(cx - u * 0.7f, cy + u * 0.55f)
                }
                drawPath(p, Color.White, style = Stroke(0.02f * s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

/**
 * Ilustración animada de los auriculares: entran con un muelle (cada uno con un poco de retraso) y luego
 * flotan suavemente. Los cascos entran escalando y emiten ondas.
 */
@Composable
internal fun BudsArt(type: BudsInfo.Type, size: Dp, accent: Color, showCase: Boolean = false, caseLevel: Int? = null, animate: Boolean = true) {
    val inL = remember { Animatable(if (animate) 0f else 1f) }
    val inR = remember { Animatable(if (animate) 0f else 1f) }
    val inC = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        kotlinx.coroutines.coroutineScope {
            launch { inC.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
            launch { kotlinx.coroutines.delay(90); inL.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            launch { kotlinx.coroutines.delay(180); inR.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        }
    }
    val t = rememberInfiniteTransition(label = "buds")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    val bob = if (animate) phase else 0f
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        when (type) {
            BudsInfo.Type.EARBUDS -> {
                if (showCase) {
                    val k = inC.value
                    translate(top = (1f - k) * 0.2f * s) {
                        case(0.5f * s, 0.8f * s, 0.58f * s, batteryColor(caseLevel, IslandPaletteStub).copy(alpha = k))
                    }
                }
                val baseY = if (showCase) 0.27f * s else 0.36f * s
                val bs = if (showCase) 0.7f * s else 0.9f * s
                val dyL = sin(bob * 2 * PI).toFloat() * 0.025f * s
                val dyR = sin((bob + 0.5f) * 2 * PI).toFloat() * 0.025f * s
                val l = inL.value; val r = inR.value
                translate(top = dyL + (1f - l) * 0.35f * s) {
                    scale(0.6f + 0.4f * l, Offset(0.27f * s, baseY)) { bud(0.27f * s, baseY, bs, 1f, accent) }
                }
                translate(top = dyR + (1f - r) * 0.35f * s) {
                    scale(0.6f + 0.4f * r, Offset(0.73f * s, baseY)) { bud(0.73f * s, baseY, bs, -1f, accent) }
                }
            }
            BudsInfo.Type.SPEAKER -> {
                val k = inL.value
                translate(top = (1f - k) * 0.25f * s) {
                    scale(0.6f + 0.4f * k, Offset(0.5f * s, 0.55f * s)) { speaker(s, accent, if (animate) phase else 0.12f) }
                }
            }
            BudsInfo.Type.COMPUTER -> {
                // La base entra y luego se abre la tapa.
                val k = inC.value
                scale(0.75f + 0.25f * k, Offset(0.5f * s, 0.7f * s)) { laptop(s, accent, inR.value, if (animate) phase else 0f) }
            }
            BudsInfo.Type.HEADPHONES -> {
                val k = inL.value
                val dy = sin(bob * 2 * PI).toFloat() * 0.015f * s
                translate(top = dy) {
                    scale(0.7f + 0.3f * k, Offset(0.5f * s, 0.5f * s)) { headphones(s, accent, if (animate) phase else 0.3f) }
                }
            }
        }
    }
}

/** Solo para el color de la luz del estuche sin depender de la paleta (verde / rojo / gris). */
private val IslandPaletteStub = IslandPalette(
    bg = Color.Black, content = Color.White, variant = Color(0xFF9E9AA6), accent = Color.White,
    accentContainer = Color.Black, onAccentContainer = Color.White, track = Color.Gray,
)

/** Pila pequeña horizontal con el nivel. */
@Composable
internal fun BatteryGlyph(level: Int?, color: Color, width: Dp = 22.dp) {
    Canvas(Modifier.width(width).height(width * 0.5f)) {
        val w = size.width; val h = size.height
        val body = Size(w * 0.88f, h)
        drawRoundRect(color.copy(alpha = 0.35f), size = body, cornerRadius = CornerRadius(h * 0.3f))
        val f = ((level ?: 0) / 100f).coerceIn(0f, 1f)
        if (f > 0f) drawRoundRect(color, size = Size(body.width * f, h), cornerRadius = CornerRadius(h * 0.3f))
        drawRoundRect(
            color.copy(alpha = 0.5f), topLeft = Offset(w * 0.91f, h * 0.3f), size = Size(w * 0.09f, h * 0.4f),
            cornerRadius = CornerRadius(h * 0.1f),
        )
    }
}

// ── Píldora ancha ────────────────────────────────────────────────────────────────────

@Composable
internal fun BudsPill(item: IslandItem, b: BudsInfo, p: IslandPalette, d: Dims) {
    val level = b.shown
    val color = if (b.connected) batteryColor(level, p) else p.variant
    Row(
        Modifier.width(d.wideW).height(d.pillH).padding(horizontal = d.edgePad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(d.icon), contentAlignment = Alignment.Center) {
                BudsArt(b.type, d.icon, if (b.connected) p.accent else p.variant, animate = false)
            }
            Spacer(Modifier.width(Sp.iconText))
            Text(
                b.name, style = MaterialTheme.typography.labelLarge, color = p.content,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(d.cameraW))
        Row(
            Modifier.weight(1f).padding(end = Sp.iconText), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (b.connected && level != null) {
                BatteryGlyph(level, color)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                item.value ?: "", color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.merge(Tabular).copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

// ── Tarjeta ──────────────────────────────────────────────────────────────────────────

/** Un componente con su nivel: "Left 80%", "Case 45%". */
@Composable
private fun PartLevel(label: String, level: Int?, p: IslandPalette, modifier: Modifier) {
    val c = batteryColor(level, p)
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(p.accentContainer.copy(alpha = 0.45f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.variant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryGlyph(level, c, 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                level?.let { "$it%" } ?: "–", color = p.content,
                style = MaterialTheme.typography.titleMedium.merge(Tabular).copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
internal fun BudsCard(item: IslandItem, p: IslandPalette) {
    val b = item.buds ?: return
    val level = b.shown
    Column(Modifier.fillMaxWidth().padding(Sp.edge)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BudsArt(b.type, 104.dp, p.accent, showCase = b.type == BudsInfo.Type.EARBUDS && b.case != null, caseLevel = b.case)
            Spacer(Modifier.width(Sp.edge))
            Column(Modifier.weight(1f)) {
                Caption(item.text ?: if (b.connected) "Connected" else "Disconnected", if (item.text == "Low battery") BatteryRed else p.variant)
                Spacer(Modifier.height(Sp.lines))
                Text(
                    b.name, style = MaterialTheme.typography.titleMedium, color = p.content,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (!b.hasParts && b.hasBattery) {
                    Spacer(Modifier.height(Sp.lines))
                    Text(
                        level?.let { "$it%" } ?: if (b.connected) "Battery unknown" else "",
                        color = p.content,
                        style = (if (level != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyMedium)
                            .merge(Tabular).copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        if (b.hasParts) {
            Spacer(Modifier.height(Sp.edge))
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.iconText)) {
                if (b.type == BudsInfo.Type.EARBUDS) {
                    PartLevel("Left", b.left, p, Modifier.weight(1f))
                    PartLevel("Right", b.right, p, Modifier.weight(1f))
                    if (b.case != null) PartLevel("Case", b.case, p, Modifier.weight(1f))
                } else {
                    PartLevel("Battery", b.shown, p, Modifier.weight(1f))
                }
            }
        } else if (level != null) {
            Spacer(Modifier.height(Sp.edge))
            val c = batteryColor(level, p)
            WavyProgress(level / 100f, animate = false, color = c, track = c.copy(alpha = 0.2f))
        }
    }
}
