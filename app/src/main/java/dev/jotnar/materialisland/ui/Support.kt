package dev.jotnar.materialisland.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.jotnar.materialisland.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

const val KOFI_URL = "https://ko-fi.com/jotnardev"
private val KofiRed = Color(0xFFFF5E5B)

/** Botón de Ko-fi: apoyo voluntario. En la versión libre ocupa el sitio de los anuncios. */
@Composable
fun KofiCard(compact: Boolean = false) {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            if (!compact) {
                Text("Support Material Island", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every feature is free and the island will never show ads. A coffee helps pay for the Google Play fee and keeps the updates coming.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (compact) {
                    Text(
                        "Enjoying the island?", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    colors = ButtonDefaults.buttonColors(containerColor = KofiRed, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.LocalCafe, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Support on Ko-fi")
                }
            }
        }
    }
}

/**
 * Aviso destacado antes de activar el servicio de accesibilidad (lo exige Google Play para apps que no son
 * herramientas de accesibilidad): una pantalla propia que dice para qué se usa, qué no hace y pide aceptar.
 */
@Composable
fun AccessibilityDisclosure(onAgree: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(32.dp), modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Icon(Icons.Rounded.Accessibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(14.dp))
                Text("Material Island uses the Accessibility API", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Material Island is not an accessibility tool. It uses Android's AccessibilityService API only to show the dynamic island:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Point(Icons.Rounded.Check, "Draw the island over the status bar, around the camera.")
                Point(Icons.Rounded.Check, "Know which app is in the foreground, to hide a music or call card while that app is open.")
                Spacer(Modifier.height(4.dp))
                Point(Icons.Rounded.Block, "It never reads what's on your screen, what you type or your passwords.")
                Point(Icons.Rounded.Block, "It doesn't collect, store or share any of this data, and nothing from it leaves your phone.")
                Spacer(Modifier.height(12.dp))
                Text(
                    "You can turn the service off at any time in Android's accessibility settings. Without it, the island can't be shown.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onDismiss) { Text("No thanks") }
                    Spacer(Modifier.width(8.dp))
                    Button(onAgree) { Text("Agree and continue") }
                }
            }
        }
    }
}

@Composable
private fun Point(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Icon(
            icon, null, modifier = Modifier.size(18.dp).padding(top = 2.dp),
            tint = if (icon == Icons.Rounded.Check) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
