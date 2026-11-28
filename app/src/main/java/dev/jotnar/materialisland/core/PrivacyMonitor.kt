package dev.jotnar.materialisland.core

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind

/**
 * Punto de privacidad: avisa en la isla mientras alguna app usa la cámara o el micrófono.
 *
 * Android deja saber a cualquier app CUÁNDO están en uso (cámara no disponible, grabaciones de audio
 * activas), pero no QUÉ app las usa: eso solo lo sabe el sistema. Por eso se indica la app que estaba
 * en pantalla cuando empezó, como pista, no como certeza.
 */
class PrivacyMonitor(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val cameras = ctx.getSystemService(CameraManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)

    private val busyCameras = HashSet<String>()
    private var micOn = false
    private var startedWith: String? = null

    private val cameraCb = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) { busyCameras.add(cameraId); update() }
        override fun onCameraAvailable(cameraId: String) { busyCameras.remove(cameraId); update() }
    }

    private val audioCb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            micOn = configs.any { it.clientAudioSource != HOTWORD && !it.isClientSilenced }
            update()
        }
    }

    fun start() {
        runCatching { cameras?.registerAvailabilityCallback(cameraCb, main) }
        runCatching {
            audio?.registerAudioRecordingCallback(audioCb, main)
            micOn = audio?.activeRecordingConfigurations?.any { it.clientAudioSource != HOTWORD && !it.isClientSilenced } == true
        }
        update()
    }

    fun stop() {
        runCatching { cameras?.unregisterAvailabilityCallback(cameraCb) }
        runCatching { audio?.unregisterAudioRecordingCallback(audioCb) }
        IslandStore.remove(ID)
    }

    private fun update() {
        val cam = busyCameras.isNotEmpty()
        if (!SettingsStore.current.privacyIndicator || (!cam && !micOn)) {
            startedWith = null
            IslandStore.remove(ID)
            return
        }
        if (startedWith == null) startedWith = IslandStore.foregroundPkg.value
        val app = startedWith?.let { NotificationParser.appLabel(ctx, it) }
        IslandStore.upsert(item(cam, micOn, app, settingsIntent(ctx)))
    }

    /** Volver a mirar (al cambiar el ajuste). */
    fun refresh() = update()

    companion object {
        const val ID = "sys:privacy"
        /** MediaRecorder.AudioSource.HOTWORD (oculta): "Ok Google" escuchando, no es una grabación. */
        private const val HOTWORD = 1999

        private fun settingsIntent(ctx: Context): android.app.PendingIntent = android.app.PendingIntent.getActivity(
            ctx, 77, android.content.Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        fun item(cam: Boolean, mic: Boolean, app: String?, settings: android.app.PendingIntent? = null, onClose: (() -> Unit)? = null): IslandItem {
            val what = when {
                cam && mic -> "Camera and microphone"
                cam -> "Camera"
                else -> "Microphone"
            }
            return IslandItem(
                id = ID, kind = Kind.PRIVACY, title = "$what in use",
                text = app?.let { "Started while $it was open" },
                compactText = when { cam && mic -> "Cam · Mic"; cam -> "Camera"; else -> "Mic" },
                privacy = dev.jotnar.materialisland.model.PrivacyInfo(cam, mic),
                actions = listOf(
                    if (onClose != null) dev.jotnar.materialisland.model.IslandAction("Close", null, onClick = onClose)
                    else dev.jotnar.materialisland.model.IslandAction("Privacy settings", settings),
                ),
            )
        }
    }
}
