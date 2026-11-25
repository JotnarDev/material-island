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
                val lvl = batteryLevel()
                show(Kind.CHARGING, "Cargando", "$lvl%", key = "on")
            }
            Intent.ACTION_POWER_DISCONNECTED -> if (s.charging) {
                show(Kind.CHARGING, "Desconectado", "${batteryLevel()}%", key = "off")
            }
            Intent.ACTION_BATTERY_LOW -> if (s.lowBattery) show(Kind.BATTERY, "Batería baja", "${batteryLevel()}%")
            AudioManager.RINGER_MODE_CHANGED_ACTION -> if (s.ringer) {
                when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                    AudioManager.RINGER_MODE_SILENT -> show(Kind.RINGER, "Silencio", "Activado", "silent")
                    AudioManager.RINGER_MODE_VIBRATE -> show(Kind.RINGER, "Vibración", "Activada", "vibrate")
                    AudioManager.RINGER_MODE_NORMAL -> show(Kind.RINGER, "Sonido", "Activado", "normal")
                }
            }
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> if (s.airplane) {
                val on = intent.getBooleanExtra("state", false)
                show(Kind.AIRPLANE, "Modo avión", if (on) "Activado" else "Desactivado", if (on) "on" else "off")
            }
            AudioManager.ACTION_HEADSET_PLUG -> if (s.headphones) {
                val on = intent.getIntExtra("state", 0) == 1
                show(Kind.HEADPHONES, "Auriculares", if (on) "Conectados" else "Desconectados", if (on) "on" else "off")
            }
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (s.bluetooth) {
                val on = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
                val name = deviceName(intent) ?: "Bluetooth"
                show(Kind.BLUETOOTH, name, if (on) "Conectado" else "Desconectado", if (on) "on" else "off")
            }
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun deviceName(intent: Intent): String? {
        if (ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return null
        val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return null
        return runCatching { d.alias ?: d.name }.getOrNull()
    }
}
