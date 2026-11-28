package dev.jotnar.materialisland.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/** Vistas de la tarjeta At a glance, en el orden en que se deslizan. */
@Serializable
enum class GlanceView(val label: String) {
    WEATHER("Weather"),
    CALENDAR("Calendar"),
    SPORTS("Sports"),
    ALERTS("Alerts"),
}

/** Ligas que se pueden seguir (rutas del marcador público de ESPN). */
enum class League(val path: String, val label: String, val sport: String) {
    UCL("soccer/uefa.champions", "Champions League", "soccer"),
    EPL("soccer/eng.1", "Premier League", "soccer"),
    LALIGA("soccer/esp.1", "LaLiga", "soccer"),
    SERIEA("soccer/ita.1", "Serie A", "soccer"),
    BUNDES("soccer/ger.1", "Bundesliga", "soccer"),
    LIGUE1("soccer/fra.1", "Ligue 1", "soccer"),
    MLS("soccer/usa.1", "MLS", "soccer"),
    LIGAMX("soccer/mex.1", "Liga MX", "soccer"),
    NBA("basketball/nba", "NBA", "basketball"),
    NFL("football/nfl", "NFL", "football"),
    MLB("baseball/mlb", "MLB", "baseball"),
    NHL("hockey/nhl", "NHL", "hockey"),
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
    SHORTCUTS("Shortcuts card"),
    FLASHLIGHT("Flashlight"),
    SOUND_MODE("Sound mode"),
    VOLUME("Volume"),
    ALARMS("Alarms"),
    TIMER("Timer"),
    CAMERA("Camera"),
    INTERNET("Internet"),
    SCREENSHOT("Screenshot"),
    NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick settings"),
    LOCK_SCREEN("Lock screen"),
    OPEN_ISLAND_APP("Open Material Island"),
    ;

    companion object {
        /** Lo que se puede poner en la tarjeta de accesos (todo menos "nada" y la propia tarjeta). */
        val shortcutChoices get() = entries.filter { it != NONE && it != SHORTCUTS }
        val defaultShortcuts = listOf(FLASHLIGHT, SOUND_MODE, VOLUME, ALARMS, TIMER, CAMERA)
    }
}

/** Qué muestra la Liquid bar, en el orden en que aparece (izquierda: hora y notificaciones; derecha: el resto). */
@Serializable
enum class BarItem(val label: String, val hint: String, val onByDefault: Boolean) {
    NOTIFICATIONS("Notification icons", "Apps with notifications, next to the clock", true),
    NET_SPEED("Network speed", "Download and upload per second", false),
    TEMPERATURE("Temperature", "Battery temperature, red when the phone is hot", false),
    RAM("Memory", "RAM in use", false),
    ALARM("Alarm", "When an alarm is set", true),
    DND("Do Not Disturb", "When it is on", true),
    RINGER("Vibrate and silent", "When the phone isn't on ring", true),
    BLUETOOTH("Bluetooth", "When Bluetooth is on", true),
    AIRPLANE("Airplane mode", "When it is on", true),
    WIFI("Wi-Fi", "Signal of the network you're on", true),
    MOBILE("Mobile signal", "Signal bars of your SIM", true),
    BATTERY_PERCENT("Battery percentage", "Number inside the battery", true),
}

/** Qué notificaciones aparecen como aviso. */
@Serializable
/** Cómo sale la isla de la cámara. */
enum class OpenStyle(val label: String) {
    MORPH("Morph"),
    DROP("Liquid drop"),
    /** Como la gota, pero en reposo es una barra de borde a borde (con hora y batería) que se contrae hacia la cámara. */
    BAR("Liquid bar"),
}

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
    /** MORPH: la cámara se estira en píldora. DROP: una gota cae de la cámara y se abre debajo. */
    val openStyle: OpenStyle = OpenStyle.MORPH,
    /** Separación entre la cámara y la isla en el estilo DROP. */
    val dropGapDp: Float = 8f,
    /** Estilo BAR: margen de la barra con los bordes de la pantalla. */
    val barMarginDp: Float = 6f,
    /** Estilo BAR: elementos activados de la barra. */
    val barItems: Set<BarItem> = BarItem.entries.filter { it.onByDefault }.toSet(),

    // ── Actividades ──────────────────────────────────────────────────
    val calls: Boolean = true,
    val media: Boolean = true,
    val timers: Boolean = true,
    val navigation: Boolean = true,
    /** Viajes (Uber, DiDi...) y pedidos (Rappi, Uber Eats...) con tiempo de llegada. */
    val trips: Boolean = true,
    /** Punto de privacidad: avisar mientras una app usa la cámara o el micrófono. */
    val privacyIndicator: Boolean = true,
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
    val idleTap: IdleAction = IdleAction.SHORTCUTS,
    val idleDoubleTap: IdleAction = IdleAction.NONE,
    val idleLongPress: IdleAction = IdleAction.NONE,
    /** Accesos de la tarjeta, en el orden en que se muestran. */
    val shortcuts: List<IdleAction> = IdleAction.defaultShortcuts,

    /** El usuario aceptó el aviso de uso de la API de accesibilidad. */
    val a11yDisclosureAccepted: Boolean = false,

    /** Mirar si hay versión nueva al abrir la app (una consulta a GitHub). Solo si el usuario lo eligió. */
    val autoUpdateCheck: Boolean = false,

    /** Ya se le preguntó si quiere buscar actualizaciones en GitHub (la pregunta sale una vez). */
    val updateOptInAsked: Boolean = false,

    // ── At a glance (lo único que usa internet) ──────────────────────
    val glanceEnabled: Boolean = false,
    val glanceViews: List<GlanceView> = GlanceView.entries,
    /** Lugar del clima: se guarda al usar la ubicación o buscar una ciudad. */
    val glanceLat: Double? = null,
    val glanceLon: Double? = null,
    val glancePlace: String = "",
    val glanceFahrenheit: Boolean = false,
    /** Rutas de [League] seguidas. */
    val glanceLeagues: List<String> = listOf(League.UCL.path, League.EPL.path, League.NBA.path, League.NFL.path),
    /** Equipos favoritos separados por comas (nombre o abreviatura). Vacío = todos los partidos. */
    val glanceTeams: String = "",
    /** Calendario: mostrar también los eventos de todo el día. */
    val glanceAllDay: Boolean = true,

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

    /** Vistas que ya no existen (Finance se quitó en 1.3.0): se descartan para no perder el resto de ajustes. */
    private val REMOVED_VIEWS = setOf("FINANCE")

    private fun decode(text: String): IslandSettings? = runCatching {
        val obj = json.parseToJsonElement(text).jsonObject
        val views = obj["glanceViews"]?.jsonArray
        val clean = if (views == null) obj else JsonObject(
            obj + ("glanceViews" to JsonArray(views.filter { it.jsonPrimitive.content !in REMOVED_VIEWS })),
        )
        json.decodeFromJsonElement(IslandSettings.serializer(), clean)
    }.getOrNull()
}
