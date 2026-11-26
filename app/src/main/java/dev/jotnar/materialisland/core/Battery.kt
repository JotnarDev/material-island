package dev.jotnar.materialisland.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dev.jotnar.materialisland.model.ChargeInfo
import kotlin.math.abs

/** Lee el estado de la batería y el cargador. */
object Battery {
    // Mismos umbrales que usa la interfaz del sistema para decir "carga rápida" / "carga lenta".
    private const val SLOW_UW = 5_000_000L
    private const val FAST_UW = 7_500_000L

    fun read(ctx: Context): ChargeInfo {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            ?: i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
            ?: 0
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        // El estado se lee en vivo: el último aviso guardado (sticky) puede ir atrasado justo al conectar.
        val status = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)?.takeIf { it > 0 }
            ?: i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = bm?.isCharging == true ||
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        var plug = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> ChargeInfo.Plug.WIRED
            BatteryManager.BATTERY_PLUGGED_USB -> ChargeInfo.Plug.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeInfo.Plug.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> ChargeInfo.Plug.DOCK
            else -> ChargeInfo.Plug.NONE
        }
        // Si carga pero el aviso guardado aún dice "desconectado", al menos no decir "Unplugged".
        if (charging && plug == ChargeInfo.Plug.NONE) plug = ChargeInfo.Plug.WIRED

        // Potencia máxima que anuncia el cargador (extras del sistema, sin constante pública).
        val maxUa = i?.getIntExtra("max_charging_current", -1) ?: -1
        var maxUv = i?.getIntExtra("max_charging_voltage", -1) ?: -1
        if (maxUv <= 0) maxUv = 5_000_000
        val maxUw = if (maxUa > 0) (maxUa / 1000L) * (maxUv / 1000L) else -1L
        val speed = when {
            maxUw <= 0 -> ChargeInfo.Speed.NORMAL
            maxUw < SLOW_UW -> ChargeInfo.Speed.SLOW
            maxUw > FAST_UW -> ChargeInfo.Speed.FAST
            else -> ChargeInfo.Speed.NORMAL
        }

        // Potencia real ahora: corriente × voltaje de la batería.
        val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val watts = if (currentUa != Int.MIN_VALUE && currentUa != 0 && mv > 0) abs(currentUa) / 1e6f * mv / 1e3f else null

        var fullIn = if (charging) runCatching { bm?.computeChargeTimeRemaining() ?: -1L }.getOrDefault(-1L) else -1L
        if (charging && fullIn <= 0 && level < 100) {
            // Respaldo: carga que falta / corriente actual.
            val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0
            if (counter > 0 && level > 0 && currentUa != Int.MIN_VALUE && abs(currentUa) > 50_000) {
                val fullUah = counter.toLong() * 100 / level
                fullIn = ((fullUah - counter) * 3_600_000L / abs(currentUa))
            }
        }
        val temp = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10f)
        return ChargeInfo(level, charging, plug, speed, watts, fullIn, temp)
    }
}
