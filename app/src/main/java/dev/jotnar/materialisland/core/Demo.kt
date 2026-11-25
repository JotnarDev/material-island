package dev.jotnar.materialisland.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import dev.jotnar.materialisland.model.ActionStyle
import dev.jotnar.materialisland.model.IslandAction
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind
import dev.jotnar.materialisland.model.MediaInfo

/** Actividades falsas para probar la isla sin esperar a que pasen de verdad. */
object Demo {
    enum class Type(val label: String) {
        MEDIA("Música"), CALL("Llamada"), TIMER("Temporizador"), NAV("Navegación"),
        DOWNLOAD("Descarga"), MESSAGE("Mensaje"), OTP("Código"), CHARGING("Carga"),
        BLUETOOTH("Bluetooth"), RINGER("Vibración"),
    }

    private fun gradient(a: Int, b: Int): Bitmap {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 256f, 256f, a, b, Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, 256f, 256f, p)
        return bmp
    }

    private var demoPlaying = true

    private fun mediaItem(playing: Boolean, pos: Long = 42_000L): IslandItem {
        val art = gradient(0xFFFF8A65.toInt(), 0xFF7E57C2.toInt())
        val info = MediaInfo(
            title = "Canción de demostración", artist = "Material Island", art = art, artColor = 0xFFFF8A65.toInt(),
            durationMs = 215_000, positionMs = pos, positionUpdatedAt = SystemClock.elapsedRealtime(),
            speed = 1f, playing = playing, controller = null,
        )
        return IslandItem(
            id = "demo:media", kind = Kind.MEDIA, title = info.title, text = info.artist, appLabel = "Demo",
            image = art, accent = info.artColor, media = info,
        )
    }

    /** Pausa/reanuda la música de demo (la real usa el MediaController). */
    fun toggleDemoMedia() {
        val cur = IslandStore.get("demo:media")?.media ?: return
        demoPlaying = !demoPlaying
        IslandStore.upsert(mediaItem(demoPlaying, cur.currentPosition()))
    }

    fun run(type: Type) {
        val now = System.currentTimeMillis()
        when (type) {
            Type.MEDIA -> { demoPlaying = true; IslandStore.upsert(mediaItem(true)) }
            Type.CALL -> IslandStore.upsert(
                IslandItem(
                    id = "demo:call", kind = Kind.CALL, title = "Mamá", text = "Llamada entrante · Teléfono",
                    appLabel = "Teléfono", incoming = true, autoExpand = true,
                    actions = listOf(
                        IslandAction("Rechazar", null, ActionStyle.NEGATIVE, onClick = { IslandStore.remove("demo:call") }),
                        IslandAction("Contestar", null, ActionStyle.POSITIVE, onClick = { answerDemoCall() }),
                    ),
                ),
            )
            Type.TIMER -> IslandStore.upsert(
                IslandItem(
                    id = "demo:timer", kind = Kind.TIMER, title = "Temporizador", text = "Pasta",
                    appLabel = "Reloj", chronoBase = now + 5 * 60_000, countDown = true,
                    actions = listOf(IslandAction("Detener", null, onClick = { IslandStore.remove("demo:timer") })),
                ),
            )
            Type.NAV -> IslandStore.upsert(
                IslandItem(
                    id = "demo:nav", kind = Kind.NAVIGATION, title = "350 m · Gira a la derecha",
                    text = "Av. Siempre Viva · Llegada 18:42", appLabel = "Maps", compactText = "350 m",
                    actions = listOf(IslandAction("Salir", null, onClick = { IslandStore.remove("demo:nav") })),
                ),
            )
            Type.DOWNLOAD -> {
                IslandStore.upsert(
                    IslandItem(
                        id = "demo:dl", kind = Kind.PROGRESS, title = "Descargando archivo.zip", text = "48 MB de 120 MB",
                        appLabel = "Chrome", progress = 0.4f,
                    ),
                )
            }
            Type.MESSAGE -> IslandStore.show(
                IslandItem(
                    id = "demo:msg", kind = Kind.NOTIFICATION, title = "Sebastián",
                    text = "¿Viste la isla nueva? Quedó brutal 🔥", appLabel = "Mensajes", durationMs = 6000,
                    actions = listOf(
                        IslandAction("Responder", null, onReply = { }),
                        IslandAction("Marcar como leído", null, onClick = { IslandStore.dismissTransient("demo:msg") }),
                    ),
                ),
            )
            Type.OTP -> IslandStore.show(
                IslandItem(
                    id = "demo:otp", kind = Kind.OTP, title = "Código · Banco", text = "Tu código es 482913",
                    appLabel = "Mensajes", otp = "482913", value = "482913", durationMs = 8000,
                ),
            )
            Type.CHARGING -> IslandStore.show(
                IslandItem(id = "sys:CHARGING", kind = Kind.CHARGING, title = "Cargando", value = "78%", compactText = "on", durationMs = 3000),
            )
            Type.BLUETOOTH -> IslandStore.show(
                IslandItem(id = "sys:BLUETOOTH", kind = Kind.BLUETOOTH, title = "Pixel Buds Pro 2", value = "Conectado", compactText = "on", durationMs = 3000),
            )
            Type.RINGER -> IslandStore.show(
                IslandItem(id = "sys:RINGER", kind = Kind.RINGER, title = "Vibración", value = "Activada", compactText = "vibrate", durationMs = 3000),
            )
        }
    }

    private fun answerDemoCall() {
        IslandStore.upsert(
            IslandItem(
                id = "demo:call", kind = Kind.CALL, title = "Mamá", text = "En llamada", appLabel = "Teléfono",
                chronoBase = System.currentTimeMillis(),
                actions = listOf(IslandAction("Colgar", null, ActionStyle.NEGATIVE, onClick = { IslandStore.remove("demo:call") })),
            ),
        )
    }

    fun clear() = IslandStore.clearWhere { it.id.startsWith("demo:") || it.id.startsWith("sys:") }
}
