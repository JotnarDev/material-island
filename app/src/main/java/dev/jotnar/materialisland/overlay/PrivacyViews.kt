package dev.jotnar.materialisland.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import dev.jotnar.materialisland.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jotnar.materialisland.model.IslandItem

/** Verde del indicador de privacidad de Android (el mismo tono que usa el sistema). */
internal val PrivacyGreen = Color(0xFF6DD58C)

/** Punto verde que "respira", como el del sistema. */
@Composable
internal fun PrivacyDot(size: Dp) {
    val t = rememberInfiniteTransition(label = "privDot")
    val a by t.animateFloat(1f, 0.45f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).graphicsLayer { alpha = a }.clip(CircleShape).background(PrivacyGreen))
}

/** Izquierda de la píldora: cámara o micrófono sobre un círculo verde. */
@Composable
internal fun PrivacyCompactIcon(item: IslandItem, size: Dp) {
    val cam = item.privacy?.camera == true
    Box(Modifier.size(size).clip(CircleShape).background(PrivacyGreen), contentAlignment = Alignment.Center) {
        Icon(if (cam) Icons.Rounded.Videocam else Icons.Rounded.Mic, null, tint = Color.Black.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.6f))
    }
}

/** Derecha de la píldora: los iconos que estén en uso y el punto. */
@Composable
internal fun PrivacyCompactRight(item: IslandItem, size: Dp) {
    val pr = item.privacy
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (pr?.camera == true && pr.mic) Icon(Icons.Rounded.Mic, null, tint = PrivacyGreen, modifier = Modifier.size(size * 0.7f))
        PrivacyDot(size * 0.42f)
    }
}

@Composable
private fun SensorChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, p: IslandPalette, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "chip")
    val s by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Row(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) PrivacyGreen.copy(alpha = 0.16f) else p.accentContainer.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).scale(if (on) s else 1f).clip(CircleShape).background(if (on) PrivacyGreen else p.variant.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (on) Color.Black.copy(alpha = 0.85f) else p.variant, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, color = p.content, maxLines = 1)
            Text(if (on) "In use" else "Off", style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) PrivacyGreen else p.variant)
        }
    }
}

/** Tarjeta: qué sensor está en uso, la pista de qué app lo empezó y acceso a los ajustes de privacidad. */
@Composable
internal fun PrivacyBody(item: IslandItem, p: IslandPalette, actions: @Composable () -> Unit) {
    val pr = item.privacy ?: return
    Text(item.title, style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    item.text?.let {
        Spacer(Modifier.height(Sp.lines))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = p.variant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(Sp.group))
    Row(horizontalArrangement = Arrangement.spacedBy(Sp.group)) {
        SensorChip(Icons.Rounded.Videocam, "Camera", pr.camera, p, Modifier.weight(1f))
        SensorChip(Icons.Rounded.Mic, "Microphone", pr.mic, p, Modifier.weight(1f))
    }
    actions()
}
