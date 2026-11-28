package dev.jotnar.materialisland.overlay

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.NetworkWifi1Bar
import androidx.compose.material.icons.rounded.NetworkWifi2Bar
import androidx.compose.material.icons.rounded.NetworkWifi3Bar
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SignalWifi0Bar
import androidx.compose.material.icons.rounded.SignalWifi4Bar
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import dev.jotnar.materialisland.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jotnar.materialisland.core.IslandStore
import dev.jotnar.materialisland.data.BarItem
import dev.jotnar.materialisland.ui.theme.PixelFonts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Barra de estado propia de la Liquid bar, dibujada para parecerse a la del Pixel (Android 16):
// misma fuente, tamaños y formas. Todo se lee en el teléfono; nada se envía.

private val IconSize = 17.dp
private val Gap = 5.dp

private fun barText(size: Float, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = PixelFonts.sans, fontSize = size.sp, fontWeight = weight, letterSpacing = 0.sp,
    fontFeatureSettings = "tnum",
)

/** Estado del sistema que la barra necesita; se sondea cada segundo mientras la barra está a la vista. */
private class SysState {
    var alarm by mutableStateOf(false)
    var dnd by mutableStateOf(false)
    var ringer by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)
    var btOn by mutableStateOf(false)
    var btConnected by mutableStateOf(false)
    var airplane by mutableStateOf(false)
    var wifiLevel by mutableIntStateOf(-1)      // -1 = sin wifi
    var mobileLevel by mutableIntStateOf(-1)    // -1 = sin SIM o sin señal
    var battery by mutableIntStateOf(-1)
    var charging by mutableStateOf(false)
    var tempC by mutableStateOf(Float.NaN)
    var hot by mutableStateOf(false)
    var ramPct by mutableIntStateOf(-1)
    var down by mutableLongStateOf(0)
    var up by mutableLongStateOf(0)
}

@Composable
private fun rememberSysState(items: Set<BarItem>): SysState {
    val ctx = LocalContext.current
    val st = remember { SysState() }

    // Batería y temperatura (aviso del sistema, sin sondeo).
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) st.battery = l * 100 / sc.coerceAtLeast(1)
                val s = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                st.charging = s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
                i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { st.tempC = it / 10f }
            }
        }
        ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { r.onReceive(ctx, it) }
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }

    // Wifi: nivel de la red por defecto.
    DisposableEffect(BarItem.WIFI in items) {
        if (BarItem.WIFI !in items) return@DisposableEffect onDispose { }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val wm = ctx.getSystemService(WifiManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                st.wifiLevel = if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val rssi = c.signalStrength
                    if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) 4
                    else (wm?.calculateSignalLevel(rssi) ?: 4).let { lv -> (lv * 4f / (wm?.maxSignalLevel ?: 4).coerceAtLeast(1)).roundToInt() }
                } else -1
            }
            override fun onLost(n: Network) { st.wifiLevel = -1 }
        }
        // Cualquier wifi conectado, aunque no sea la red por defecto (como hace el Pixel con señal débil).
        val req = android.net.NetworkRequest.Builder().clearCapabilities().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { cm?.registerNetworkCallback(req, cb) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }

    // Señal móvil: no necesita permisos.
    DisposableEffect(BarItem.MOBILE in items) {
        if (BarItem.MOBILE !in items) return@DisposableEffect onDispose { }
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(s: SignalStrength) { st.mobileLevel = s.level }
        }
        val ok = runCatching { tm?.registerTelephonyCallback(ctx.mainExecutor, cb) }.isSuccess
        if (tm?.simState != TelephonyManager.SIM_STATE_READY) st.mobileLevel = -1
        onDispose { if (ok) runCatching { tm?.unregisterTelephonyCallback(cb) } }
    }

    // Lo demás se mira cada segundo (llamadas baratas, solo mientras la barra se ve).
    LaunchedEffect(items) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val alarms = ctx.getSystemService(AlarmManager::class.java)
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        val pm = ctx.getSystemService(PowerManager::class.java)
        val act = ctx.getSystemService(ActivityManager::class.java)
        var lastRx = TrafficStats.getTotalRxBytes(); var lastTx = TrafficStats.getTotalTxBytes(); var lastT = System.nanoTime()
        var tick = 0
        while (true) {
            st.alarm = alarms?.nextAlarmClock != null
            st.dnd = (nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) > NotificationManager.INTERRUPTION_FILTER_ALL
            st.ringer = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            st.btOn = runCatching { bt?.isEnabled == true }.getOrDefault(false)
            st.btConnected = st.btOn && runCatching {
                bt?.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET) == android.bluetooth.BluetoothAdapter.STATE_CONNECTED ||
                    bt?.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == android.bluetooth.BluetoothAdapter.STATE_CONNECTED
            }.getOrDefault(false)
            st.airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            st.hot = (pm?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE
            if (BarItem.NET_SPEED in items) {
                val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes(); val t = System.nanoTime()
                val secs = ((t - lastT) / 1e9).coerceAtLeast(0.2)
                st.down = ((rx - lastRx) / secs).toLong().coerceAtLeast(0); st.up = ((tx - lastTx) / secs).toLong().coerceAtLeast(0)
                lastRx = rx; lastTx = tx; lastT = t
            }
            if (BarItem.RAM in items && tick % 3 == 0) {
                val mi = ActivityManager.MemoryInfo().also { act?.getMemoryInfo(it) }
                if (mi.totalMem > 0) st.ramPct = (100 - mi.availMem * 100 / mi.totalMem).toInt()
            }
            tick++
            delay(1000)
        }
    }
    return st
}

// ── Piezas ────────────────────────────────────────────────────────────

@Composable
private fun Clock(color: Color) {
    val ctx = LocalContext.current
    fun now() = java.text.SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", Locale.getDefault()).format(Date())
    var text by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) { text = now(); delay(60_000L - System.currentTimeMillis() % 60_000L + 50) }
    }
    Text(text, color = color, maxLines = 1, style = barText(16f))
}

/** Batería del Pixel (Android 16): píldora con el porcentaje dentro y la parte llena según la carga. */
@Composable
private fun PixelBattery(level: Int, charging: Boolean, showPct: Boolean, color: Color) {
    val fill = when {
        charging -> Color(0xFF6DD58C)
        level <= 15 -> Color(0xFFFF8A80)
        else -> color
    }
    val w = if (showPct) 27.dp else 22.dp
    Box(Modifier.width(w + 3.dp).height(14.dp), contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.width(w + 3.dp).height(14.dp)) {
            val bw = w.toPx(); val h = size.height
            val r = CornerRadius(4.5.dp.toPx())
            drawRoundRect(color.copy(alpha = 0.32f), size = Size(bw, h), cornerRadius = r)
            // Relleno recortado a la forma de la píldora.
            val f = bw * level.coerceIn(0, 100) / 100f
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, f, h)
            drawRoundRect(fill, size = Size(bw, h), cornerRadius = r)
            drawContext.canvas.restore()
            drawRoundRect(color.copy(alpha = 0.32f), topLeft = Offset(bw + 1.dp.toPx(), h * 0.32f), size = Size(1.8.dp.toPx(), h * 0.36f), cornerRadius = CornerRadius(1.dp.toPx()))
        }
        if (showPct) Text(
            "$level", modifier = Modifier.width(w), textAlign = TextAlign.Center, maxLines = 1,
            color = Color.Black.copy(alpha = 0.88f), style = barText(10.5f, FontWeight.Bold),
        )
    }
}

/** Barras de cobertura del Pixel: cuatro barritas redondeadas, las apagadas en gris. */
@Composable
private fun MobileBars(level: Int, color: Color) {
    Canvas(Modifier.width(15.dp).height(IconSize)) {
        val bw = 2.6.dp.toPx(); val gap = (size.width - 4 * bw) / 3; val base = size.height * 0.84f; val top = size.height * 0.18f
        for (i in 0..3) {
            val h = (base - top) * (0.4f + 0.2f * i)
            val x = i * (bw + gap)
            drawRoundRect(
                if (i < level.coerceAtLeast(0)) color else color.copy(alpha = 0.3f),
                topLeft = Offset(x, base - h), size = Size(bw, h), cornerRadius = CornerRadius(bw / 2),
            )
        }
    }
}

@Composable
private fun SysIcon(v: ImageVector, color: Color, size: Dp = IconSize) = Icon(v, null, tint = color, modifier = Modifier.size(size))

private fun wifiIcon(level: Int): ImageVector = when (level) {
    0 -> Icons.Rounded.SignalWifi0Bar
    1 -> Icons.Rounded.NetworkWifi1Bar
    2 -> Icons.Rounded.NetworkWifi2Bar
    3 -> Icons.Rounded.NetworkWifi3Bar
    else -> Icons.Rounded.SignalWifi4Bar
}

private fun speed(bps: Long): String = when {
    bps >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB/s", bps / 1_000_000f)
    bps >= 1_000 -> "${bps / 1_000} KB/s"
    else -> "$bps B/s"
}

/** Dos líneas pequeñas, como los indicadores de velocidad de las ROMs. */
@Composable
private fun TwoLine(top: String, bottom: String, color: Color) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy((-3).dp)) {
        Text(top, color = color, maxLines = 1, style = barText(9.5f, FontWeight.SemiBold))
        Text(bottom, color = color.copy(alpha = 0.75f), maxLines = 1, style = barText(9.5f, FontWeight.SemiBold))
    }
}

// ── Barra completa ────────────────────────────────────────────────────

/**
 * Contenido de la Liquid bar: a la izquierda la hora y los iconos de notificaciones (hasta donde empieza la
 * zona de la cámara); a la derecha los indicadores y la batería. [leftMax] y [rightMax] es el sitio de cada lado.
 */
@Composable
internal fun BarStatusContent(items: Set<BarItem>, leftMax: Dp, rightMax: Dp, modifier: Modifier = Modifier) {
    val st = rememberSysState(items)
    val c = Color.White
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Izquierda
        Row(Modifier.widthIn(max = leftMax).clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
            Clock(c)
            if (BarItem.NOTIFICATIONS in items) {
                val icons by IslandStore.statusIcons.collectAsStateWithLifecycle()
                // Caben los que quepan después de la hora (~44 dp); si sobran, un punto como en el Pixel.
                val room = ((leftMax.value - 50f) / (IconSize.value + 4f)).toInt().coerceAtLeast(0)
                val shown = if (icons.size > room) icons.take((room - 1).coerceAtLeast(0)) else icons
                if (shown.isNotEmpty() || icons.isNotEmpty()) Spacer(Modifier.width(8.dp))
                shown.forEach { (pkg, bmp) ->
                    androidx.compose.runtime.key(pkg) {
                        Image(bmp.asImageBitmap(), null, Modifier.size(IconSize - 1.dp), colorFilter = ColorFilter.tint(c))
                        Spacer(Modifier.width(4.dp))
                    }
                }
                if (icons.size > shown.size && room > 0) Canvas(Modifier.size(6.dp)) { drawCircle(c) }
            }
        }
        Spacer(Modifier.weight(1f))
        // Derecha: de la batería hacia la izquierda; lo que no cabe entero no se muestra (los extras primero).
        FitRowEnd(rightMax, Gap) {
                run {
                    if (st.battery >= 0) PixelBattery(st.battery, st.charging, BarItem.BATTERY_PERCENT in items, c)
                    if (BarItem.MOBILE in items && st.mobileLevel >= 0 && !st.airplane) MobileBars(st.mobileLevel, c)
                    if (BarItem.WIFI in items && st.wifiLevel >= 0) SysIcon(wifiIcon(st.wifiLevel), c)
                    if (BarItem.AIRPLANE in items && st.airplane) SysIcon(Icons.Rounded.AirplanemodeActive, c)
                    if (BarItem.BLUETOOTH in items && st.btOn) SysIcon(if (st.btConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth, c, 16.dp)
                    if (BarItem.RINGER in items && st.ringer != AudioManager.RINGER_MODE_NORMAL) {
                        SysIcon(if (st.ringer == AudioManager.RINGER_MODE_VIBRATE) Icons.Rounded.Vibration else Icons.Rounded.NotificationsOff, c)
                    }
                    if (BarItem.DND in items && st.dnd) SysIcon(Icons.Rounded.DoNotDisturbOn, c)
                    if (BarItem.ALARM in items && st.alarm) SysIcon(Icons.Rounded.Alarm, c, 16.dp)
                    if (BarItem.RAM in items && st.ramPct >= 0) TwoLine("${st.ramPct}%", "RAM", c)
                    if (BarItem.TEMPERATURE in items && !st.tempC.isNaN()) {
                        Text("${st.tempC.roundToInt()}°", color = if (st.hot) Color(0xFFFF8A80) else c, maxLines = 1, style = barText(13f, FontWeight.SemiBold))
                    }
                    if (BarItem.NET_SPEED in items) TwoLine("↓ " + speed(st.down), "↑ " + speed(st.up), c)
                }
        }
    }
}

/**
 * Fila alineada a la derecha que coloca sus hijos en orden de derecha a izquierda (el primero, pegado al borde)
 * y deja fuera, enteros, los que ya no caben en [maxWidth]. Así nunca se ve un icono cortado.
 */
@Composable
private fun FitRowEnd(maxWidth: Dp, gap: Dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, Modifier.widthIn(max = maxWidth).fillMaxHeight()) { ms, cons ->
        val loose = cons.copy(minWidth = 0, minHeight = 0)
        val g = gap.roundToPx()
        val placed = ArrayList<androidx.compose.ui.layout.Placeable>()
        var used = 0
        for (m in ms) {
            val p = m.measure(loose)
            val need = p.width + if (placed.isEmpty()) 0 else g
            if (used + need > cons.maxWidth) break
            used += need; placed += p
        }
        layout(used, cons.maxHeight) {
            var x = used
            placed.forEach { p ->
                x -= p.width
                p.place(x, (cons.maxHeight - p.height) / 2)
                x -= g
            }
        }
    }
}
