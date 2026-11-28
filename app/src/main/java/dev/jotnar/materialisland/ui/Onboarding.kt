package dev.jotnar.materialisland.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.jotnar.materialisland.core.Demo
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.i18n.Text
import dev.jotnar.materialisland.service.IslandNotificationListener

/** Pasos del asistente. Los opcionales se pueden saltar; Xiaomi solo sale en Xiaomi. */
private enum class Step(val icon: ImageVector?, val title: String, val body: String, val optional: Boolean = false) {
    WELCOME(
        null, "Welcome to Material Island",
        "A dynamic island in the Pixel style. It needs a few permissions to work; this takes about a minute.",
    ),
    NOTIFICATIONS(
        Icons.Rounded.NotificationsActive, "Notification access",
        "The island reads your notifications to show music, calls, timers, rides and messages. They are read on the phone and never leave it.",
    ),
    ACCESSIBILITY(
        Icons.Rounded.Accessibility, "Accessibility service",
        "Android only lets the island draw over the status bar, around the camera, through an accessibility service. It never reads your screen.",
    ),
    BATTERY(
        Icons.Rounded.BatteryChargingFull, "Unrestricted battery",
        "Keeps Android from closing the island in the background. It uses almost no battery: it only wakes up when something happens.",
        optional = true,
    ),
    BLUETOOTH(
        Icons.Rounded.Bluetooth, "Nearby devices",
        "Optional: shows the name and battery of your headphones, speakers and laptop when they connect.",
        optional = true,
    ),
    XIAOMI(
        Icons.Rounded.RestartAlt, "Autostart (Xiaomi)",
        "HyperOS closes background apps unless Autostart is on. Turn on Material Island in the list.",
        optional = true,
    ),
    DONE(null, "All set", "Tap the island to open it. You can change everything later in the settings."),
}

/**
 * Asistente de bienvenida: un paso por permiso, con una forma Expressive que gira detrás del icono.
 * Al volver de los ajustes de Android se comprueba el permiso y, si ya está, avanza solo.
 */
@Composable
internal fun OnboardingScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val steps = remember { Step.entries.filter { it != Step.XIAOMI || isXiaomi } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var perms by remember { mutableStateOf(readPerms(ctx)) }
    var showDisclosure by remember { mutableStateOf(false) }
    val step = steps[index]

    fun granted(s: Step) = when (s) {
        Step.NOTIFICATIONS -> perms.notifications
        Step.ACCESSIBILITY -> perms.accessibility
        Step.BATTERY -> perms.battery
        Step.BLUETOOTH -> perms.bluetooth
        else -> false
    }
    fun next() { if (index < steps.lastIndex) index++ else onFinish() }

    // Al volver de Ajustes: si el permiso del paso actual ya está, se avanza.
    var waiting by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        perms = readPerms(ctx)
        if (waiting && granted(step)) { waiting = false; next() }
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        perms = readPerms(ctx); next()
    }
    BackHandler(index > 0) { index-- }

    fun act() {
        when (step) {
            Step.NOTIFICATIONS -> {
                waiting = true
                ctx.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName(ctx, IslandNotificationListener::class.java).flattenToString(),
                    ),
                )
            }
            Step.ACCESSIBILITY ->
                if (SettingsStore.current.a11yDisclosureAccepted) {
                    waiting = true; ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else showDisclosure = true
            Step.BATTERY -> {
                waiting = true
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }.onFailure { waiting = false; next() }
            }
            Step.BLUETOOTH -> btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            Step.XIAOMI -> { openXiaomiAutostart(ctx); next() }
            Step.DONE -> onFinish()
            Step.WELCOME -> next()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            // Progreso: un punto por paso; el actual se alarga.
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                steps.forEachIndexed { i, _ ->
                    val w by animateFloatAsState(if (i == index) 24f else 8f, spring(0.6f, Spring.StiffnessMediumLow), label = "dot")
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(width = w.dp, height = 8.dp).clip(CircleShape)
                            .background(if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            AnimatedContent(
                targetState = index, modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(340)) { dir * it / 3 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(260)) { -dir * it / 3 } + fadeOut(tween(160)))
                },
                label = "step",
            ) { i ->
                val s = steps[i]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    when (s) {
                        Step.WELCOME -> IslandPreview(200.dp)
                        Step.DONE -> {
                            IslandPreview(200.dp)
                            LaunchedEffect(Unit) { Demo.run(Demo.Type.MEDIA) }
                        }
                        else -> StepArt(s.icon!!, granted(s))
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(s.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        s.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (perms.restricted && (s == Step.NOTIFICATIONS || s == Step.ACCESSIBILITY) && !granted(s)) {
                        Spacer(Modifier.height(20.dp))
                        RestrictedCard { openAppInfo(ctx) }
                    }
                }
            }
            // Botones
            val done = granted(step)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    step == Step.WELCOME -> TextButton(onFinish) { Text("Skip setup") }
                    step == Step.DONE -> {}
                    done -> {}
                    else -> TextButton({ next() }) { Text(if (step.optional) "Skip" else "Later") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (done) next() else act() }) {
                    Text(
                        when {
                            step == Step.WELCOME -> "Start"
                            step == Step.DONE -> "Start using it"
                            done -> "Next"
                            step == Step.BLUETOOTH -> "Allow"
                            step == Step.XIAOMI -> "Open"
                            else -> "Turn on"
                        },
                    )
                }
            }
        }
    }

    if (showDisclosure) {
        AccessibilityDisclosure(
            onAgree = {
                showDisclosure = false
                SettingsStore.update { it.copy(a11yDisclosureAccepted = true) }
                waiting = true
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

/** Icono del paso sobre una galleta Expressive que gira despacio; al concederse pasa a un círculo con ✓. */
@Composable
private fun StepArt(icon: ImageVector, ok: Boolean) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(ok) { pop.snapTo(0.7f); pop.animateTo(1f, spring(0.45f, Spring.StiffnessLow)) }
    val shape = (if (ok) MaterialShapes.Circle else MaterialShapes.Cookie9Sided).toShape()
    Box(Modifier.size(168.dp).scale(pop.value), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(168.dp).rotate(if (ok) 0f else angle).clip(shape)
                .background(if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
        )
        Icon(
            if (ok) Icons.Rounded.Check else icon, null, modifier = Modifier.size(64.dp),
            tint = if (ok) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Spacer(Modifier.width(0.dp))
}
