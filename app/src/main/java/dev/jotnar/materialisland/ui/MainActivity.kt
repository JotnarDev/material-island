package dev.jotnar.materialisland.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Lock
import android.content.pm.PackageInstaller
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotnar.materialisland.core.Actions
import dev.jotnar.materialisland.core.Demo
import dev.jotnar.materialisland.core.IslandStore
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.model.IslandAction
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.overlay.IslandCallbacks
import dev.jotnar.materialisland.overlay.IslandUi
import dev.jotnar.materialisland.overlay.IslandView
import dev.jotnar.materialisland.overlay.MediaCmd
import dev.jotnar.materialisland.overlay.Mode
import dev.jotnar.materialisland.overlay.computeIslandGeometry
import dev.jotnar.materialisland.service.IslandAccessibilityService
import dev.jotnar.materialisland.service.IslandNotificationListener
import dev.jotnar.materialisland.ui.screens.AboutScreen
import dev.jotnar.materialisland.ui.screens.ActivitiesScreen
import dev.jotnar.materialisland.ui.screens.AnimationScreen
import dev.jotnar.materialisland.ui.screens.AppearanceScreen
import dev.jotnar.materialisland.ui.screens.AppsScreen
import dev.jotnar.materialisland.ui.screens.BackupScreen
import dev.jotnar.materialisland.ui.screens.BehaviorScreen
import dev.jotnar.materialisland.ui.screens.GesturesScreen
import dev.jotnar.materialisland.ui.screens.SizeScreen
import dev.jotnar.materialisland.ui.theme.AppTheme

enum class Screen { HOME, APPEARANCE, SIZE, ANIMATION, ACTIVITIES, GESTURES, BEHAVIOR, APPS, GLANCE, BACKUP, ABOUT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Solo hace algo en la versión de Play (consentimiento y anuncios de las pantallas de ajustes).
        dev.jotnar.materialisland.ads.Ads.init(this)
        setContent { AppTheme { App() } }
    }
}

@Composable
private fun App() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(screen != Screen.HOME) { screen = Screen.HOME }
    val back = { screen = Screen.HOME }
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState != Screen.HOME) {
                (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(220))) togetherWith fadeOut(tween(120))
            } else {
                fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(180)))
            }
        },
        label = "nav",
    ) { s ->
        when (s) {
            Screen.HOME -> HomeScreen { screen = it }
            Screen.APPEARANCE -> AppearanceScreen(back)
            Screen.SIZE -> SizeScreen(back)
            Screen.ANIMATION -> AnimationScreen(back)
            Screen.ACTIVITIES -> ActivitiesScreen(back)
            Screen.GESTURES -> GesturesScreen(back)
            Screen.BEHAVIOR -> BehaviorScreen(back)
            Screen.APPS -> AppsScreen(back)
            Screen.GLANCE -> dev.jotnar.materialisland.ui.screens.GlanceScreen(back)
            Screen.BACKUP -> BackupScreen(back)
            Screen.ABOUT -> AboutScreen(back)
        }
    }
}

// ── Permisos ──────────────────────────────────────────────────────────

private data class Perms(
    val notifications: Boolean,
    val accessibility: Boolean,
    val battery: Boolean,
    val bluetooth: Boolean,
    /** Android 13+ bloquea accesibilidad y lectura de notificaciones en apps instaladas desde una APK. */
    val restricted: Boolean,
)

private val isXiaomi = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

/**
 * ¿Está activo el bloqueo de "ajuste restringido"? Se lee la operación oculta
 * android:access_restricted_settings; si el sistema no deja leerla, se supone bloqueada cuando
 * la app no vino de una tienda (lo que hace Android para decidir si la restringe).
 */
private fun restrictedSettingsBlocked(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val mode = runCatching {
        ctx.getSystemService(android.app.AppOpsManager::class.java)
            .unsafeCheckOpNoThrow("android:access_restricted_settings", android.os.Process.myUid(), ctx.packageName)
    }.getOrNull()
    if (mode != null) return mode != android.app.AppOpsManager.MODE_ALLOWED && mode != android.app.AppOpsManager.MODE_DEFAULT
    val installer = runCatching { ctx.packageManager.getInstallSourceInfo(ctx.packageName).packageSource }.getOrNull()
    return installer != PackageInstaller.PACKAGE_SOURCE_STORE
}

/** Abre la pantalla de "Inicio automático" de MIUI/HyperOS; si no existe, la info de la app. */
private fun openXiaomiAutostart(ctx: Context) {
    val autostart = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(autostart) }.onFailure { openAppInfo(ctx) }
}

private fun openAppInfo(ctx: Context) = ctx.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
)

private fun readPerms(ctx: Context): Perms {
    val listeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
    val a11y = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val pm = ctx.getSystemService(PowerManager::class.java)
    val notifications = listeners.contains(ctx.packageName + "/")
    val accessibility = a11y.contains(ctx.packageName + "/")
    return Perms(
        notifications = notifications,
        accessibility = accessibility,
        battery = pm.isIgnoringBatteryOptimizations(ctx.packageName),
        bluetooth = ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED,
        restricted = !(notifications && accessibility) && restrictedSettingsBlocked(ctx),
    )
}

// ── Inicio ────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(readPerms(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = readPerms(ctx) }
    val running by IslandAccessibilityService.running.collectAsStateWithLifecycle()
    val listening by IslandNotificationListener.connected.collectAsStateWithLifecycle()
    var showDisclosure by remember { mutableStateOf(false) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perms = readPerms(ctx) }

    SettingsScaffold(
        title = "Material Island",
        subtitle = if (running && listening) "Active" else "Setup needed",
        onBack = null,
    ) {
        IslandPreview()
        Spacer(Modifier.height(12.dp))
        DemoChips()

        // Versión nueva en GitHub: solo si el usuario lo eligió (se le pregunta una vez). En Play y F-Droid actualiza la tienda.
        val st = dev.jotnar.materialisland.data.SettingsStore.flow.collectAsStateWithLifecycle().value
        if (dev.jotnar.materialisland.BuildConfig.SELF_UPDATE && !st.updateOptInAsked) {
            Spacer(Modifier.height(12.dp))
            UpdateOptInCard()
        }
        val auto = dev.jotnar.materialisland.BuildConfig.SELF_UPDATE && st.updateOptInAsked && st.autoUpdateCheck
        androidx.compose.runtime.LaunchedEffect(auto) { if (auto) dev.jotnar.materialisland.core.Updater.autoCheck(ctx) }
        val upd by dev.jotnar.materialisland.core.Updater.state.collectAsStateWithLifecycle()
        if (dev.jotnar.materialisland.BuildConfig.SELF_UPDATE) if (upd is dev.jotnar.materialisland.core.Updater.State.Available || upd is dev.jotnar.materialisland.core.Updater.State.Downloading ||
            upd is dev.jotnar.materialisland.core.Updater.State.Installing || upd is dev.jotnar.materialisland.core.Updater.State.Failed
        ) {
            Spacer(Modifier.height(12.dp))
            UpdateCard(always = false)
        }

        val allOk = perms.notifications && perms.accessibility
        if (!allOk || !perms.battery || !perms.bluetooth) {
            GroupTitle("Permissions")
            if (perms.restricted) {
                RestrictedCard { openAppInfo(ctx) }
                Spacer(Modifier.height(12.dp))
            }
            Group(
                {
                    PermRow(
                        "Notification access", "To show music, calls, timers and alerts",
                        Icons.Rounded.NotificationsActive, perms.notifications,
                    ) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                ComponentName(ctx, IslandNotificationListener::class.java).flattenToString(),
                            ),
                        )
                    }
                },
                {
                    PermRow(
                        "Accessibility service", "To draw the island over the status bar",
                        Icons.Rounded.Accessibility, perms.accessibility,
                    ) {
                        // Antes de mandar a los ajustes de accesibilidad, el aviso destacado (una vez aceptado, ya no sale).
                        if (dev.jotnar.materialisland.data.SettingsStore.current.a11yDisclosureAccepted) {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else showDisclosure = true
                    }
                },
                {
                    PermRow(
                        "Unrestricted battery", "Keeps Android from killing the island in the background",
                        Icons.Rounded.BatteryChargingFull, perms.battery,
                    ) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                        )
                    }
                },
                {
                    PermRow(
                        "Nearby devices", "Optional: your Bluetooth headphones' name when they connect",
                        Icons.Rounded.Bluetooth, perms.bluetooth,
                    ) { btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                },
                *(if (isXiaomi) arrayOf<@Composable () -> Unit>({
                    PermRow(
                        "Autostart (Xiaomi)", "HyperOS closes background apps unless Autostart is on",
                        Icons.Rounded.RestartAlt, ok = false, action = "Open",
                    ) { openXiaomiAutostart(ctx) }
                }) else emptyArray()),
            )
        }

        GroupTitle("Customize")
        Group(
            { NavRow("Appearance", "Background, colors, album art", Icons.Rounded.Palette) { go(Screen.APPEARANCE) } },
            { NavRow("Size & position", "Calibrate around the camera", Icons.Rounded.CenterFocusStrong) { go(Screen.SIZE) } },
            { NavRow("Animation", "Spring bounce and speed", Icons.Rounded.Animation) { go(Screen.ANIMATION) } },
        )
        Spacer(Modifier.height(12.dp))
        Group(
            { NavRow("Activities", "What shows up in the island", Icons.Rounded.Dashboard) { go(Screen.ACTIVITIES) } },
            { NavRow("Gestures", "Tap, hold, swipe", Icons.Rounded.TouchApp) { go(Screen.GESTURES) } },
            { NavRow("Behavior", "Hiding, auto-collapse, haptics", Icons.Rounded.Tune) { go(Screen.BEHAVIOR) } },
            { NavRow("Apps", "Choose which apps can use the island", Icons.Rounded.Apps) { go(Screen.APPS) } },
            { NavRow("At a glance", "Weather, sports, finance and alerts", Icons.Rounded.AutoAwesome) { go(Screen.GLANCE) } },
        )
        Spacer(Modifier.height(12.dp))
        Group(
            { NavRow("Backup", "Export or import your settings", Icons.Rounded.SettingsBackupRestore) { go(Screen.BACKUP) } },
            { NavRow("About", "Credits and privacy", Icons.Rounded.Info) { go(Screen.ABOUT) } },
        )
        // Versión de Play: un anuncio aquí abajo, en ajustes. Versión libre: el botón de Ko-fi en su lugar.
        Spacer(Modifier.height(16.dp))
        if (dev.jotnar.materialisland.ads.Ads.ENABLED) dev.jotnar.materialisland.ads.SettingsAd()
        else KofiCard(compact = true)
    }
    if (showDisclosure) {
        AccessibilityDisclosure(
            onAgree = {
                showDisclosure = false
                dev.jotnar.materialisland.data.SettingsStore.update { it.copy(a11yDisclosureAccepted = true) }
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

@Composable
private fun PermRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ok: Boolean,
    action: String = "Turn on",
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(if (ok) 12.dp else 20.dp))
                .background(if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (ok) Icons.Rounded.Check else icon, null,
                tint = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) { Text(action) }
        }
    }
}

/** Aviso cuando Android bloquea los interruptores de accesibilidad / notificaciones. */
@Composable
private fun RestrictedCard(onUnblock: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text("Android is blocking setup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Apps installed from an APK can't get accessibility or notification access until you allow it. " +
                    "Tap Unblock, then the ⋮ menu (top right) → Allow restricted settings. " +
                    if (isXiaomi) "On Xiaomi, try turning the switch on once first so the option appears." else "",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onUnblock, modifier = Modifier.align(Alignment.End)) { Text("Unblock") }
        }
    }
}

// ── Vista previa ──────────────────────────────────────────────────────

/** Réplica de la isla real dentro de un "teléfono" de mentira; refleja lo mismo que el overlay. */
@Composable
fun IslandPreview(height: androidx.compose.ui.unit.Dp = 250.dp) {
    val ctx = LocalContext.current
    val state by IslandStore.state.collectAsStateWithLifecycle()
    val unread by IslandStore.unread.collectAsStateWithLifecycle()
    val settings by SettingsStore.flow.collectAsStateWithLifecycle()
    var expandedId by remember { mutableStateOf<String?>(null) }
    val realGeo = remember { computeIslandGeometry(ctx) }
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(36.dp))
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer))),
    ) {
        val geo = realGeo.copy(widthDp = maxWidth.value, holeCenterXDp = maxWidth.value / 2f, holeCenterYDp = 24f)
        // Cámara "de mentira" (en el teléfono real, la isla en reposo la tapa exactamente).
        Box(
            Modifier.padding(top = (24f - geo.holeDiameterDp / 2).dp).align(Alignment.TopCenter)
                .size(geo.holeDiameterDp.dp).clip(RoundedCornerShape(50)).background(Color.Black),
        )
        Text(
            "Preview", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
        val primary = state.primary
        val ui = IslandUi(state, expanded = expandedId != null && expandedId == primary?.id, hidden = false, unread = unread)
        IslandView(
            ui, geo, settings.copy(offsetXDp = 0f, offsetYDp = 0f, calibration = false),
            object : IslandCallbacks {
                override fun onTap(item: IslandItem?, mode: Mode) {
                    when (mode) {
                        Mode.COMPACT, Mode.PEEK -> expandedId = item?.id
                        Mode.EXPANDED -> expandedId = null
                        Mode.WIDE -> IslandStore.dismissTransient(item?.id)
                        else -> Unit
                    }
                }
                override fun onAction(item: IslandItem, action: IslandAction) {
                    Actions.run(ctx, action)
                    if (!item.kind.persistent) IslandStore.dismissTransient(item.id)
                }
                override fun onMedia(item: IslandItem, cmd: MediaCmd) {
                    val c = item.media?.controller?.transportControls
                    if (c == null) { if (cmd == MediaCmd.PLAY_PAUSE) Demo.toggleDemoMedia(); return }
                    when (cmd) {
                        MediaCmd.PLAY_PAUSE -> if (item.media.playing) c.pause() else c.play()
                        MediaCmd.NEXT -> c.skipToNext()
                        MediaCmd.PREV -> c.skipToPrevious()
                    }
                }
                override fun onSecondaryTap() = IslandStore.cycle()
            },
        )
    }
}

@Composable
private fun DemoChips() {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(16.dp)) {
            Text("Try the island", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "Launch test activities: they show up here and on the real island. Tap the island to expand it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Demo.Type.entries.forEach { t ->
                    FilledTonalButton(onClick = { Demo.run(t) }) { Text(t.label) }
                }
                OutlinedButton(onClick = { Demo.clear() }) {
                    Icon(Icons.Rounded.ClearAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Clear")
                }
            }
        }
    }
}
