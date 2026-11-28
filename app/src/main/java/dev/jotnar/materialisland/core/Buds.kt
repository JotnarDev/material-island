package dev.jotnar.materialisland.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import dev.jotnar.materialisland.model.BudsInfo
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Auriculares Bluetooth: qué tipo son y cuánta batería tienen.
 *
 * La batería sale de lo que Android ya sabe del aparato (perfil de manos libres o servicio de batería BLE):
 * `BluetoothDevice.getBatteryLevel()` es una API oculta, así que se llama con HiddenApiBypass, y Android
 * avisa de los cambios con el broadcast [ACTION_BATTERY]. El nivel por auricular y del estuche solo lo
 * tiene el sistema (Fast Pair, permiso privilegiado), por eso normalmente hay un único nivel.
 */
object Buds {
    const val ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
    const val LOW = 15

    private val earbuds = Regex("buds|pods|earbud|ear ?\\(|\\bwf-|elite|liberty|tws|in-?ear|nothing ear|\\bcmf\\b|beats (fit|flex)|galaxy buds")
    private val headphones = Regex("headphone|\\bwh-|\\bqc ?\\d|quietcomfort|\\bstudio\\b|momentum|over-?ear|\\bxm\\d|\\bmax\\b|\\bsolo\\b|tune 7|live 6|space q")

    /** null si no es un aparato de audio para la cabeza (coche, altavoz, reloj, tele...). */
    @SuppressLint("MissingPermission")
    fun type(d: BluetoothDevice, name: String): BudsInfo.Type? {
        val n = name.lowercase()
        val cls = runCatching { d.bluetoothClass }.getOrNull()
        return when {
            "airpods max" in n -> BudsInfo.Type.HEADPHONES
            earbuds.containsMatchIn(n) -> BudsInfo.Type.EARBUDS
            headphones.containsMatchIn(n) -> BudsInfo.Type.HEADPHONES
            cls?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> BudsInfo.Type.HEADPHONES
            cls?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                cls?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BudsInfo.Type.EARBUDS
            else -> null
        }
    }

    /** Batería que Android conoce del aparato (0..100) o null si aún no la ha recibido. */
    @SuppressLint("MissingPermission")
    fun battery(d: BluetoothDevice): Int? = runCatching {
        HiddenApiBypass.invoke(BluetoothDevice::class.java, d, "getBatteryLevel") as Int
    }.getOrNull()?.takeIf { it in 0..100 }
}
