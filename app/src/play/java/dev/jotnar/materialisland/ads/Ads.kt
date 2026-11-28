package dev.jotnar.materialisland.ads

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dev.jotnar.materialisland.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Versión de Play: un banner en las pantallas de ajustes. Nunca en la isla.
 * Primero se pide el consentimiento con la plataforma de Google (obligatorio en la UE/RU) y solo
 * después se inicializan los anuncios.
 */
object Ads {
    const val ENABLED = true
    private const val TAG = "Ads"
    private val started = AtomicBoolean(false)
    private val ready = MutableStateFlow(false)
    private var consent: ConsentInformation? = null

    fun init(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity).also { consent = it }
        info.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                    if (err != null) Log.w(TAG, "consent form: ${err.message}")
                    if (info.canRequestAds()) start(activity)
                }
            },
            { err -> Log.w(TAG, "consent update: ${err.message}") },
        )
        // Si ya había consentimiento de otra sesión, no hace falta esperar.
        if (info.canRequestAds()) start(activity)
    }

    private fun start(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(activity.applicationContext) { ready.value = true }
    }

    /** Para que el usuario cambie su elección de privacidad (lo exige el RGPD). */
    fun hasPrivacyOptions() =
        consent?.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun privacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) { }

    internal val readyFlow get() = ready
}

/**
 * Banner adaptativo dentro de un marco con el estilo de las tarjetas de ajustes y la etiqueta "Sponsored".
 * El anuncio en sí no se recorta ni se tapa (reglas de AdMob): el marco solo lo rodea.
 */
@Composable
fun SettingsAd(modifier: Modifier = Modifier) {
    val ready by Ads.readyFlow.collectAsState()
    if (!ready) return
    val ctx = LocalContext.current
    val pad = 12
    val width = LocalConfiguration.current.screenWidthDp - 32 - 2 * pad
    Surface(
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = pad.dp, end = pad.dp, top = 10.dp, bottom = pad.dp)) {
            Text(
                "Sponsored", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    AdView(ctx).apply {
                        adUnitId = BuildConfig.ADMOB_BANNER_ID
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width))
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
