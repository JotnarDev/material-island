package dev.jotnar.materialisland.core

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.palette.graphics.Palette
import dev.jotnar.materialisland.data.SettingsStore
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind
import dev.jotnar.materialisland.model.MediaInfo

/**
 * Sigue las sesiones multimedia activas y publica la que suena (o la última pausada, durante unos
 * minutos) como actividad MEDIA.
 */
class MediaMonitor(private val ctx: Context, private val listener: ComponentName) {
    private val handler = Handler(Looper.getMainLooper())
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private var shownPkg: String? = null
    private var lastArt: Bitmap? = null
    private var lastArtColor: Int? = null
    private val pausedSince = HashMap<String, Long>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    private val expiryCheck = object : Runnable {
        override fun run() {
            publish()
            handler.postDelayed(this, 30_000)
        }
    }

    fun start() {
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listener, handler)
            attach(msm.getActiveSessions(listener))
        }
        handler.postDelayed(expiryCheck, 30_000)
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        handler.removeCallbacksAndMessages(null)
        IslandStore.remove(ID)
    }

    private fun attach(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it.sessionToken !in tokens }.forEach { c ->
            callbacks.remove(c)?.let { c.unregisterCallback(it) }
        }
        list.forEach { c ->
            if (callbacks.keys.none { it.sessionToken == c.sessionToken }) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                    override fun onSessionDestroyed() {
                        callbacks.remove(c)
                        publish()
                    }
                }
                c.registerCallback(cb, handler)
                callbacks[c] = cb
            }
        }
        publish()
    }

    private fun isPlaying(c: MediaController) = when (c.playbackState?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> true
        else -> false
    }

    private fun publish() {
        val s = SettingsStore.current
        if (!s.media) {
            IslandStore.remove(ID); return
        }
        val now = SystemClock.elapsedRealtime()
        val controllers = callbacks.keys.filter { it.metadata != null && it.packageName !in s.blockedApps }
        controllers.forEach { c ->
            if (isPlaying(c)) pausedSince.remove(c.packageName)
            else pausedSince.putIfAbsent(c.packageName, now)
        }
        val graceMs = s.pausedMediaMinutes * 60_000L
        val chosen = controllers.firstOrNull { isPlaying(it) }
            ?: controllers.firstOrNull { it.packageName == shownPkg && now - (pausedSince[it.packageName] ?: now) < graceMs }
            ?: controllers.filter { now - (pausedSince[it.packageName] ?: now) < graceMs }
                .minByOrNull { now - (pausedSince[it.packageName] ?: now) }
        if (chosen == null) {
            shownPkg = null
            IslandStore.remove(ID)
            return
        }
        shownPkg = chosen.packageName
        val md = chosen.metadata ?: return
        val st = chosen.playbackState
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val color = artColor(art)
        val playing = isPlaying(chosen)
        val label = NotificationParser.appLabel(ctx, chosen.packageName)

        IslandStore.upsert(
            IslandItem(
                id = ID, kind = Kind.MEDIA, pkg = chosen.packageName, appLabel = label,
                appIcon = NotificationParser.appIcon(ctx, chosen.packageName),
                title = title, text = artist, image = art, accent = color,
                contentIntent = chosen.sessionActivity,
                media = MediaInfo(
                    title = title, artist = artist, art = art, artColor = color,
                    durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    positionMs = st?.position ?: 0L,
                    positionUpdatedAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: now,
                    speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                    playing = playing, controller = chosen,
                ),
            ),
        )
    }

    private fun artColor(art: Bitmap?): Int? {
        if (art == null) return null
        if (art === lastArt) return lastArtColor
        val p = runCatching { Palette.from(art).maximumColorCount(16).generate() }.getOrNull()
        val c = p?.vibrantSwatch?.rgb ?: p?.lightVibrantSwatch?.rgb ?: p?.dominantSwatch?.rgb
        lastArt = art
        lastArtColor = c
        return c
    }

    companion object {
        const val ID = "media"
    }
}
