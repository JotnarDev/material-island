package dev.jotnar.materialisland.model

/** Estado de la carga para la píldora y la tarjeta de carga. */
data class ChargeInfo(
    val level: Int,
    val charging: Boolean,
    val plug: Plug,
    val speed: Speed,
    /** Potencia que entra ahora mismo, en vatios (null si el sistema no la da). */
    val watts: Float? = null,
    /** Milisegundos hasta el 100 %; -1 si aún no se sabe. */
    val fullInMs: Long = -1,
    val tempC: Float? = null,
    /** Datos falsos de la demo: no se leen del sistema. */
    val demo: Boolean = false,
) {
    enum class Plug(val label: String) { WIRED("Wired"), USB("USB cable"), WIRELESS("Wireless"), DOCK("Dock"), NONE("Unplugged") }
    enum class Speed(val label: String) { SLOW("Charging slowly"), NORMAL("Charging"), FAST("Fast charging") }
}
