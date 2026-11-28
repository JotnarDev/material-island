package dev.jotnar.materialisland.core

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind

/** id del evento de auriculares (se actualiza en sitio cuando llega la batería). */
const val BUDS_ID = "sys:BLUETOOTH"

/** Eventos cortos del sistema que aparecen como píldora ancha unos segundos. */
class SystemEventMonitor(private val ctx: Context) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            // Los broadcasts "sticky" se reenvían al registrarse: no son eventos nuevos.
            if (isInitialStickyBroadcast) return
            handle(intent)
        }
    }

    fun start() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Buds.ACTION_BATTERY)
        }
        ctx.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
    }

    fun stop() {
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    private fun batteryLevel(): Int {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }

    private fun handle(intent: Intent) {
        val s = SettingsStore.current
        val dur = s.systemEventSeconds * 1000L
        fun show(kind: Kind, title: String, value: String?, key: String? = null) = IslandStore.show(
            IslandItem(id = "sys:${kind.name}", kind = kind, title = title, value = value, compactText = key, durationMs = dur),
        )
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> if (s.charging) {
                fun charging(info: dev.jotnar.materialisland.model.ChargeInfo, at: Long = System.currentTimeMillis()) = IslandStore.show(
                    IslandItem(
                        id = "sys:CHARGING", kind = Kind.CHARGING, title = info.speed.label, value = "${info.level}%",
                        compactText = "on", durationMs = dur, charge = info, postedAt = at,
                    ),
                )
                charging(Battery.read(ctx))
                // El cargador tarda un momento en negociar la potencia: se actualiza para saber si es rápida.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val cur = IslandStore.state.value.transient
                    if (cur?.id == "sys:CHARGING" && cur.compactText == "on") charging(Battery.read(ctx), cur.postedAt)
                }, 1800)
            }
            Intent.ACTION_POWER_DISCONNECTED -> if (s.charging) {
                IslandStore.show(
                    IslandItem(
                        id = "sys:CHARGING", kind = Kind.CHARGING, title = "Disconnected", value = "${batteryLevel()}%",
                        compactText = "off", durationMs = dur, charge = Battery.read(ctx),
                    ),
                )
            }
            Intent.ACTION_BATTERY_LOW -> if (s.lowBattery) show(Kind.BATTERY, "Low battery", "${batteryLevel()}%")
            AudioManager.RINGER_MODE_CHANGED_ACTION -> if (s.ringer &&
                android.os.SystemClock.elapsedRealtime() - Actions.ownRingerChangeAt > 1500) {
                when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                    AudioManager.RINGER_MODE_SILENT -> show(Kind.RINGER, "Silent", "On", "silent")
                    AudioManager.RINGER_MODE_VIBRATE -> show(Kind.RINGER, "Vibrate", "On", "vibrate")
                    AudioManager.RINGER_MODE_NORMAL -> show(Kind.RINGER, "Sound", "On", "normal")
                }
            }
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> if (s.airplane) {
                val on = intent.getBooleanExtra("state", false)
                show(Kind.AIRPLANE, "Airplane mode", if (on) "On" else "Off", if (on) "on" else "off")
            }
            AudioManager.ACTION_HEADSET_PLUG -> if (s.headphones) {
                val on = intent.getIntExtra("state", 0) == 1
                show(Kind.HEADPHONES, "Headphones", if (on) "Connected" else "Disconnected", if (on) "on" else "off")
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (s.bluetooth) {
                val on = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
                val name = deviceName(intent) ?: "Bluetooth"
                val buds = budsInfo(intent, name, on)
                if (buds != null) {
                    if (!on) lastLevel.remove(buds.address)
                    showBuds(buds, dur)
                } else {
                    show(Kind.BLUETOOTH, name, if (on) "Connected" else "Disconnected", if (on) "on" else "off")
                }
            }
            // Los auriculares mandan la batería un momento después de conectarse, y luego cada vez que baja.
            Buds.ACTION_BATTERY -> if (s.bluetooth) {
                val level = intent.getIntExtra(Buds.EXTRA_BATTERY, -1).takeIf { it in 0..100 } ?: return
                val name = deviceName(intent) ?: return
                val buds = budsInfo(intent, name, true)?.copy(level = level) ?: return
                val prev = lastLevel.put(buds.address, level)
                val cur = IslandStore.state.value.transient
                when {
                    cur?.id == BUDS_ID && cur.buds?.address == buds.address -> showBuds(buds, dur, cur.postedAt, cur.text)
                    level <= Buds.LOW && prev != null && prev > Buds.LOW -> showBuds(buds, dur, low = true)
                }
            }
        }
    }

    /** Último nivel conocido de cada auricular, para avisar una sola vez al bajar del umbral. */
    private val lastLevel = HashMap<String, Int>()

    private fun showBuds(b: dev.jotnar.materialisland.model.BudsInfo, dur: Long, at: Long = System.currentTimeMillis(), text: String? = null, low: Boolean = false) {
        IslandStore.show(
            IslandItem(
                id = BUDS_ID, kind = Kind.BLUETOOTH, title = b.name,
                text = if (low) "Low battery" else text ?: if (b.connected) "Connected" else "Disconnected",
                value = b.shown?.let { "$it%" } ?: if (b.connected) "Connected" else "Disconnected",
                compactText = if (b.connected) "on" else "off", durationMs = dur, buds = b, postedAt = at,
            ),
        )
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun budsInfo(intent: Intent, name: String, on: Boolean): dev.jotnar.materialisland.model.BudsInfo? {
        if (ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return null
        val type = Buds.type(d, name) ?: return null
        val level = if (on) (Buds.battery(d) ?: lastLevel[d.address]) else null
        if (level != null) lastLevel[d.address] = level
        return dev.jotnar.materialisland.model.BudsInfo(name, d.address, type, on, level)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun deviceName(intent: Intent): String? {
        if (ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return null
        return runCatching { d.alias ?: d.name }.getOrNull()
    }
}
