package dev.jotnar.materialisland.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Fondo de la isla. */
@Serializable
enum class BackgroundMode(val label: String) {
    BLACK("AMOLED black"),
    MATERIAL("Material You"),
    CUSTOM("Custom"),
}

/** Color de acento (iconos, barras, progreso). */
@Serializable
enum class AccentMode(val label: String) {
    MATERIAL("Material You"),
    APP("App color"),
    CUSTOM("Custom"),
}

/** Gestos sobre la isla cuando muestra algo. */
@Serializable
enum class GestureAction(val label: String) {
    NONE("Nothing"),
    TOGGLE_EXPAND("Expand / collapse"),
    OPEN_APP("Open app"),
    PLAY_PAUSE("Play / pause"),
    NEXT_ACTIVITY("Switch activity"),
    DISMISS("Dismiss"),
}

/** Gestos sobre la cámara cuando la isla está en reposo. */
@Serializable
enum class IdleAction(val label: String) {
    NONE("Nothing"),
    FLASHLIGHT("Flashlight"),
    SCREENSHOT("Screenshot"),
    NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick settings"),
    LOCK_SCREEN("Lock screen"),
    OPEN_ISLAND_APP("Open Material Island"),
}

/** Qué notificaciones aparecen como aviso. */
@Serializable
enum class NotificationMode(val label: String) {
    ALL("All"),
    MESSAGES("Messages only"),
    NONE("None"),
}

/**
 * Toda la configuración de la isla. Se guarda como JSON, así que se puede exportar/importar y añadir
 * campos nuevos sin romper nada (los que falten toman su valor por defecto).
 */
@Serializable
data class IslandSettings(
    // ── Tamaño y posición ─────────────────────────────────────────────
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    /** Margen alrededor del agujero de la cámara: define el alto de la píldora. */
    val pillPaddingDp: Float = 5f,
    /** Ancho de cada "ala" de la píldora compacta a los lados de la cámara. */
    val wingDp: Float = 68f,
    /** Ancho de la píldora ancha (eventos del sistema). */
    val wideWidthDp: Float = 300f,
    /** Margen lateral de la tarjeta expandida. */
    val expandedMarginDp: Float = 10f,
    val expandedCornerDp: Float = 38f,
    /** Ajuste fino del círculo en reposo respecto al agujero real. */
    val restSizeAdjustDp: Float = 0f,

    // ── Apariencia ───────────────────────────────────────────────────
    val background: BackgroundMode = BackgroundMode.BLACK,
    val customBackground: Long = 0xFF1C1B1FL,
    val accent: AccentMode = AccentMode.MATERIAL,
    val customAccent: Long = 0xFFA8C7FAL,
    /** Tiñe la tarjeta de música con el color de la carátula. */
    val albumTint: Boolean = true,
    val outline: Boolean = false,
    /** Carátula con forma de galleta Material que gira mientras suena. */
    val morphingArt: Boolean = true,

    // ── Animación ────────────────────────────────────────────────────
    /** Amortiguación del muelle: menos = más rebote. */
    val damping: Float = 0.68f,
    /** Multiplicador de rigidez: más = más rápido. */
    val speed: Float = 1f,

    // ── Actividades ──────────────────────────────────────────────────
    val calls: Boolean = true,
    val media: Boolean = true,
    val timers: Boolean = true,
    val navigation: Boolean = true,
    val progress: Boolean = true,
    val liveUpdates: Boolean = true,
    val recording: Boolean = true,
    val notifications: NotificationMode = NotificationMode.ALL,
    val otp: Boolean = true,
    val otpAutoCopy: Boolean = false,
    val payments: Boolean = true,
    val charging: Boolean = true,
    val lowBattery: Boolean = true,
    val bluetooth: Boolean = true,
    val ringer: Boolean = true,
    val airplane: Boolean = true,
    val headphones: Boolean = true,
    val peekSeconds: Int = 5,
    val systemEventSeconds: Int = 3,
    /** Minutos que la música pausada se queda en la isla. 0 = se va al pausar. */
    val pausedMediaMinutes: Int = 5,

    // ── Comportamiento ───────────────────────────────────────────────
    val autoCollapseSeconds: Int = 8,
    val hideInLandscape: Boolean = true,
    val hideInFullscreen: Boolean = true,
    val hideOnLockscreen: Boolean = false,
    val hideMediaWhenAppOpen: Boolean = true,
    val respectDnd: Boolean = true,
    /** Apaga las notificaciones emergentes del sistema mientras la isla está activa. */
    val islandIsPrimary: Boolean = false,
    /** Burbujas de mensajes sin leer a la izquierda de la isla. */
    val unreadBubbles: Boolean = true,
    val maxBubbles: Int = 3,
    val showSecondary: Boolean = true,
    val haptics: Boolean = true,
    /** Muestra el contorno de la isla en reposo para calibrar con la cámara. */
    val calibration: Boolean = false,

    // ── Gestos ───────────────────────────────────────────────────────
    val tap: GestureAction = GestureAction.TOGGLE_EXPAND,
    val longPress: GestureAction = GestureAction.OPEN_APP,
    val swipeUp: GestureAction = GestureAction.DISMISS,
    val swipeDown: GestureAction = GestureAction.TOGGLE_EXPAND,
    val swipeSideways: GestureAction = GestureAction.NEXT_ACTIVITY,
    val idleTap: IdleAction = IdleAction.NONE,
    val idleLongPress: IdleAction = IdleAction.NONE,

    // ── Apps ─────────────────────────────────────────────────────────
    val blockedApps: Set<String> = emptySet(),
)

/** Guarda y publica la configuración. La app y el overlay comparten proceso, así que comparten este objeto. */
object SettingsStore {
    private const val KEY = "settings_json"
    private lateinit var prefs: SharedPreferences
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private val _flow = MutableStateFlow(IslandSettings())
    val flow: StateFlow<IslandSettings> = _flow.asStateFlow()
    val current: IslandSettings get() = _flow.value

    fun init(context: Context) {
        prefs = context.getSharedPreferences("island", Context.MODE_PRIVATE)
        _flow.value = prefs.getString(KEY, null)?.let { decode(it) } ?: IslandSettings()
    }

    fun update(block: (IslandSettings) -> IslandSettings) {
        val next = block(_flow.value)
        _flow.value = next
        prefs.edit().putString(KEY, json.encodeToString(IslandSettings.serializer(), next)).apply()
    }

    fun reset() = update { IslandSettings(calibration = false) }

    fun export(): String = json.encodeToString(IslandSettings.serializer(), current)

    /** Devuelve true si el JSON era válido. */
    fun import(text: String): Boolean {
        val parsed = decode(text) ?: return false
        update { parsed }
        return true
    }

    private fun decode(text: String): IslandSettings? =
        runCatching { json.decodeFromString(IslandSettings.serializer(), text) }.getOrNull()
}
