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
        MEDIA("Music"), CALL("Call"), TIMER("Timer"), NAV("Navigation"),
        DOWNLOAD("Download"), MESSAGE("Message"), OTP("Code"), CHARGING("Charging"),
        BLUETOOTH("Bluetooth"), SPEAKER("Speaker"), LAPTOP("Laptop"), RINGER("Vibrate"), RIDE("Ride"), ORDER("Order"), PARCEL("Package"), PRIVACY("Camera in use"),
    }

    private fun gradient(a: Int, b: Int): Bitmap {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 256f, 256f, a, b, Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, 256f, 256f, p)
        return bmp
    }

    private var demoPlaying = true

    /** Portada de la demo: degradado con anillos y un sol, para que se note el cristal desenfocado. */
    private val demoCover: Bitmap by lazy {
        val bmp = gradient(0xFFFF8A65.toInt(), 0xFF7E57C2.toInt())
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFE082.toInt(); c.drawCircle(176f, 84f, 38f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 10f
        listOf(0x66FFFFFF, 0x44FFFFFF, 0x2EFFFFFF).forEachIndexed { i, col ->
            p.color = col; c.drawCircle(72f, 196f, 46f + i * 34f, p)
        }
        p.style = Paint.Style.FILL; p.color = 0xFF311B92.toInt()
        c.drawRect(0f, 214f, 256f, 256f, p)
        p.color = 0xFF4527A0.toInt(); c.drawCircle(200f, 230f, 54f, p)
        bmp
    }

    private fun mediaItem(playing: Boolean, pos: Long = 42_000L): IslandItem {
        val art = demoCover
        val info = MediaInfo(
            title = "Demo song", artist = "Material Island", art = art, artColor = 0xFFFF8A65.toInt(),
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
                    id = "demo:call", kind = Kind.CALL, title = "Mom", text = "Incoming call · Phone",
                    appLabel = "Phone", incoming = true, autoExpand = true,
                    actions = listOf(
                        IslandAction("Decline", null, ActionStyle.NEGATIVE, onClick = { IslandStore.remove("demo:call") }),
                        IslandAction("Answer", null, ActionStyle.POSITIVE, onClick = { answerDemoCall() }),
                    ),
                ),
            )
            Type.TIMER -> IslandStore.upsert(
                IslandItem(
                    id = "demo:timer", kind = Kind.TIMER, title = "Timer", text = "Pasta",
                    appLabel = "Clock", chronoBase = now + 5 * 60_000, countDown = true,
                    actions = listOf(IslandAction("Stop", null, onClick = { IslandStore.remove("demo:timer") })),
                ),
            )
            Type.NAV -> IslandStore.upsert(
                IslandItem(
                    id = "demo:nav", kind = Kind.NAVIGATION, title = "350 m · Turn right",
                    text = "Evergreen Terrace · Arrive 6:42 PM", appLabel = "Maps", compactText = "350 m",
                    actions = listOf(IslandAction("Exit", null, onClick = { IslandStore.remove("demo:nav") })),
                ),
            )
            Type.DOWNLOAD -> {
                IslandStore.upsert(
                    IslandItem(
                        id = "demo:dl", kind = Kind.PROGRESS, title = "Downloading archive.zip", text = "48 MB of 120 MB",
                        appLabel = "Chrome", progress = 0.4f,
                    ),
                )
            }
            Type.MESSAGE -> IslandStore.show(
                IslandItem(
                    id = "demo:msg", kind = Kind.NOTIFICATION, title = "Alex",
                    text = "Did you see the new island? It looks amazing 🔥", appLabel = "Messages", durationMs = 6000,
                    actions = listOf(
                        IslandAction("Reply", null, onReply = { }),
                        IslandAction("Mark as read", null, onClick = { IslandStore.dismissTransient("demo:msg") }),
                    ),
                ),
            )
            Type.OTP -> IslandStore.show(
                IslandItem(
                    id = "demo:otp", kind = Kind.OTP, title = "Code · Bank", text = "Your code is 482913",
                    appLabel = "Messages", otp = "482913", value = "482913", durationMs = 8000,
                ),
            )
            Type.CHARGING -> IslandStore.show(
                IslandItem(
                    id = "sys:CHARGING", kind = Kind.CHARGING, title = "Fast charging", value = "78%", compactText = "on",
                    durationMs = 4000,
                    charge = dev.jotnar.materialisland.model.ChargeInfo(
                        level = 78, charging = true, plug = dev.jotnar.materialisland.model.ChargeInfo.Plug.WIRED,
                        speed = dev.jotnar.materialisland.model.ChargeInfo.Speed.FAST, watts = 18.4f,
                        fullInMs = 38 * 60_000L, tempC = 31.5f, demo = true,
                    ),
                ),
            )
            Type.BLUETOOTH -> IslandStore.show(
                IslandItem(
                    id = "sys:BLUETOOTH", kind = Kind.BLUETOOTH, title = "Pixel Buds Pro 2", text = "Connected", value = "80%",
                    compactText = "on", durationMs = 4000,
                    buds = dev.jotnar.materialisland.model.BudsInfo(
                        "Pixel Buds Pro 2", "demo", dev.jotnar.materialisland.model.BudsInfo.Type.EARBUDS, true,
                        left = 80, right = 85, case = 45,
                    ),
                ),
            )
            Type.SPEAKER, Type.LAPTOP -> {
                val sp = type == Type.SPEAKER
                IslandStore.show(
                    IslandItem(
                        id = "sys:BLUETOOTH", kind = Kind.BLUETOOTH, title = if (sp) "JBL Flip 6" else "MacBook Air",
                        text = "Connected", value = if (sp) "65%" else "Connected", compactText = "on", durationMs = 6000,
                        buds = dev.jotnar.materialisland.model.BudsInfo(
                            if (sp) "JBL Flip 6" else "MacBook Air", "demo",
                            if (sp) dev.jotnar.materialisland.model.BudsInfo.Type.SPEAKER else dev.jotnar.materialisland.model.BudsInfo.Type.COMPUTER,
                            true, level = if (sp) 65 else null,
                        ),
                    ),
                )
            }
            Type.RIDE -> {
                val trip = dev.jotnar.materialisland.model.TripInfo(
                    dev.jotnar.materialisland.model.TripInfo.Type.RIDE, eta = "4 min", stage = 1,
                    driver = "Carlos", plate = "KLM-482", vehicle = "Gray Toyota Corolla",
                )
                IslandStore.upsert(
                    IslandItem(
                        id = "demo:ride", kind = Kind.TRIP, title = "Carlos is on the way", text = "Gray Toyota Corolla · KLM-482",
                        appLabel = "Ride", compactText = trip.eta, trip = trip,
                        actions = listOf(IslandAction("Cancel", null, onClick = { IslandStore.remove("demo:ride") })),
                    ),
                )
            }
            Type.ORDER -> {
                val trip = dev.jotnar.materialisland.model.TripInfo(dev.jotnar.materialisland.model.TripInfo.Type.FOOD, eta = "12 min", stage = 2)
                IslandStore.upsert(
                    IslandItem(
                        id = "demo:order", kind = Kind.TRIP, title = "Your order is on the way", text = "Burger place · 2 items",
                        appLabel = "Delivery", compactText = trip.eta, trip = trip, progress = 0.58f,
                        actions = listOf(IslandAction("Close", null, onClick = { IslandStore.remove("demo:order") })),
                    ),
                )
            }
            Type.PARCEL -> {
                val trip = dev.jotnar.materialisland.model.TripInfo(dev.jotnar.materialisland.model.TripInfo.Type.PACKAGE, eta = "Today", stage = 2)
                IslandStore.upsert(
                    IslandItem(
                        id = "demo:parcel", kind = Kind.TRIP, title = "Out for delivery", text = "Headphones case · 1 package",
                        appLabel = "Store", compactText = trip.eta, trip = trip,
                        actions = listOf(IslandAction("Close", null, onClick = { IslandStore.remove("demo:parcel") })),
                    ),
                )
            }
            Type.PRIVACY -> IslandStore.upsert(
                PrivacyMonitor.item(cam = true, mic = true, app = "Camera", onClose = { IslandStore.remove(PrivacyMonitor.ID) }),
            )
            Type.RINGER -> IslandStore.show(
                IslandItem(id = "sys:RINGER", kind = Kind.RINGER, title = "Vibrate", value = "On", compactText = "vibrate", durationMs = 3000),
            )
        }
    }

    private fun answerDemoCall() {
        IslandStore.upsert(
            IslandItem(
                id = "demo:call", kind = Kind.CALL, title = "Mom", text = "On call", appLabel = "Phone",
                chronoBase = System.currentTimeMillis(),
                actions = listOf(IslandAction("Hang up", null, ActionStyle.NEGATIVE, onClick = { IslandStore.remove("demo:call") })),
            ),
        )
    }

    fun clear() = IslandStore.clearWhere { it.id.startsWith("demo:") || it.id.startsWith("sys:") }
}
