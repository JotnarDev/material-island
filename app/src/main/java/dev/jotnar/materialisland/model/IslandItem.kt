package dev.jotnar.materialisland.model

import android.app.PendingIntent
import android.graphics.Bitmap
import android.media.session.MediaController

/**
 * Todo lo que la isla puede mostrar.
 *
 * Las actividades "en curso" (persistent = true) viven mientras exista su fuente (notificación,
 * sesión de música...). Las "transitorias" (avisos, eventos del sistema) aparecen unos segundos y se van.
 *
 * [priority] decide cuál ocupa la isla cuando hay varias a la vez: la de mayor prioridad va en la
 * isla y la segunda aparece como burbuja al lado.
 */
enum class Kind(val priority: Int, val persistent: Boolean) {
    CALL(100, true),
    NAVIGATION(80, true),
    TIMER(70, true),
    RECORDING(68, true),
    LIVE(65, true),
    MEDIA(60, true),
    PROGRESS(50, true),

    NOTIFICATION(0, false),
    OTP(0, false),
    PAYMENT(0, false),
    CHARGING(0, false),
    BATTERY(0, false),
    BLUETOOTH(0, false),
    RINGER(0, false),
    AIRPLANE(0, false),
    HEADPHONES(0, false),
    INFO(0, false),
}

enum class ActionStyle { NORMAL, POSITIVE, NEGATIVE }

data class IslandAction(
    val title: String,
    val intent: PendingIntent?,
    val style: ActionStyle = ActionStyle.NORMAL,
    /** Si no es null, la acción acepta texto (respuesta rápida). */
    val onReply: ((String) -> Unit)? = null,
    /** Acción local (demo, copiar código...). Tiene prioridad sobre [intent]. */
    val onClick: (() -> Unit)? = null,
)

data class MediaInfo(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val artColor: Int?,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val playing: Boolean,
    val controller: MediaController?,
) {
    fun currentPosition(now: Long = android.os.SystemClock.elapsedRealtime()): Long {
        if (!playing) return positionMs
        val p = positionMs + ((now - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

data class IslandItem(
    val id: String,
    val kind: Kind,
    val title: String,
    val text: String? = null,
    val pkg: String? = null,
    val appLabel: String? = null,
    /** Icono de la app (ya rasterizado). */
    val appIcon: Bitmap? = null,
    /** Imagen grande: avatar, carátula, flecha de navegación... */
    val image: Bitmap? = null,
    /** Color propio de la notificación/carátula (ARGB) o null para usar Material You. */
    val accent: Int? = null,
    /** Texto corto para la isla compacta. */
    val compactText: String? = null,
    /** 0..1, o -1 si no hay progreso. */
    val progress: Float = -1f,
    val indeterminate: Boolean = false,
    /** Base del cronómetro (System.currentTimeMillis) o 0. */
    val chronoBase: Long = 0L,
    val countDown: Boolean = false,
    val incoming: Boolean = false,
    val otp: String? = null,
    val actions: List<IslandAction> = emptyList(),
    val contentIntent: PendingIntent? = null,
    val media: MediaInfo? = null,
    /** Para eventos del sistema: valor a la derecha (p. ej. "78%"). */
    val value: String? = null,
    /** Abrir expandida automáticamente (llamada entrante). */
    val autoExpand: Boolean = false,
    /** Duración en pantalla para las transitorias, ms. */
    val durationMs: Long = 4000L,
    val contentHash: Int = 0,
    val postedAt: Long = System.currentTimeMillis(),
)
