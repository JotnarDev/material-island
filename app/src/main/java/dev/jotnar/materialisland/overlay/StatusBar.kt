package dev.jotnar.materialisland.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/** Hora del sistema, actualizada al cambiar el minuto. */
@Composable
internal fun BarClock(color: Color) {
    val ctx = LocalContext.current
    fun now() = java.text.SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", Locale.getDefault()).format(Date())
    var text by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            text = now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
    }
    Text(text, color = color, maxLines = 1, style = MaterialTheme.typography.titleSmall.merge(Tabular).copy(fontWeight = FontWeight.SemiBold))
}

/** Batería: porcentaje y un icono que se llena (verde cargando, rojo con poca). */
@Composable
internal fun BarBattery(color: Color) {
    val ctx = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) level = l * 100 / sc.coerceAtLeast(1)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { r.onReceive(ctx, it) }
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    if (level < 0) return
    val fill = when {
        charging -> Color(0xFF6DD58C)
        level <= 15 -> Color(0xFFFFB4AB)
        else -> color
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$level%", color = color, maxLines = 1, style = MaterialTheme.typography.labelLarge.merge(Tabular).copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(5.dp))
        Canvas(Modifier.width(22.dp).height(12.dp)) {
            val bodyW = size.width - 2.dp.toPx()
            val r = CornerRadius(3.dp.toPx())
            drawRoundRect(color.copy(alpha = 0.55f), size = Size(bodyW, size.height), cornerRadius = r, style = Stroke(1.4.dp.toPx()))
            val pad = 2.2.dp.toPx()
            drawRoundRect(fill, topLeft = Offset(pad, pad), size = Size((bodyW - 2 * pad) * level / 100f, size.height - 2 * pad), cornerRadius = CornerRadius(1.5.dp.toPx()))
            drawRoundRect(color.copy(alpha = 0.55f), topLeft = Offset(bodyW + 0.6.dp.toPx(), size.height * 0.3f), size = Size(1.6.dp.toPx(), size.height * 0.4f), cornerRadius = CornerRadius(1.dp.toPx()))
        }
    }
}
