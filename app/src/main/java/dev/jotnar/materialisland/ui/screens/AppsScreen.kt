package dev.jotnar.materialisland.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.ui.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val pkg: String, val label: String, val icon: Bitmap?)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by SettingsStore.flow.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != ctx.packageName }
                .map { ai ->
                    AppEntry(ai.packageName, pm.getApplicationLabel(ai).toString(), runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96) }.getOrNull())
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    SettingsScaffold("Apps", onBack, "Turn an app off so it never shows up in the island") {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search apps") },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@SettingsScaffold
        }
        val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            filtered.forEachIndexed { i, app ->
                val allowed = app.pkg !in s.blockedApps
                val big = 22.dp
                val small = 5.dp
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (i == 0) big else small, topEnd = if (i == 0) big else small,
                        bottomStart = if (i == filtered.lastIndex) big else small, bottomEnd = if (i == filtered.lastIndex) big else small,
                    ),
                    color = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle(app.pkg, allowed) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp).clip(CircleShape)) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(checked = allowed, onCheckedChange = { toggle(app.pkg, allowed) })
                    }
                }
            }
        }
    }
}

private fun toggle(pkg: String, currentlyAllowed: Boolean) = SettingsStore.update {
    it.copy(blockedApps = if (currentlyAllowed) it.blockedApps + pkg else it.blockedApps - pkg)
}
