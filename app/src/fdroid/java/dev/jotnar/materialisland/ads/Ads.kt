package dev.jotnar.materialisland.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Versión libre: sin anuncios. En su lugar, la app muestra el botón de Ko-fi. */
object Ads {
    const val ENABLED = false
    fun init(activity: Activity) = Unit
    fun privacyOptions(activity: Activity) = Unit
    fun hasPrivacyOptions() = false
}

@Composable
fun SettingsAd(modifier: Modifier = Modifier) = Unit
