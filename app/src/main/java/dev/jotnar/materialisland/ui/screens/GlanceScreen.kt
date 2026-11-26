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
            "This is the only part of Material Island that uses the internet: weather from Open-Meteo, scores from ESPN and " +
                "quotes from Yahoo Finance. No account, no tracking. Without a connection only this card pauses; the rest of the island keeps working.",
        )

        if (s.glanceEnabled) {
            GroupTitle("Views")
            Group(
                *GlanceView.entries.map { v ->
                    @Composable {
                        SwitchRow(v.label, v in s.glanceViews, icon = viewIcon(v)) { on ->
                            set { st ->
                                val next = if (on) st.glanceViews + v else st.glanceViews - v
                                st.copy(glanceViews = GlanceView.entries.filter { it in next })
                            }
                        }
                    }
                }.toTypedArray(),
            )
            note("Swipe sideways on the card to move between them, in this order. Swipe up or down to close it.")

            if (GlanceView.WEATHER in s.glanceViews || GlanceView.ALERTS in s.glanceViews) {
                GroupTitle("Weather")
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
                Group(
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
            }

            if (GlanceView.SPORTS in s.glanceViews || GlanceView.ALERTS in s.glanceViews) {
                GroupTitle("Sports")
                Group(
                    *League.entries.map { lg ->
                        @Composable {
                            SwitchRow(lg.label, lg.path in s.glanceLeagues, icon = sportIcon(lg.sport)) { on ->
                                set { st -> st.copy(glanceLeagues = if (on) st.glanceLeagues + lg.path else st.glanceLeagues - lg.path) }
                            }
                        }
                    }.toTypedArray(),
                )
                TextSetting(
                    "Favorite teams", s.glanceTeams, "Real Madrid, Lakers, KC",
                    "Separated by commas. With teams set, only their games show; live games of your teams also light up the icon. Empty shows every game.",
                ) { v -> set { it.copy(glanceTeams = v) } }
            }

            if (GlanceView.FINANCE in s.glanceViews || GlanceView.ALERTS in s.glanceViews) {
                GroupTitle("Finance")
                TextSetting(
                    "Symbols", s.glanceSymbols, "^GSPC, AAPL, BTC-USD, EURUSD=X",
                    "Up to 4, separated by commas, as Yahoo Finance writes them: ^GSPC is the S&P 500, BTC-USD bitcoin, EURUSD=X euro to dollar. Moves of 3% or more also show in Alerts.",
                ) { v -> set { it.copy(glanceSymbols = v) } }
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
    Group(
        {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text(title) }, placeholder = { Text(hint) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text.trim()) }),
                )
                TextButton({ onSave(text.trim()) }, enabled = text.trim() != value) { Text("Save") }
            }
        },
    )
    Text(
        help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun isInstalled(ctx: android.content.Context, pkg: String) =
    runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

private fun viewIcon(v: GlanceView) = when (v) {
    GlanceView.WEATHER -> Icons.Rounded.WbSunny
    GlanceView.SPORTS -> Icons.Rounded.EmojiEvents
    GlanceView.FINANCE -> Icons.Rounded.ShowChart
    GlanceView.ALERTS -> Icons.Rounded.NotificationsActive
}

private fun sportIcon(sport: String) = when (sport) {
    "soccer" -> Icons.Rounded.SportsSoccer
    "basketball" -> Icons.Rounded.SportsBasketball
    "football" -> Icons.Rounded.SportsFootball
    "baseball" -> Icons.Rounded.SportsBaseball
    else -> Icons.Rounded.SportsHockey
}
