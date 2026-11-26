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
import androidx.compose.runtime.remember
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
import dev.jotnar.materialisland.data.OpenStyle
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
private fun sec(v: Int, zero: String = "Never") = if (v == 0) zero else "$v s"

// ── Apariencia ────────────────────────────────────────────────────────

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Appearance", onBack) {
        IslandPreview(200.dp)
        GroupTitle("Background")
        Group(
            {
                SegmentedRow("Background color", BackgroundMode.entries, s.background, { it.label }) { m ->
                    set { it.copy(background = m) }
                }
            },
            *if (s.background == BackgroundMode.CUSTOM) arrayOf<@Composable () -> Unit>({
                SwatchRow("Custom background", SwatchColors.take(5), s.customBackground) { c -> set { it.copy(customBackground = c) } }
            }) else emptyArray(),
            {
                SwitchRow(
                    "Album art tint", s.albumTint,
                    "The music card takes the album's color", Icons.Rounded.Album,
                ) { v -> set { it.copy(albumTint = v) } }
            },
        )
        GroupTitle("Accent")
        Group(
            {
                SegmentedRow("Accent color", AccentMode.entries, s.accent, { it.label }) { m -> set { it.copy(accent = m) } }
            },
            *if (s.accent == AccentMode.CUSTOM) arrayOf<@Composable () -> Unit>({
                SwatchRow("Custom accent", SwatchColors.drop(5), s.customAccent) { c -> set { it.copy(customAccent = c) } }
            }) else emptyArray(),
        )
        GroupTitle("Details")
        Group(
            {
                SwitchRow(
                    "Material Expressive album art", s.morphingArt,
                    "Cookie shape that spins while playing and turns square when paused", Icons.Rounded.Cookie,
                ) { v -> set { it.copy(morphingArt = v) } }
            },
            {
                SwitchRow("Outline", s.outline, "Thin border in the accent color", Icons.Rounded.CropFree) { v ->
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
    SettingsScaffold("Size & position", onBack, "Changes show live on the real island") {
        GroupTitle("Calibration")
        Group(
            {
                SwitchRow(
                    "Calibration mode", s.calibration,
                    "Draws a red outline to line the island up with the camera", Icons.Rounded.CenterFocusStrong,
                ) { v -> set { it.copy(calibration = v) } }
            },
            { SliderRow("Horizontal offset", s.offsetXDp, -30f..30f, ::dp) { v -> set { it.copy(offsetXDp = (v * 2).roundToInt() / 2f) } } },
            { SliderRow("Vertical offset", s.offsetYDp, -30f..30f, ::dp) { v -> set { it.copy(offsetYDp = (v * 2).roundToInt() / 2f) } } },
            {
                SliderRow("Resting circle", s.restSizeAdjustDp, -6f..10f, ::dp, subtitle = "Adjustment relative to the real camera hole") { v ->
                    set { it.copy(restSizeAdjustDp = (v * 2).roundToInt() / 2f) }
                }
            },
        )
        GroupTitle("Pill")
        Group(
            { SliderRow("Thickness", s.pillPaddingDp, 1f..14f, ::dp, subtitle = "Margin around the camera") { v -> set { it.copy(pillPaddingDp = v.roundToInt().toFloat()) } } },
            { SliderRow("Wing width", s.wingDp, 40f..130f, ::dp) { v -> set { it.copy(wingDp = v.roundToInt().toFloat()) } } },
            { SliderRow("System alert width", s.wideWidthDp, 220f..390f, ::dp) { v -> set { it.copy(wideWidthDp = v.roundToInt().toFloat()) } } },
        )
        GroupTitle("Expanded card")
        Group(
            { SliderRow("Side margin", s.expandedMarginDp, 0f..40f, ::dp) { v -> set { it.copy(expandedMarginDp = v.roundToInt().toFloat()) } } },
            { SliderRow("Corner radius", s.expandedCornerDp, 12f..60f, ::dp) { v -> set { it.copy(expandedCornerDp = v.roundToInt().toFloat()) } } },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Demo.run(Demo.Type.MEDIA) }) { Text("Show music") }
            OutlinedButton(onClick = {
                val d = IslandSettings()
                set {
                    it.copy(
                        offsetXDp = d.offsetXDp, offsetYDp = d.offsetYDp, restSizeAdjustDp = d.restSizeAdjustDp,
                        pillPaddingDp = d.pillPaddingDp, wingDp = d.wingDp, wideWidthDp = d.wideWidthDp,
                        expandedMarginDp = d.expandedMarginDp, expandedCornerDp = d.expandedCornerDp,
                    )
                }
            }) { Text("Reset") }
        }
    }
}

// ── Animación ─────────────────────────────────────────────────────────

@Composable
fun AnimationScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Animation", onBack) {
        IslandPreview(200.dp)
        GroupTitle("Open style")
        Group(
            {
                SegmentedRow("How the island leaves the camera", OpenStyle.entries, s.openStyle, { it.label }) { v ->
                    set { it.copy(openStyle = v) }
                }
            },
            *(if (s.openStyle == OpenStyle.DROP) arrayOf<@Composable () -> Unit>({
                SliderRow(
                    "Drop distance", s.dropGapDp, 2f..24f, { "${it.roundToInt()} dp" },
                    subtitle = "Gap between the camera and the island",
                ) { v -> set { it.copy(dropGapDp = v) } }
            }) else emptyArray()),
        )
        Text(
            if (s.openStyle == OpenStyle.DROP) "A drop pulls away from the camera on a liquid neck, falls, bounces and opens into the island below it."
            else "The camera hole stretches into a pill, like a classic dynamic island.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        GroupTitle("Springs")
        Group(
            {
                SliderRow(
                    "Bounce", 1.05f - s.damping, 0.05f..0.7f, { "%.0f%%".format((it - 0.05f) / 0.65f * 100) },
                    subtitle = "How much the island stretches and bounces when it changes shape",
                ) { v -> set { it.copy(damping = 1.05f - v) } }
            },
            {
                SliderRow("Speed", s.speed, 0.4f..2.5f, { "%.1f×".format(it) }) { v -> set { it.copy(speed = v) } }
            },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Demo.run(Demo.Type.CHARGING) }) { Text("Try it") }
            OutlinedButton(onClick = { set { it.copy(damping = IslandSettings().damping, speed = 1f) } }) { Text("Reset") }
        }
    }
}

// ── Actividades ───────────────────────────────────────────────────────

@Composable
fun ActivitiesScreen(onBack: () -> Unit) {
    val s = settings()
    SettingsScaffold("Activities", onBack) {
        GroupTitle("Ongoing")
        Group(
            { SwitchRow("Calls", s.calls, "Answer and hang up from the island", Icons.Rounded.Call) { v -> set { it.copy(calls = v) } } },
            { SwitchRow("Music & video", s.media, "Album art, controls and wavy seek bar", Icons.Rounded.MusicNote) { v -> set { it.copy(media = v) } } },
            { SwitchRow("Timers & stopwatches", s.timers, icon = Icons.Rounded.Timer) { v -> set { it.copy(timers = v) } } },
            { SwitchRow("Navigation", s.navigation, "Next turn from Maps", Icons.Rounded.Navigation) { v -> set { it.copy(navigation = v) } } },
            { SwitchRow("Downloads & progress", s.progress, icon = Icons.Rounded.Download) { v -> set { it.copy(progress = v) } } },
            { SwitchRow("Live Updates", s.liveUpdates, "Orders, rides, sports (Android 16+)", Icons.Rounded.Bolt) { v -> set { it.copy(liveUpdates = v) } } },
            { SwitchRow("Recordings", s.recording, "Screen and voice", Icons.Rounded.FiberManualRecord) { v -> set { it.copy(recording = v) } } },
            {
                SliderRow(
                    "Paused music", s.pausedMediaMinutes.toFloat(), 0f..30f, { if (it < 1) "Leaves on pause" else "${it.roundToInt()} min" },
                    subtitle = "How long it stays in the island after pausing",
                ) { v -> set { it.copy(pausedMediaMinutes = v.roundToInt()) } }
            },
        )
        GroupTitle("Unread messages")
        Group(
            {
                SwitchRow(
                    "Message bubbles", s.unreadBubbles,
                    "Unread chats appear as bubbles left of the island. Tap to read and reply, hold to open the app.",
                    Icons.Rounded.Forum,
                ) { v -> set { it.copy(unreadBubbles = v) } }
            },
            {
                SliderRow("Max bubbles", s.maxBubbles.toFloat(), 1f..5f, { "${it.roundToInt()}" }, steps = 3) { v ->
                    set { it.copy(maxBubbles = v.roundToInt()) }
                }
            },
        )
        Text(
            "To hide Android's own notification icons from the status bar (they come back after a reboot):\n" +
                "adb shell cmd statusbar send-disable-flag notification-icons",
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        GroupTitle("Alerts")
        Group(
            { SegmentedRow("Notifications", NotificationMode.entries, s.notifications, { it.label }) { m -> set { it.copy(notifications = m) } } },
            { SwitchRow("Verification codes", s.otp, "Detects the code and copies it with one tap", Icons.Rounded.Key) { v -> set { it.copy(otp = v) } } },
            { SwitchRow("Copy code automatically", s.otpAutoCopy, icon = Icons.Rounded.ContentCopy) { v -> set { it.copy(otpAutoCopy = v) } } },
            { SwitchRow("Payments", s.payments, "Google Wallet and similar", Icons.Rounded.CreditCard) { v -> set { it.copy(payments = v) } } },
            { SliderRow("Alert duration", s.peekSeconds.toFloat(), 2f..15f, { "${it.roundToInt()} s" }) { v -> set { it.copy(peekSeconds = v.roundToInt()) } } },
        )
        GroupTitle("System")
        Group(
            { SwitchRow("Charging", s.charging, icon = Icons.Rounded.BatteryChargingFull) { v -> set { it.copy(charging = v) } } },
            { SwitchRow("Low battery", s.lowBattery, icon = Icons.Rounded.BatteryAlert) { v -> set { it.copy(lowBattery = v) } } },
            { SwitchRow("Bluetooth", s.bluetooth, icon = Icons.Rounded.Bluetooth) { v -> set { it.copy(bluetooth = v) } } },
            { SwitchRow("Sound mode", s.ringer, "Sound, vibrate, silent", Icons.Rounded.Vibration) { v -> set { it.copy(ringer = v) } } },
            { SwitchRow("Airplane mode", s.airplane, icon = Icons.Rounded.AirplanemodeActive) { v -> set { it.copy(airplane = v) } } },
            { SwitchRow("Wired headphones", s.headphones, icon = Icons.Rounded.Headphones) { v -> set { it.copy(headphones = v) } } },
            { SliderRow("Duration", s.systemEventSeconds.toFloat(), 1f..8f, { "${it.roundToInt()} s" }) { v -> set { it.copy(systemEventSeconds = v.roundToInt()) } } },
        )
    }
}

// ── Gestos ────────────────────────────────────────────────────────────

@Composable
fun GesturesScreen(onBack: () -> Unit) {
    val s = settings()
    val g = GestureAction.entries
    val i = IdleAction.entries
    SettingsScaffold("Gestures", onBack) {
        GroupTitle("With an activity")
        Group(
            { ChoiceRow("Tap", g, s.tap, { it.label }, Icons.Rounded.TouchApp) { v -> set { it.copy(tap = v) } } },
            { ChoiceRow("Long press", g, s.longPress, { it.label }, Icons.Rounded.BackHand) { v -> set { it.copy(longPress = v) } } },
            { ChoiceRow("Swipe up", g, s.swipeUp, { it.label }, Icons.Rounded.SwipeUp) { v -> set { it.copy(swipeUp = v) } } },
            { ChoiceRow("Swipe down", g, s.swipeDown, { it.label }, Icons.Rounded.SwipeDown) { v -> set { it.copy(swipeDown = v) } } },
            { ChoiceRow("Swipe sideways", g, s.swipeSideways, { it.label }, Icons.Rounded.Swipe) { v -> set { it.copy(swipeSideways = v) } } },
        )
        GroupTitle("At rest (on the camera)")
        Group(
            { ChoiceRow("Tap the camera", i, s.idleTap, { it.label }, Icons.Rounded.CameraFront) { v -> set { it.copy(idleTap = v) } } },
            { ChoiceRow("Double tap the camera", i, s.idleDoubleTap, { it.label }, Icons.Rounded.TouchApp) { v -> set { it.copy(idleDoubleTap = v) } } },
            { ChoiceRow("Long press the camera", i, s.idleLongPress, { it.label }, Icons.Rounded.FlashlightOn) { v -> set { it.copy(idleLongPress = v) } } },
        )
        if (IdleAction.SHORTCUTS in listOf(s.idleTap, s.idleDoubleTap, s.idleLongPress)) {
            GroupTitle("Shortcuts card")
            val choices = IdleAction.shortcutChoices
            Group(
                *choices.map { a ->
                    @Composable {
                        SwitchRow(a.label, a in s.shortcuts) { on ->
                            set { st ->
                                // Se mantiene el orden de la lista de opciones.
                                val next = if (on) (st.shortcuts + a) else (st.shortcuts - a)
                                st.copy(shortcuts = choices.filter { it in next })
                            }
                        }
                    }
                }.toTypedArray(),
            )
            Text(
                "Shown 3 per row, in this order. Flashlight and Sound mode stay open so you can see the change.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Text(
            "With \"Nothing\" on all three, the camera ignores touches and never gets in the way of pulling down the shade. " +
                "Double tap makes a single tap wait a moment to tell them apart.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ── Comportamiento ────────────────────────────────────────────────────

@Composable
fun BehaviorScreen(onBack: () -> Unit) {
    val s = settings()
    val ctx = LocalContext.current
    val canWrite = remember { dev.jotnar.materialisland.core.SystemHeadsUp.canWrite(ctx) }
    SettingsScaffold("Behavior", onBack) {
        GroupTitle("System notifications")
        Group(
            {
                SwitchRow(
                    "Island first", s.islandIsPrimary && canWrite,
                    if (canWrite) "Turns off the system's pop-up notifications. They stay in the status bar and the shade. If the island stops, they come back on their own."
                    else "Needs a permission that can only be granted over ADB (see below)",
                    Icons.Rounded.Star,
                ) { v -> if (canWrite) set { it.copy(islandIsPrimary = v) } }
            },
            {
                dev.jotnar.materialisland.ui.NavRow("Send test notification", "To check it doesn't show up twice", Icons.Rounded.Send) {
                    dev.jotnar.materialisland.core.SystemHeadsUp.postTest(ctx)
                }
            },
        )
        if (!canWrite) Text(
            "adb shell pm grant dev.jotnar.materialisland android.permission.WRITE_SECURE_SETTINGS",
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        GroupTitle("Expanded")
        Group(
            {
                SliderRow("Auto-collapse after", s.autoCollapseSeconds.toFloat(), 0f..30f, { sec(it.roundToInt()) }) { v ->
                    set { it.copy(autoCollapseSeconds = v.roundToInt()) }
                }
            },
            { SwitchRow("Secondary bubble", s.showSecondary, "Second activity next to the island; tap it to switch", Icons.Rounded.Circle) { v -> set { it.copy(showSecondary = v) } } },
        )
        GroupTitle("Hide")
        Group(
            { SwitchRow("In landscape", s.hideInLandscape, icon = Icons.Rounded.ScreenRotation) { v -> set { it.copy(hideInLandscape = v) } } },
            { SwitchRow("In fullscreen", s.hideInFullscreen, "Videos and games", Icons.Rounded.Fullscreen) { v -> set { it.copy(hideInFullscreen = v) } } },
            { SwitchRow("On the lock screen", s.hideOnLockscreen, icon = Icons.Rounded.Lock) { v -> set { it.copy(hideOnLockscreen = v) } } },
            { SwitchRow("Music while its app is open", s.hideMediaWhenAppOpen, icon = Icons.Rounded.MusicOff) { v -> set { it.copy(hideMediaWhenAppOpen = v) } } },
            { SwitchRow("Respect Do Not Disturb", s.respectDnd, "No alerts when Do Not Disturb blocks them", Icons.Rounded.DoNotDisturbOn) { v -> set { it.copy(respectDnd = v) } } },
        )
        GroupTitle("Feel")
        Group(
            { SwitchRow("Vibrate", s.haptics, "Subtle taps when expanding, collapsing and when something arrives", Icons.Rounded.Vibration) { v -> set { it.copy(haptics = v) } } },
        )
    }
}

// ── Copia de seguridad ────────────────────────────────────────────────

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = settings()
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    SettingsScaffold("Backup", onBack) {
        Text(
            "Your settings are a JSON file. You can copy it, edit it by hand and paste it back.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
            Button(onClick = {
                cm.setPrimaryClip(ClipData.newPlainText("Material Island", SettingsStore.export()))
                Toast.makeText(ctx, "Settings copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
            FilledTonalButton(onClick = {
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                val ok = SettingsStore.import(text)
                Toast.makeText(ctx, if (ok) "Settings imported" else "The clipboard doesn't contain valid settings", Toast.LENGTH_SHORT).show()
            }) { Text("Paste") }
            OutlinedButton(onClick = {
                SettingsStore.reset()
                Toast.makeText(ctx, "Defaults", Toast.LENGTH_SHORT).show()
            }) { Text("Reset") }
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
    SettingsScaffold("About", onBack) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Material Island 1.0", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "A dynamic island in the Pixel style: Material You, Material 3 Expressive shapes and springs.\n\n" +
                        "No internet permission: nothing leaves the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        GroupTitle("Ideas borrowed from")
        Group(
            { Credit("Expressive Cutout", "Accessibility window, partial touch region, Expressive style") },
            { Credit("Dynamic Island for Android", "Notification parsing: calls, timers, Live Updates") },
            { Credit("LiveBridge", "Verification code detection") },
            { Credit("OmniLand", "Secondary bubble, system events, resting gestures") },
            { Credit("SmartIsland", "Quick reply and expanded cards") },
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
        FilledTonalButton(onClick = { Demo.run(Demo.Type.MEDIA) }) { Text("Music") }
        FilledTonalButton(onClick = { Demo.run(Demo.Type.TIMER) }) { Text("Timer") }
        FilledTonalButton(onClick = { Demo.run(Demo.Type.MESSAGE) }) { Text("Message") }
    }
}
