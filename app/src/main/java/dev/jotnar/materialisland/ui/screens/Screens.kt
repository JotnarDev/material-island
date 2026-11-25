package dev.jotnar.materialisland.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotnar.materialisland.core.Demo
import dev.jotnar.materialisland.data.AccentMode
import dev.jotnar.materialisland.data.BackgroundMode
import dev.jotnar.materialisland.data.GestureAction
import dev.jotnar.materialisland.data.IdleAction
import dev.jotnar.materialisland.data.IslandSettings
import dev.jotnar.materialisland.data.NotificationMode
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.ui.ChoiceRow
import dev.jotnar.materialisland.ui.Group
import dev.jotnar.materialisland.ui.GroupTitle
import dev.jotnar.materialisland.ui.IslandPreview
import dev.jotnar.materialisland.ui.SegmentedRow
import dev.jotnar.materialisland.ui.SettingsScaffold
import dev.jotnar.materialisland.ui.SliderRow
import dev.jotnar.materialisland.ui.SwatchColors
import dev.jotnar.materialisland.ui.SwatchRow
import dev.jotnar.materialisland.ui.SwitchRow
import kotlin.math.roundToInt

@Composable
private fun settings(): IslandSettings = SettingsStore.flow.collectAsStateWithLifecycle().value

private fun set(block: (IslandSettings) -> IslandSettings) = SettingsStore.update(block)

private fun dp(v: Float) = "%.1f dp".format(v).replace(".0 dp", " dp")
private fun sec(v: Int, zero: String = "Nunca") = if (v == 0) zero else "$v s"

// ── Apariencia ────────────────────────────────────────────────────────

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Apariencia", onBack) {
        IslandPreview(200.dp)
        GroupTitle("Fondo")
        Group(
            {
                SegmentedRow("Color de fondo", BackgroundMode.entries, s.background, { it.label }) { m ->
                    set { it.copy(background = m) }
                }
            },
            *if (s.background == BackgroundMode.CUSTOM) arrayOf<@Composable () -> Unit>({
                SwatchRow("Fondo personalizado", SwatchColors.take(5), s.customBackground) { c -> set { it.copy(customBackground = c) } }
            }) else emptyArray(),
            {
                SwitchRow(
                    "Tinte de la carátula", s.albumTint,
                    "La tarjeta de música toma el color del álbum", Icons.Rounded.Album,
                ) { v -> set { it.copy(albumTint = v) } }
            },
        )
        GroupTitle("Acento")
        Group(
            {
                SegmentedRow("Color de acento", AccentMode.entries, s.accent, { it.label }) { m -> set { it.copy(accent = m) } }
            },
            *if (s.accent == AccentMode.CUSTOM) arrayOf<@Composable () -> Unit>({
                SwatchRow("Acento personalizado", SwatchColors.drop(5), s.customAccent) { c -> set { it.copy(customAccent = c) } }
            }) else emptyArray(),
        )
        GroupTitle("Detalles")
        Group(
            {
                SwitchRow(
                    "Carátula Material Expressive", s.morphingArt,
                    "Forma de galleta que gira al sonar y se vuelve cuadrada al pausar", Icons.Rounded.Cookie,
                ) { v -> set { it.copy(morphingArt = v) } }
            },
            {
                SwitchRow("Contorno", s.outline, "Borde fino del color de acento", Icons.Rounded.CropFree) { v ->
                    set { it.copy(outline = v) }
                }
            },
        )
        DemoHint()
    }
}

// ── Tamaño y posición ─────────────────────────────────────────────────

@Composable
fun SizeScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Tamaño y posición", onBack, "Los cambios se ven en vivo en la isla real") {
        GroupTitle("Calibración")
        Group(
            {
                SwitchRow(
                    "Modo calibración", s.calibration,
                    "Dibuja un borde rojo para alinear la isla con la cámara", Icons.Rounded.CenterFocusStrong,
                ) { v -> set { it.copy(calibration = v) } }
            },
            { SliderRow("Desplazamiento horizontal", s.offsetXDp, -30f..30f, ::dp) { v -> set { it.copy(offsetXDp = (v * 2).roundToInt() / 2f) } } },
            { SliderRow("Desplazamiento vertical", s.offsetYDp, -30f..30f, ::dp) { v -> set { it.copy(offsetYDp = (v * 2).roundToInt() / 2f) } } },
            {
                SliderRow("Círculo en reposo", s.restSizeAdjustDp, -6f..10f, ::dp, subtitle = "Ajuste respecto al agujero real de la cámara") { v ->
                    set { it.copy(restSizeAdjustDp = (v * 2).roundToInt() / 2f) }
                }
            },
        )
        GroupTitle("Píldora")
        Group(
            { SliderRow("Grosor", s.pillPaddingDp, 1f..14f, ::dp, subtitle = "Margen alrededor de la cámara") { v -> set { it.copy(pillPaddingDp = v.roundToInt().toFloat()) } } },
            { SliderRow("Ancho de las alas", s.wingDp, 40f..130f, ::dp) { v -> set { it.copy(wingDp = v.roundToInt().toFloat()) } } },
            { SliderRow("Ancho de los avisos del sistema", s.wideWidthDp, 220f..390f, ::dp) { v -> set { it.copy(wideWidthDp = v.roundToInt().toFloat()) } } },
        )
        GroupTitle("Tarjeta expandida")
        Group(
            { SliderRow("Margen lateral", s.expandedMarginDp, 0f..40f, ::dp) { v -> set { it.copy(expandedMarginDp = v.roundToInt().toFloat()) } } },
            { SliderRow("Radio de las esquinas", s.expandedCornerDp, 12f..60f, ::dp) { v -> set { it.copy(expandedCornerDp = v.roundToInt().toFloat()) } } },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Demo.run(Demo.Type.MEDIA) }) { Text("Mostrar música") }
            OutlinedButton(onClick = {
                val d = IslandSettings()
                set {
                    it.copy(
                        offsetXDp = d.offsetXDp, offsetYDp = d.offsetYDp, restSizeAdjustDp = d.restSizeAdjustDp,
                        pillPaddingDp = d.pillPaddingDp, wingDp = d.wingDp, wideWidthDp = d.wideWidthDp,
                        expandedMarginDp = d.expandedMarginDp, expandedCornerDp = d.expandedCornerDp,
                    )
                }
            }) { Text("Restablecer") }
        }
    }
}

// ── Animación ─────────────────────────────────────────────────────────

@Composable
fun AnimationScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Animación", onBack) {
        IslandPreview(200.dp)
        GroupTitle("Muelles")
        Group(
            {
                SliderRow(
                    "Rebote", 1.05f - s.damping, 0.05f..0.7f, { "%.0f%%".format((it - 0.05f) / 0.65f * 100) },
                    subtitle = "Cuánto se estira y rebota la isla al cambiar de forma",
                ) { v -> set { it.copy(damping = 1.05f - v) } }
            },
            {
                SliderRow("Velocidad", s.speed, 0.4f..2.5f, { "%.1f×".format(it) }) { v -> set { it.copy(speed = v) } }
            },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Demo.run(Demo.Type.CHARGING) }) { Text("Probar") }
            OutlinedButton(onClick = { set { it.copy(damping = IslandSettings().damping, speed = 1f) } }) { Text("Restablecer") }
        }
    }
}

// ── Actividades ───────────────────────────────────────────────────────

@Composable
fun ActivitiesScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Actividades", onBack) {
        GroupTitle("En curso")
        Group(
            { SwitchRow("Llamadas", s.calls, "Contestar y colgar desde la isla", Icons.Rounded.Call) { v -> set { it.copy(calls = v) } } },
            { SwitchRow("Música y vídeo", s.media, "Carátula, controles y barra ondulada", Icons.Rounded.MusicNote) { v -> set { it.copy(media = v) } } },
            { SwitchRow("Temporizadores y cronómetros", s.timers, icon = Icons.Rounded.Timer) { v -> set { it.copy(timers = v) } } },
            { SwitchRow("Navegación", s.navigation, "Siguiente giro de Maps", Icons.Rounded.Navigation) { v -> set { it.copy(navigation = v) } } },
            { SwitchRow("Descargas y progreso", s.progress, icon = Icons.Rounded.Download) { v -> set { it.copy(progress = v) } } },
            { SwitchRow("Live Updates", s.liveUpdates, "Pedidos, viajes, deportes (Android 16+)", Icons.Rounded.Bolt) { v -> set { it.copy(liveUpdates = v) } } },
            { SwitchRow("Grabaciones", s.recording, "Pantalla y voz", Icons.Rounded.FiberManualRecord) { v -> set { it.copy(recording = v) } } },
            {
                SliderRow(
                    "Música pausada", s.pausedMediaMinutes.toFloat(), 0f..30f, { if (it < 1) "Se va al pausar" else "${it.roundToInt()} min" },
                    subtitle = "Cuánto tiempo sigue en la isla tras pausar",
                ) { v -> set { it.copy(pausedMediaMinutes = v.roundToInt()) } }
            },
        )
        GroupTitle("Avisos")
        Group(
            { SegmentedRow("Notificaciones", NotificationMode.entries, s.notifications, { it.label }) { m -> set { it.copy(notifications = m) } } },
            { SwitchRow("Códigos de verificación", s.otp, "Detecta el código y lo copia con un toque", Icons.Rounded.Key) { v -> set { it.copy(otp = v) } } },
            { SwitchRow("Copiar código automáticamente", s.otpAutoCopy, icon = Icons.Rounded.ContentCopy) { v -> set { it.copy(otpAutoCopy = v) } } },
            { SwitchRow("Pagos", s.payments, "Google Wallet y similares", Icons.Rounded.CreditCard) { v -> set { it.copy(payments = v) } } },
            { SliderRow("Duración de los avisos", s.peekSeconds.toFloat(), 2f..15f, { "${it.roundToInt()} s" }) { v -> set { it.copy(peekSeconds = v.roundToInt()) } } },
        )
        GroupTitle("Sistema")
        Group(
            { SwitchRow("Carga", s.charging, icon = Icons.Rounded.BatteryChargingFull) { v -> set { it.copy(charging = v) } } },
            { SwitchRow("Batería baja", s.lowBattery, icon = Icons.Rounded.BatteryAlert) { v -> set { it.copy(lowBattery = v) } } },
            { SwitchRow("Bluetooth", s.bluetooth, icon = Icons.Rounded.Bluetooth) { v -> set { it.copy(bluetooth = v) } } },
            { SwitchRow("Modo de sonido", s.ringer, "Sonido, vibración, silencio", Icons.Rounded.Vibration) { v -> set { it.copy(ringer = v) } } },
            { SwitchRow("Modo avión", s.airplane, icon = Icons.Rounded.AirplanemodeActive) { v -> set { it.copy(airplane = v) } } },
            { SwitchRow("Auriculares con cable", s.headphones, icon = Icons.Rounded.Headphones) { v -> set { it.copy(headphones = v) } } },
            { SliderRow("Duración", s.systemEventSeconds.toFloat(), 1f..8f, { "${it.roundToInt()} s" }) { v -> set { it.copy(systemEventSeconds = v.roundToInt()) } } },
        )
    }
}

// ── Gestos ────────────────────────────────────────────────────────────

@Composable
fun GesturesScreen(onBack: () -> Unit) {
    val s = settings()
    val g = GestureAction.entries
    val i = IdleAction.entries
    SettingsScaffold("Gestos", onBack) {
        GroupTitle("Con actividad")
        Group(
            { ChoiceRow("Tocar", g, s.tap, { it.label }, Icons.Rounded.TouchApp) { v -> set { it.copy(tap = v) } } },
            { ChoiceRow("Mantener pulsado", g, s.longPress, { it.label }, Icons.Rounded.BackHand) { v -> set { it.copy(longPress = v) } } },
            { ChoiceRow("Deslizar hacia arriba", g, s.swipeUp, { it.label }, Icons.Rounded.SwipeUp) { v -> set { it.copy(swipeUp = v) } } },
            { ChoiceRow("Deslizar hacia abajo", g, s.swipeDown, { it.label }, Icons.Rounded.SwipeDown) { v -> set { it.copy(swipeDown = v) } } },
            { ChoiceRow("Deslizar de lado", g, s.swipeSideways, { it.label }, Icons.Rounded.Swipe) { v -> set { it.copy(swipeSideways = v) } } },
        )
        GroupTitle("En reposo (sobre la cámara)")
        Group(
            { ChoiceRow("Tocar la cámara", i, s.idleTap, { it.label }, Icons.Rounded.CameraFront) { v -> set { it.copy(idleTap = v) } } },
            { ChoiceRow("Mantener pulsada la cámara", i, s.idleLongPress, { it.label }, Icons.Rounded.FlashlightOn) { v -> set { it.copy(idleLongPress = v) } } },
        )
        Text(
            "Con \"Nada\" en ambos, la cámara no recibe toques y no estorba al bajar la barra de notificaciones.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ── Comportamiento ────────────────────────────────────────────────────

@Composable
fun BehaviorScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Comportamiento", onBack) {
        GroupTitle("Expandida")
        Group(
            {
                SliderRow("Contraer sola tras", s.autoCollapseSeconds.toFloat(), 0f..30f, { sec(it.roundToInt()) }) { v ->
                    set { it.copy(autoCollapseSeconds = v.roundToInt()) }
                }
            },
            { SwitchRow("Burbuja secundaria", s.showSecondary, "Segunda actividad junto a la isla; tócala para cambiar", Icons.Rounded.Circle) { v -> set { it.copy(showSecondary = v) } } },
        )
        GroupTitle("Ocultar")
        Group(
            { SwitchRow("En horizontal", s.hideInLandscape, icon = Icons.Rounded.ScreenRotation) { v -> set { it.copy(hideInLandscape = v) } } },
            { SwitchRow("En pantalla completa", s.hideInFullscreen, "Vídeos y juegos", Icons.Rounded.Fullscreen) { v -> set { it.copy(hideInFullscreen = v) } } },
            { SwitchRow("En la pantalla de bloqueo", s.hideOnLockscreen, icon = Icons.Rounded.Lock) { v -> set { it.copy(hideOnLockscreen = v) } } },
            { SwitchRow("Música si su app está abierta", s.hideMediaWhenAppOpen, icon = Icons.Rounded.MusicOff) { v -> set { it.copy(hideMediaWhenAppOpen = v) } } },
            { SwitchRow("Respetar No molestar", s.respectDnd, "Sin avisos si No molestar los bloquea", Icons.Rounded.DoNotDisturbOn) { v -> set { it.copy(respectDnd = v) } } },
        )
        GroupTitle("Sensación")
        Group(
            { SwitchRow("Vibración", s.haptics, "Toques finos al expandir, contraer y llegar algo", Icons.Rounded.Vibration) { v -> set { it.copy(haptics = v) } } },
        )
    }
}

// ── Copia de seguridad ────────────────────────────────────────────────

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = settings()
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    SettingsScaffold("Copia de seguridad", onBack) {
        Text(
            "La configuración es un JSON. Puedes copiarlo, editarlo a mano y volver a pegarlo.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
            Button(onClick = {
                cm.setPrimaryClip(ClipData.newPlainText("Material Island", SettingsStore.export()))
                Toast.makeText(ctx, "Configuración copiada", Toast.LENGTH_SHORT).show()
            }) { Text("Copiar") }
            FilledTonalButton(onClick = {
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                val ok = SettingsStore.import(text)
                Toast.makeText(ctx, if (ok) "Configuración importada" else "El portapapeles no tiene una configuración válida", Toast.LENGTH_SHORT).show()
            }) { Text("Pegar") }
            OutlinedButton(onClick = {
                SettingsStore.reset()
                Toast.makeText(ctx, "Valores por defecto", Toast.LENGTH_SHORT).show()
            }) { Text("Restablecer") }
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
            Text(
                SettingsStore.json.encodeToString(IslandSettings.serializer(), s),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

// ── Acerca de ─────────────────────────────────────────────────────────

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold("Acerca de", onBack) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Material Island 1.0", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Isla dinámica con el estilo del Pixel: Material You, formas y muelles de Material 3 Expressive.\n\n" +
                        "Sin permiso de internet: nada sale del teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        GroupTitle("Ideas tomadas de")
        Group(
            { Credit("Expressive Cutout", "Ventana de accesibilidad, zona táctil parcial, estilo Expressive") },
            { Credit("Dynamic Island for Android", "Lectura de notificaciones: llamadas, temporizadores, Live Updates") },
            { Credit("LiveBridge", "Detección de códigos de verificación") },
            { Credit("OmniLand", "Burbuja secundaria, eventos del sistema, gestos en reposo") },
            { Credit("SmartIsland", "Respuesta rápida y tarjetas expandidas") },
        )
    }
}

@Composable
private fun Credit(name: String, what: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(what, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DemoHint() {
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { Demo.run(Demo.Type.MEDIA) }) { Text("Música") }
        FilledTonalButton(onClick = { Demo.run(Demo.Type.TIMER) }) { Text("Temporizador") }
        FilledTonalButton(onClick = { Demo.run(Demo.Type.MESSAGE) }) { Text("Mensaje") }
    }
}
