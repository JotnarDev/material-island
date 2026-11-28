package dev.jotnar.materialisland.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotnar.materialisland.core.Glance
import dev.jotnar.materialisland.data.GlanceView
import dev.jotnar.materialisland.data.League
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.ui.Group
import dev.jotnar.materialisland.ui.GroupTitle
import dev.jotnar.materialisland.ui.NavRow
import dev.jotnar.materialisland.ui.SegmentedRow
import dev.jotnar.materialisland.ui.SettingsScaffold
import dev.jotnar.materialisland.ui.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val PIXEL_LAUNCHER = "com.google.android.apps.nexuslauncher"

@Composable
fun GlanceScreen(onBack: () -> Unit) {
    val s = SettingsStore.flow.collectAsStateWithLifecycle().value
    val set: ((dev.jotnar.materialisland.data.IslandSettings) -> dev.jotnar.materialisland.data.IslandSettings) -> Unit = {
        SettingsStore.update(it); Glance.refresh(force = true)
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val note = @Composable { text: String ->
        Text(
            text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    // Ubicación aproximada → nombre de la ciudad.
    fun useLocation() = scope.launch {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val loc = withContext(Dispatchers.IO) {
            runCatching {
                listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                    .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                    .maxByOrNull { it.time }
            }.getOrNull()
        }
        if (loc == null) {
            Toast.makeText(ctx, "No location yet. Search for your city instead.", Toast.LENGTH_LONG).show(); return@launch
        }
        val name = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()
        } ?: "Current location"
        // Se guarda con 2 decimales (~1 km): suficiente para el clima.
        set { it.copy(glanceLat = "%.2f".format(Locale.US, loc.latitude).toDouble(), glanceLon = "%.2f".format(Locale.US, loc.longitude).toDouble(), glancePlace = name) }
    }
    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "Without calendar access this view stays empty.", Toast.LENGTH_SHORT).show()
        Glance.refresh()
    }
    fun calAllowed() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) useLocation() else Toast.makeText(ctx, "Search for your city instead.", Toast.LENGTH_SHORT).show()
    }

    SettingsScaffold("At a glance", onBack) {
        Group(
            {
                SwitchRow(
                    "Show At a glance", s.glanceEnabled,
                    "Icon on the left of the camera. Tap it for a card you can swipe.", Icons.Rounded.AutoAwesome,
                ) { on -> set { it.copy(glanceEnabled = on) } }
            },
        )
        note(
            "This is the only part of Material Island that uses the internet: weather from Open-Meteo and scores from ESPN. " +
                "No account, no tracking. Without a connection only this card pauses; the rest of the island keeps working. " +
                "The calendar is read on the phone, from the Google Calendar accounts already on it, and never leaves it.",
        )

        if (s.glanceEnabled) {
            GroupTitle("Views")
            note("Swipe the icon next to the camera to move between the views that are on; tap it to open that view below.")
            var city by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            fun search() {
                if (city.isBlank() || busy) return
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Glance.searchCity(city) }
                    busy = false
                    if (r == null) Toast.makeText(ctx, "City not found (or no internet).", Toast.LENGTH_SHORT).show()
                    else { set { it.copy(glanceLat = r.second, glanceLon = r.third, glancePlace = r.first) }; city = "" }
                }
            }
            GlanceView.entries.forEach { v ->
                val on = v in s.glanceViews
                val toggle = @Composable {
                    SwitchRow(v.label, on, viewSubtitle(v), viewIcon(v)) { enable ->
                        if (enable && v == GlanceView.CALENDAR && !calAllowed()) calLauncher.launch(Manifest.permission.READ_CALENDAR)
                        set { st ->
                            val next = if (enable) st.glanceViews + v else st.glanceViews - v
                            st.copy(glanceViews = GlanceView.entries.filter { it in next })
                        }
                    }
                }
                // Las opciones de cada vista van justo debajo, solo si está activa.
                val options: List<@Composable () -> Unit> = if (!on) emptyList() else when (v) {
                    GlanceView.WEATHER -> listOf(
                        {
                            NavRow(
                                if (s.glanceLat == null) "No location yet" else s.glancePlace.ifBlank { "Saved location" },
                                "Use my approximate location", Icons.Rounded.MyLocation,
                            ) {
                                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (granted) useLocation() else permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    city, { city = it }, Modifier.weight(1f), singleLine = true,
                                    placeholder = { Text("Search a city") },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { search() }),
                                )
                                TextButton(::search, enabled = city.isNotBlank() && !busy) { Text(if (busy) "…" else "Set") }
                            }
                        },
                        {
                            SegmentedRow("Units", listOf(false, true), s.glanceFahrenheit, { if (it) "°F" else "°C" }) { f ->
                                set { it.copy(glanceFahrenheit = f) }
                            }
                        },
                    )
                    GlanceView.CALENDAR -> listOf(
                        {
                            val ok = calAllowed()
                            NavRow(
                                if (ok) "Calendar access allowed" else "Allow calendar access",
                                if (ok) "Events from Google Calendar on this phone" else "Needed to show your events", Icons.Rounded.EventAvailable,
                            ) {
                                if (!ok) calLauncher.launch(Manifest.permission.READ_CALENDAR)
                                else ctx.startActivity(Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        {
                            SwitchRow("All-day events", s.glanceAllDay, "Birthdays, holidays and other all-day events", Icons.Rounded.Today) { on ->
                                set { it.copy(glanceAllDay = on) }
                            }
                        },
                    )
                    GlanceView.SPORTS -> League.entries.map { lg ->
                        @Composable {
                            SwitchRow(lg.label, lg.path in s.glanceLeagues, icon = sportIcon(lg.sport)) { en ->
                                set { st -> st.copy(glanceLeagues = if (en) st.glanceLeagues + lg.path else st.glanceLeagues - lg.path) }
                            }
                        }
                    } + listOf<@Composable () -> Unit>({
                        TextSetting(
                            "Favorite teams", s.glanceTeams, "Real Madrid, Lakers, KC",
                            "Separated by commas. With teams set, only their games show. Empty shows every game.",
                        ) { t -> set { it.copy(glanceTeams = t) } }
                    })
                    GlanceView.ALERTS -> emptyList()
                }
                Group(*(listOf(toggle) + options).toTypedArray())
                Spacer(Modifier.height(12.dp))
            }
        }

        // El At a glance del propio Pixel vive en el Pixel Launcher; otra app no puede apagarlo,
        // así que se abren sus ajustes. Solo en Pixel.
        if (Build.MANUFACTURER.equals("Google", ignoreCase = true) && isInstalled(ctx, PIXEL_LAUNCHER)) {
            GroupTitle("Pixel At a glance")
            Group(
                {
                    NavRow("Turn Pixel At a glance on or off", "Opens Home settings › At a glance", Icons.Rounded.Widgets) {
                        val i = Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(PIXEL_LAUNCHER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val fallback = Intent().setComponent(ComponentName(PIXEL_LAUNCHER, "com.android.launcher3.settings.SettingsActivity"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(i) }.recoverCatching { ctx.startActivity(fallback) }
                            .onFailure { Toast.makeText(ctx, "Couldn't open Pixel Launcher settings.", Toast.LENGTH_SHORT).show() }
                    }
                },
            )
            note(
                "Android doesn't let other apps switch it off, so this opens the Pixel Launcher page where you can. " +
                    "Turn it off there to keep only the island's version; turn it back on any time.",
            )
        }
    }
}

@Composable
private fun TextSetting(title: String, value: String, hint: String, help: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), singleLine = true,
                label = { Text(title) }, placeholder = { Text(hint) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text.trim()) }),
            )
            TextButton({ onSave(text.trim()) }, enabled = text.trim() != value) { Text("Save") }
        }
        Text(
            help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun viewSubtitle(v: GlanceView) = when (v) {
    GlanceView.WEATHER -> "Now, high and low, next hours"
    GlanceView.CALENDAR -> "Your next event from Google Calendar, with a join button"
    GlanceView.SPORTS -> "Live scores and upcoming games"
    GlanceView.ALERTS -> "Next event, alarm, rain, UV, live games"
}

private fun isInstalled(ctx: android.content.Context, pkg: String) =
    runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

private fun viewIcon(v: GlanceView) = when (v) {
    GlanceView.WEATHER -> Icons.Rounded.WbSunny
    GlanceView.CALENDAR -> Icons.Rounded.CalendarToday
    GlanceView.SPORTS -> Icons.Rounded.EmojiEvents
    GlanceView.ALERTS -> Icons.Rounded.NotificationsActive
}

private fun sportIcon(sport: String) = when (sport) {
    "soccer" -> Icons.Rounded.SportsSoccer
    "basketball" -> Icons.Rounded.SportsBasketball
    "football" -> Icons.Rounded.SportsFootball
    "baseball" -> Icons.Rounded.SportsBaseball
    else -> Icons.Rounded.SportsHockey
}
