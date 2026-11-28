package dev.jotnar.materialisland.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Actualizaciones desde las releases de GitHub: mira la última versión, baja el APK, comprueba su SHA-256
 * (el de las notas de la release) y se lo pasa al instalador de Android, que además exige la misma firma.
 */
object Updater {
    private const val TAG = "Updater"
    private const val API = "https://api.github.com/repos/JotnarDev/material-island/releases/latest"
    private const val UA = "MaterialIsland-Updater"

    data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val sha256: String?, val page: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var checkedThisRun = false

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** 1.10.0 > 1.9.2: compara número a número. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Comprobación automática: una vez por apertura de la app. */
    suspend fun autoCheck(ctx: Context) {
        if (checkedThisRun) return
        checkedThisRun = true
        check(ctx, quiet = true)
    }

    suspend fun check(ctx: Context, quiet: Boolean = false) {
        val busy = _state.value
        if (busy is State.Downloading || busy is State.Installing) return
        if (!quiet) _state.value = State.Checking
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (c.responseCode != 200) error("GitHub answered ${c.responseCode}")
                    val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    val assets = o.getJSONArray("assets")
                    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.getString("name").let { n -> n.endsWith(".apk") && !n.endsWith("-fdroid.apk") } } ?: error("The release has no APK")
                    val body = o.optString("body")
                    Release(
                        version = o.getString("tag_name").removePrefix("v"),
                        notes = body.substringBefore("**SHA-256").trim(),
                        apkUrl = apk.getString("browser_download_url"),
                        size = apk.optLong("size"),
                        sha256 = Regex("SHA-256:\\*\\*\\s*`([0-9a-fA-F]{64})`").find(body)?.groupValues?.get(1)?.lowercase(),
                        page = o.optString("html_url"),
                    )
                } finally { c.disconnect() }
            }
        }
        _state.value = r.fold(
            { if (isNewer(it.version, currentVersion(ctx))) State.Available(it) else if (quiet) State.Idle else State.UpToDate },
            { Log.w(TAG, "check", it); if (quiet) State.Idle else State.Failed("Couldn't check for updates. Are you online?") },
        )
    }

    /** ¿Puede esta app pedir instalar APKs? (En Xiaomi y Android 8+ hay que permitirlo una vez.) */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    suspend fun downloadAndInstall(ctx: Context, r: Release) {
        val app = ctx.applicationContext
        _state.value = State.Downloading(r, 0f)
        val file = File(app.cacheDir, "update.apk")
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(r.apkUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", UA)
                try {
                    if (c.responseCode != 200) error("Download failed (${c.responseCode})")
                    val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                    val md = MessageDigest.getInstance("SHA-256")
                    c.inputStream.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L; var last = 0L
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); md.update(buf, 0, n); done += n
                                if (total > 0 && done - last > 128 * 1024) {
                                    last = done; _state.value = State.Downloading(r, done.toFloat() / total)
                                }
                            }
                        }
                    }
                    val hash = md.digest().joinToString("") { "%02x".format(it) }
                    if (r.sha256 != null && hash != r.sha256) error("The download doesn't match the release checksum")
                } finally { c.disconnect() }
            }
        }
        ok.onFailure {
            Log.w(TAG, "download", it); file.delete()
            _state.value = State.Failed(it.message ?: "Download failed", r); return
        }
        _state.value = State.Installing(r)
        withContext(Dispatchers.IO) {
            runCatching {
                val pi = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(file.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    file.inputStream().use { input -> s.openWrite("base.apk", 0, file.length()).use { input.copyTo(it); s.fsync(it) } }
                    val intent = Intent(app, InstallResultReceiver::class.java)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    s.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
                }
            }.onFailure {
                Log.w(TAG, "install", it)
                _state.value = State.Failed("Couldn't start the installer", r)
            }
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val r = (_state.value as? State.Installing)?.release
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) (r?.let { State.Available(it) } ?: State.Idle)
            else State.Failed(message ?: "Install failed", r)
        }
    }
}

/** Respuesta del instalador: abre la confirmación si Android la pide, o informa del error. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
            ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
