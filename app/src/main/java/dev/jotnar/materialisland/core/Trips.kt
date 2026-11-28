package dev.jotnar.materialisland.core

import dev.jotnar.materialisland.model.TripInfo

/**
 * Viajes (Uber, DiDi, inDrive...) y pedidos (Rappi, Uber Eats, DoorDash...) a partir de la notificación
 * en curso que publican esas apps. Se lee el texto que ya muestran (tiempo de llegada, etapa, conductor
 * y matrícula), en inglés, español y portugués. Nada sale del teléfono.
 */
object Trips {
    private val RIDE_APPS = setOf(
        "com.ubercab", "me.lyft.android", "com.xiaojukeji.didi.global.customer", "com.didiglobal.passenger",
        "sinet.startup.inDriver", "com.cabify.rider", "ee.mtakso.client", "com.taxis99", "com.grabtaxi.passenger",
        "com.beat.passenger", "com.picap.app", "com.easytaxi", "com.uber.lite",
    )
    private val DELIVERY_APPS = setOf(
        "com.ubercab.eats", "com.dd.doordash", "com.grubhub.android", "com.grability.rappi", "com.rappi.android",
        "com.instacart.client", "ca.skipthedishes.customer", "com.skipthedishes.customer", "br.com.brainweb.ifood",
        "com.global.foodpanda.android", "com.deliveroo.orderapp", "com.glovoapp", "com.didiglobal.food",
        "com.justeat.app.uk", "com.pedidosya", "com.wolt.android", "com.postmates.android",
    )
    /** Tiendas y mensajerías: paquetes, no comida. */
    private val PACKAGE_APPS = setOf(
        "com.mercadolibre", "com.amazon.mShop.android.shopping", "com.alibaba.aliexpresshd", "com.einnovation.temu",
        "com.zzkko", "com.shopify.arrive", "com.ups.mobile.android", "com.fedex.ida.android", "com.dhl.exp.dhlmobile",
        "de.dhl.paket", "com.canadapost.android", "com.usps", "com.parcelsapp", "com.servientrega",
    )
    private val PACKAGE_WORDS = Regex("(?i)package|paquete|pacote|parcel|shipment|env[ií]o|shipped|enviado|tracking|rastreo|out for delivery|en reparto")

    fun isTripApp(pkg: String) = pkg in RIDE_APPS || pkg in DELIVERY_APPS || pkg in PACKAGE_APPS

    private val ORDER_WORDS = Regex("(?i)order|pedido|food|comida|delivery|entrega|restaurant|courier|dasher|repartidor|rappitendero|package|paquete")

    private fun stage(t: String, type: TripInfo.Type): Int {
        fun has(r: String) = Regex("(?i)$r").containsMatchIn(t)
        if (type == TripInfo.Type.PACKAGE) return when {
            has("delivered|entregad|has arrived|lleg[oó]|chegou") -> 3
            has("out for delivery|en reparto|saiu para entrega|on its way to you|llega hoy|arriving today|chega hoje") -> 2
            has("shipped|enviado|in transit|en tr[aá]nsito|em tr[aâ]nsito|on the way|en camino|a caminho|despachado") -> 1
            else -> 0
        }
        val ride = type == TripInfo.Type.RIDE
        return if (ride) when {
            has("(driver|conductor|motorista|chofer)\\b.{0,30}(has arrived|arrived|ha llegado|lleg[oó]|chegou|is here|est[aá] aqu)") -> 1
            has("you('ve| have) arrived|trip (completed|ended)|viaje (finalizado|terminado|completado)|llegaste|chegou ao destino|rate your") -> 3
            has("on (your )?trip|en viaje|heading to|en ruta|to your destination|al destino|drop-?off|em viagem|enjoy your ride") -> 2
            has("arriv|on the way|en camino|llega en|arrives in|pick ?up|te recoge|recoger|a caminho|is coming|driver|conductor") -> 1
            else -> 0
        } else when {
            has("delivered|entregad|has arrived|ha llegado|lleg[oó] tu|chegou|enjoy your|disfruta|buen provecho") -> 3
            has("on the way|on its way|en camino|picked up|recogi|heading|out for delivery|a caminho|arriving|llegando|almost there|casi|nearby|cerca|saiu para") -> 2
            has("prepar|cooking|making your|being made|shopping|comprando|picking|en preparaci|preparando|separando") -> 1
            else -> 0
        }
    }

    private val MINUTES = Regex("(?i)\\b(\\d{1,3})\\s*(?:min|mins|minutes|minutos|minuto)\\b")
    private val CLOCK = Regex("(?i)\\b(\\d{1,2}:\\d{2})\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?")
    private val PLATE = Regex("\\b(?=[A-Z0-9 -]{5,9}\\b)(?=[A-Z0-9 -]*\\d)(?=[A-Z0-9 -]*[A-Z])[A-Z0-9]{2,4}[- ]?[A-Z0-9]{2,4}\\b")
    private val DRIVER = Regex("\\b([A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,})\\s+(?:is|está|esta|will|llega|llegará|chega|arrives|has)\\b")

    fun parse(pkg: String, title: String?, text: String?, shortCritical: String?): TripInfo {
        val all = listOfNotNull(title, text).joinToString(" · ")
        val type = when {
            pkg in PACKAGE_APPS -> TripInfo.Type.PACKAGE
            pkg in RIDE_APPS && !ORDER_WORDS.containsMatchIn(all) -> TripInfo.Type.RIDE
            pkg !in RIDE_APPS && PACKAGE_WORDS.containsMatchIn(all) && !Regex("(?i)food|comida|restaurant").containsMatchIn(all) -> TripInfo.Type.PACKAGE
            else -> TripInfo.Type.FOOD
        }
        val ride = type == TripInfo.Type.RIDE
        val st = stage(all, type)
        val eta = shortCritical?.takeIf { it.isNotBlank() }
            ?: MINUTES.find(all)?.let { "${it.groupValues[1]} min" }
            ?: CLOCK.find(all)?.let { m -> listOf(m.groupValues[1], m.groupValues[2].uppercase().replace(".", "").replace(" ", "")).filter { it.isNotEmpty() }.joinToString(" ") }
        // La matrícula se busca sin horas ni minutos, y con al menos 6 letras/números ("ETA 12" o "45 PM" no valen).
        val plate = if (ride) {
            val clean = CLOCK.replace(MINUTES.replace(all, " "), " ")
            PLATE.findAll(clean).map { it.value }.firstOrNull { v -> v.count(Char::isLetterOrDigit) >= 6 }
        } else null
        val vehicle = plate?.let { pl ->
            all.split('·', '•', '|', ',', '\n').map { it.trim() }
                .firstOrNull { seg -> pl !in seg && seg.length in 3..32 && seg.any(Char::isLetter) && !MINUTES.containsMatchIn(seg) && !CLOCK.containsMatchIn(seg) && seg != title }
        }
        val driver = DRIVER.find(all)?.groupValues?.get(1)?.takeIf { it !in setOf("Your", "Tu", "Seu", "Driver", "Order", "Pedido") }
        return TripInfo(
            type = type,
            eta = eta, stage = st, driver = driver, plate = plate, vehicle = vehicle,
        )
    }
}
