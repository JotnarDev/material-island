package dev.jotnar.materialisland.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.TripInfo
import kotlin.math.sin

internal fun tripIcon(t: TripInfo?): ImageVector = when (t?.type) {
    TripInfo.Type.RIDE -> Icons.Rounded.DirectionsCar
    TripInfo.Type.PACKAGE -> Icons.Rounded.LocalShipping
    else -> Icons.Rounded.DeliveryDining
}

/** Dónde va el vehículo en la línea (0..1): el progreso real si la app lo da, si no según la etapa. */
private fun tripPosition(item: IslandItem, t: TripInfo): Float =
    // Con progreso real nunca se queda por detrás del punto de la etapa actual.
    if (item.progress >= 0f) maxOf(item.progress.coerceIn(0f, 1f), t.stage / 3f)
    else when (t.stage) { 0 -> 0.04f; 1 -> 0.36f; 2 -> 0.68f; else -> 1f }

/** Icono de la píldora compacta: el vehículo en un círculo, con un pequeño vaivén mientras avanza. */
@Composable
internal fun TripCompactIcon(item: IslandItem, p: IslandPalette, size: Dp) {
    val t = item.trip
    val moving = t != null && t.stage in 1..2
    val tr = rememberInfiniteTransition(label = "tripBob")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    Box(Modifier.size(size).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
        Icon(
            tripIcon(t), null, tint = p.onAccentContainer,
            modifier = Modifier.size(size * 0.62f).offset(y = if (moving) (sin(ph * 2 * Math.PI) * 1.2).dp else 0.dp),
        )
    }
}

/**
 * Línea de la ruta con cuatro etapas. La parte recorrida va en color de acento; el resto, punteada.
 * El vehículo se desliza con un muelle cuando cambia la etapa o el progreso.
 */
@Composable
private fun TripTrack(item: IslandItem, t: TripInfo, p: IslandPalette) {
    val target = tripPosition(item, t)
    val pos by animateFloatAsState(target, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow), label = "tripPos")
    val tr = rememberInfiniteTransition(label = "tripTrack")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    val chip = 30.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(chip + 4.dp)) {
        val w = maxWidth
        Canvas(Modifier.fillMaxWidth().height(chip + 4.dp)) {
            val y = size.height / 2
            val r = 4.dp.toPx()
            val x0 = r; val x1 = size.width - r
            val xp = x0 + (x1 - x0) * pos
            drawLine(
                p.variant.copy(alpha = 0.45f), Offset(xp, y), Offset(x1, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
            drawLine(p.accent, Offset(x0, y), Offset(xp, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            for (i in 0..3) {
                val x = x0 + (x1 - x0) * (i / 3f)
                val done = i <= t.stage
                drawCircle(if (done) p.accent else p.bg, radius = r + 1.dp.toPx(), center = Offset(x, y))
                drawCircle(if (done) p.accent else p.variant.copy(alpha = 0.7f), radius = r + 1.dp.toPx(), center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
        val moving = t.stage in 1..2
        val x = (w - chip) * pos
        Box(
            Modifier.offset(x = x, y = if (moving) (sin(ph * 2 * Math.PI) * 1.0).dp else 0.dp)
                .padding(top = 2.dp).size(chip).clip(CircleShape).background(p.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tripIcon(t), null, tint = Color.Black.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        t.stages.forEachIndexed { i, label ->
            Text(
                label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = when (i) { 0 -> TextAlign.Start; 3 -> TextAlign.End; else -> TextAlign.Center },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (i == t.stage) FontWeight.Bold else FontWeight.Normal),
                color = if (i == t.stage) p.content else p.variant,
            )
        }
    }
}

/** Matrícula como una placa: borde fino y letras con espaciado fijo. */
@Composable
private fun Plate(text: String, p: IslandPalette) {
    Text(
        text, color = p.content, maxLines = 1,
        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.accentContainer.copy(alpha = 0.5f))
            .border(1.dp, p.variant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Cuerpo de la tarjeta: texto de la app, tiempo de llegada grande, ruta con etapas y conductor. */
@Composable
internal fun TripBody(item: IslandItem, p: IslandPalette, actions: @Composable () -> Unit) {
    val t = item.trip ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Ilustración que distingue viaje, comida o paquete.
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(p.accentContainer.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) { TripArt(t.type, t.stage, 52.dp, p.accent) }
        Spacer(Modifier.width(Sp.group))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.text?.let {
                Spacer(Modifier.height(Sp.lines))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        t.eta?.let { eta ->
            Spacer(Modifier.width(Sp.group))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    eta, color = p.accent, maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall.merge(Tabular).copy(fontWeight = FontWeight.SemiBold),
                )
                Text(if (t.type == TripInfo.Type.RIDE && t.stage <= 1) "Pickup" else "Arrival", style = MaterialTheme.typography.labelSmall, color = p.variant)
            }
        }
    }
    Spacer(Modifier.height(Sp.group + 4.dp))
    TripTrack(item, t, p)
    if (t.driver != null || t.plate != null) {
        Spacer(Modifier.height(Sp.group))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val img = item.image
            Box(Modifier.size(Sp.action).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
                if (img != null) Image(img.asImageBitmap(), null, Modifier.size(Sp.action).clip(CircleShape))
                else Icon(Icons.Rounded.Person, null, tint = p.onAccentContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(Sp.iconText + 2.dp))
            Column(Modifier.weight(1f)) {
                Text(t.driver ?: "Driver", style = MaterialTheme.typography.labelLarge, color = p.content, maxLines = 1)
                t.vehicle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            t.plate?.let { Plate(it, p) }
        }
    }
    actions()
}

