package dev.jotnar.materialisland.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotnar.materialisland.core.Updater
import kotlinx.coroutines.launch

/** Notas de la release en texto plano y cortas, para la tarjeta. */
private fun shortNotes(md: String): String = md.lines()
    .map { it.trim().removePrefix("###").removePrefix("- ").replace("**", "").replace("*", "").trim() }
    .filter { it.isNotEmpty() && !it.equals("What's new", true) }
    .take(4).joinToString("\n") { "• $it" }

/**
 * Tarjeta de actualización. Con [always] (pantalla About) se ve siempre y lleva el botón de comprobar;
 * si no (inicio), solo aparece cuando hay algo que mostrar.
 */
@Composable
fun UpdateCard(always: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(Updater.canInstall(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = Updater.canInstall(ctx) }
    val release = when (val st = state) {
        is Updater.State.Available -> st.release
        is Updater.State.Downloading -> st.release
        is Updater.State.Installing -> st.release
        is Updater.State.Failed -> st.release
        else -> null
    }
    if (!always && release == null) return

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (release != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (release != null) "Version ${release.version} is out" else "Version ${Updater.currentVersion(ctx)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            when (val st = state) {
                is Updater.State.Checking -> Text("Checking…", style = MaterialTheme.typography.bodyMedium)
                is Updater.State.UpToDate -> Text("You have the latest version.", style = MaterialTheme.typography.bodyMedium)
                is Updater.State.Idle -> Text("Updates come from the project's GitHub releases.", style = MaterialTheme.typography.bodyMedium)
                is Updater.State.Downloading -> {
                    Text("Downloading… ${(st.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    LinearWavyProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                }
                is Updater.State.Installing -> Text(
                    "Confirm the update in the Android installer. The app closes and reopens updated; your settings are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is Updater.State.Failed -> Text(st.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                is Updater.State.Available -> Text(
                    shortNotes(st.release.notes), style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    release != null && state !is Updater.State.Downloading && state !is Updater.State.Installing -> {
                        if (!canInstall) {
                            Button({
                                ctx.startActivity(
                                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text("Allow updates") }
                        } else {
                            Button({ scope.launch { Updater.downloadAndInstall(ctx, release) } }) {
                                Text(if (state is Updater.State.Failed) "Try again" else "Update")
                            }
                        }
                        TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                            Text("What's new")
                        }
                    }
                    release == null && always -> Button(
                        { scope.launch { Updater.check(ctx) } }, enabled = state !is Updater.State.Checking,
                    ) { Text("Check for updates") }
                }
            }
            if (release != null && !canInstall && state !is Updater.State.Downloading) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android asks once to let Material Island install its own updates. Turn on \"Allow from this source\" and come back.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Pregunta (una sola vez) si buscar versiones nuevas en GitHub. Las dos respuestas pesan lo mismo y
 * se explica que esas actualizaciones no pasan por las comprobaciones de ninguna tienda.
 */
@Composable
fun UpdateOptInCard() {
    fun answer(yes: Boolean) = dev.jotnar.materialisland.data.SettingsStore.update {
        it.copy(updateOptInAsked = true, autoUpdateCheck = yes)
    }
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Get updates from GitHub?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Material Island can check GitHub for a new version each time you open it. " +
                    "Updates come straight from the developer's GitHub releases, so they skip the checks of any app store. " +
                    "You can change this later in About.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(onClick = { answer(false) }, modifier = Modifier.weight(1f)) { Text("No thanks") }
                androidx.compose.material3.FilledTonalButton(onClick = { answer(true) }, modifier = Modifier.weight(1f)) { Text("Check for updates") }
            }
        }
    }
}
