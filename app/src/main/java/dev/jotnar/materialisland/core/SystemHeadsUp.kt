package dev.jotnar.materialisland.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Controla las notificaciones emergentes (heads-up) del sistema para que la isla sea la principal.
 * Escribir Settings.Global requiere WRITE_SECURE_SETTINGS, que solo se concede por ADB:
 *   adb shell pm grant dev.jotnar.materialisland android.permission.WRITE_SECURE_SETTINGS
 */
object SystemHeadsUp {
    private const val KEY = "heads_up_notifications_enabled"

    fun canWrite(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, KEY, 1) == 1

    /** Devuelve false si no hay permiso. */
    fun setEnabled(ctx: Context, enabled: Boolean): Boolean {
        if (!canWrite(ctx)) return false
        return runCatching { Settings.Global.putInt(ctx.contentResolver, KEY, if (enabled) 1 else 0) }.getOrDefault(false)
    }

    /** Notificación de prioridad alta: sale emergente si el sistema tiene los heads-up activos. */
    fun postTest(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("test", "Pruebas", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(ctx, "test")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Notificación de prueba")
            .setContentText("Si la ves arriba en una tarjeta del sistema, los emergentes siguen activos.")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()
        nm.notify(4242, n)
    }
}
