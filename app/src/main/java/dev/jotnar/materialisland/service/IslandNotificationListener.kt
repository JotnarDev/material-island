package dev.jotnar.materialisland.service

import android.app.NotificationManager
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.jotnar.materialisland.core.IslandStore
import dev.jotnar.materialisland.core.MediaMonitor
import dev.jotnar.materialisland.core.NotificationParser
import dev.jotnar.materialisland.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow

/** Escucha notificaciones y sesiones de música y las vuelca en [IslandStore]. */
class IslandNotificationListener : NotificationListenerService() {

    private var media: MediaMonitor? = null
    /** Último contenido visto por notificación, para no repetir el aviso en cada actualización. */
    private val lastHash = HashMap<String, Int>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.value = true
        instance = this
        media = MediaMonitor(this, ComponentName(this, IslandNotificationListener::class.java)).also { it.start() }
        // Recupera lo que ya está en curso (sin avisos de cosas viejas).
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it, initial = true) }
        refreshStatusIcons()
    }

    private val iconCache = HashMap<String, android.graphics.Bitmap?>()

    /** Una entrada por app, la más reciente primero; solo las que no son silenciosas, como hace el Pixel. */
    private fun refreshStatusIcons() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = Ranking()
        val out = list.asSequence()
            .filter { it.packageName != packageName && (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 }
            .filter { !currentRanking.getRanking(it.key, ranking) || ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT }
            .sortedByDescending { it.postTime }
            .distinctBy { it.packageName }
            .take(8)
            .mapNotNull { sbn ->
                val icon = sbn.notification.smallIcon ?: return@mapNotNull null
                val key = sbn.packageName + ":" + runCatching { icon.resId }.getOrDefault(0) + ":" + icon.type
                val bmp = iconCache.getOrPut(key) {
                    runCatching {
                        val d = icon.loadDrawable(this) ?: return@runCatching null
                        val px = (20 * resources.displayMetrics.density).toInt()
                        android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
                            val c = android.graphics.Canvas(b); d.setBounds(0, 0, px, px); d.draw(c)
                        }
                    }.getOrNull()
                }
                bmp?.let { sbn.packageName to it }
            }.toList()
        IslandStore.statusIcons.value = out
    }

    override fun onListenerDisconnected() {
        connected.value = false
        instance = null
        media?.stop()
        media = null
        IslandStore.clearWhere { it.id.startsWith("n:") }
        IslandStore.clearUnread()
        IslandStore.statusIcons.value = emptyList()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { handle(sbn, initial = false); refreshStatusIcons() }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastHash.remove(sbn.key)
        IslandStore.removeUnread("n:" + sbn.key)
        IslandStore.remove("n:" + sbn.key)
        refreshStatusIcons()
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val s = SettingsStore.current
        NotificationParser.unreadMessage(this, sbn, s)?.let { IslandStore.setUnread(it) }
            ?: IslandStore.removeUnread("n:" + sbn.key)
        val canPeek = !initial && passesFilters(sbn, s.respectDnd)
        when (val r = NotificationParser.parse(this, sbn, s, canPeek)) {
            is NotificationParser.Result.Live -> {
                lastHash[sbn.key] = r.item.contentHash
                IslandStore.upsert(r.item)
            }
            is NotificationParser.Result.Peek -> {
                val prev = lastHash.put(sbn.key, r.item.contentHash)
                val alertOnce = (sbn.notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE) != 0
                if (prev == r.item.contentHash || (prev != null && alertOnce)) return
                IslandStore.remove("n:" + sbn.key) // por si antes era una actividad en curso
                IslandStore.show(r.item)
            }
            NotificationParser.Result.Ignore -> {
                // Si dejó de ser actividad en curso (p. ej. la descarga terminó), quítala.
                IslandStore.remove("n:" + sbn.key)
            }
        }
    }

    /** No interrumpe con notificaciones silenciosas ni las que No molestar bloquea. */
    private fun passesFilters(sbn: StatusBarNotification, respectDnd: Boolean): Boolean {
        val ranking = Ranking()
        if (!currentRanking.getRanking(sbn.key, ranking)) return true
        if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return false
        if (respectDnd && !ranking.matchesInterruptionFilter()) return false
        return true
    }

    fun dismiss(key: String) = runCatching { cancelNotification(key) }

    companion object {
        val connected = MutableStateFlow(false)
        var instance: IslandNotificationListener? = null
            private set
    }
}
