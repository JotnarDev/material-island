package dev.jotnar.materialisland.core

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import dev.jotnar.materialisland.data.IslandSettings
import dev.jotnar.materialisland.data.NotificationMode
import dev.jotnar.materialisland.model.ActionStyle
import dev.jotnar.materialisland.model.IslandAction
import dev.jotnar.materialisland.model.IslandItem
import dev.jotnar.materialisland.model.Kind
import java.util.Objects

/** Convierte una notificación en algo que la isla sabe pintar. */
object NotificationParser {

    sealed interface Result {
        /** Actividad en curso (llamada, temporizador, navegación...). */
        data class Live(val item: IslandItem) : Result
        /** Aviso de unos segundos (mensaje, código, pago...). */
        data class Peek(val item: IslandItem) : Result
        data object Ignore : Result
    }

    private val PAYMENT_APPS = setOf(
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.paisa.user",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
    )
    private val PAYMENT_WORDS = Regex(
        "(?i)\\b(paid|payment|pago|pagado|pagaste|compra|purchase|transacci[oó]n|transaction|cobro|charged)\\b",
    )
    private val AMOUNT = Regex("(US\\$|CA\\$|MX\\$|\\$|€|£|₹)\\s?[0-9][0-9.,]*")
    private val RECORDER = Regex("(?i)(screenrecorder|soundrecorder|voicerecorder|recorder)")

    // Inspirado en el diccionario de LiveBridge: palabras que delatan un código de verificación.
    private val OTP_TRIGGER = Regex(
        "(?i)(c[oó]digo|code|otp|verif|contrase[nñ]a|password|passcode|pin\\b|clave|token|2fa|autenticaci[oó]n|authentication|login|inicio de sesi[oó]n|one[- ]time)",
    )
    private val OTP_CODE = listOf(
        Regex("(?<![\\d$€£.,])(\\d{3}[- ]\\d{3})(?![\\d%])"),
        Regex("\\b[A-Z]-(\\d{4,8})\\b"),
        Regex("(?<![\\d$€£.,:/])(\\d{4,8})(?![\\d%:/])"),
    )

    private val iconCache = HashMap<String, Bitmap>()
    private val labelCache = HashMap<String, String>()

    fun appIcon(ctx: Context, pkg: String): Bitmap? = iconCache[pkg] ?: runCatching {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
    }.getOrNull()?.also { iconCache[pkg] = it }

    fun appLabel(ctx: Context, pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    fun extractOtp(text: String): String? {
        if (!OTP_TRIGGER.containsMatchIn(text)) return null
        for (re in OTP_CODE) {
            val m = re.find(text) ?: continue
            val code = m.groupValues[1].replace(" ", "").replace("-", "")
            // Evita años y cantidades típicas.
            if (code.length == 4 && code.toIntOrNull() in 1900..2100) continue
            return code
        }
        return null
    }

    fun parse(
        ctx: Context,
        sbn: StatusBarNotification,
        s: IslandSettings,
        canPeek: Boolean,
    ): Result {
        val n = sbn.notification ?: return Result.Ignore
        val pkg = sbn.packageName
        if (pkg == ctx.packageName || pkg == "android" || pkg == "com.android.systemui") return Result.Ignore
        if (pkg in s.blockedApps) return Result.Ignore
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return Result.Ignore
        val ex: Bundle = n.extras ?: Bundle()
        // La música va por MediaSession, que trae carátula y controles.
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return Result.Ignore

        val ongoing = (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0
        val title = (ex.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val label = appLabel(ctx, pkg)
        val icon = appIcon(ctx, pkg)
        val large = largeIcon(ctx, n)
        val accent = if (n.color != Notification.COLOR_DEFAULT && n.color != 0) n.color or 0xFF000000.toInt() else null
        val callType = ex.getInt("android.callType", 0) // CallStyle: 1 entrante, 2 en curso, 3 filtrando
        val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val p = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val cat = n.category
        val actions = n.actions?.map { toAction(ctx, it) } ?: emptyList()
        val id = "n:" + sbn.key
        val now = System.currentTimeMillis()
        val shortCritical = ex.getCharSequence("android.shortCriticalText")?.toString()

        // ── Llamadas ─────────────────────────────────────────────────────
        if (cat == Notification.CATEGORY_CALL || callType != 0) {
            if (!s.calls) return Result.Ignore
            val incoming = callType == 1 || (callType == 0 && actions.any { it.style == ActionStyle.POSITIVE })
            val caller = personName(ex) ?: title ?: label
            return Result.Live(
                IslandItem(
                    id = id, kind = Kind.CALL, pkg = pkg, appLabel = label, appIcon = icon,
                    title = caller, text = if (incoming) "Llamada entrante · $label" else (text ?: label),
                    image = large, incoming = incoming,
                    chronoBase = if (!incoming && n.`when` in 1..now) n.`when` else 0L,
                    actions = callActions(ex, actions), contentIntent = n.contentIntent,
                    autoExpand = incoming, contentHash = Objects.hash(caller, incoming),
                ),
            )
        }

        // ── Grabación de pantalla / voz ──────────────────────────────────
        if (ongoing && s.recording && RECORDER.containsMatchIn(pkg)) {
            val screen = pkg.contains("screen", ignoreCase = true)
            return Result.Live(
                IslandItem(
                    id = id, kind = Kind.RECORDING, pkg = pkg, appLabel = label, appIcon = icon,
                    title = if (screen) "Grabando pantalla" else (title ?: "Grabando"), text = text,
                    chronoBase = if (chrono && !countDown && n.`when` in 1..now) n.`when` else 0L,
                    actions = actions.take(3), contentIntent = n.contentIntent,
                    contentHash = Objects.hash(title, n.`when`),
                ),
            )
        }

        // ── Navegación ───────────────────────────────────────────────────
        if (cat == Notification.CATEGORY_NAVIGATION && ongoing) {
            if (!s.navigation) return Result.Ignore
            return Result.Live(
                IslandItem(
                    id = id, kind = Kind.NAVIGATION, pkg = pkg, appLabel = label, appIcon = icon,
                    title = title ?: label, text = text, image = large, accent = accent,
                    compactText = shortCritical ?: title?.substringBefore(" "),
                    actions = actions.take(2), contentIntent = n.contentIntent,
                    contentHash = Objects.hash(title, text),
                ),
            )
        }

        // ── Temporizadores y cronómetros ─────────────────────────────────
        val isClock = pkg.contains("clock", ignoreCase = true) || pkg.contains("deskclock", ignoreCase = true)
        if (ongoing && ((cat == Notification.CATEGORY_STOPWATCH || chrono) && n.`when` > 0 ||
                (isClock && (cat == Notification.CATEGORY_ALARM || cat == Notification.CATEGORY_STOPWATCH || cat == Notification.CATEGORY_PROGRESS)))
        ) {
            if (!s.timers) return Result.Ignore
            return Result.Live(
                IslandItem(
                    id = id, kind = Kind.TIMER, pkg = pkg, appLabel = label, appIcon = icon,
                    title = title ?: label, text = text, accent = accent,
                    chronoBase = if (chrono) n.`when` else 0L, countDown = countDown,
                    compactText = if (!chrono) (title ?: text) else null,
                    actions = actions.take(3), contentIntent = n.contentIntent,
                    contentHash = Objects.hash(title, text, n.`when`, countDown),
                ),
            )
        }

        // ── Live Updates de Android 16+ y apps "en vivo" ─────────────────
        val promoted = ex.getBoolean("android.requestPromotedOngoing", false)
        if (ongoing && promoted && (title != null || text != null)) {
            if (!s.liveUpdates) return Result.Ignore
            return Result.Live(
                IslandItem(
                    id = id, kind = Kind.LIVE, pkg = pkg, appLabel = label, appIcon = icon,
                    title = title ?: label, text = text, image = large, accent = accent,
                    compactText = shortCritical ?: title,
                    progress = if (pMax > 0) p.toFloat() / pMax else -1f, indeterminate = indeterminate,
                    actions = actions.take(3), contentIntent = n.contentIntent,
                    contentHash = Objects.hash(title, text, p),
                ),
            )
        }

        // ── Progreso (descargas, subidas, copias...) ─────────────────────
        if ((pMax > 0 || indeterminate) && (ongoing || cat == Notification.CATEGORY_PROGRESS)) {
            if (!s.progress) return Result.Ignore
            val frac = if (pMax > 0) p.toFloat() / pMax else -1f
            return Result.Live(
                IslandItem(
                    id = id, kind = Kind.PROGRESS, pkg = pkg, appLabel = label, appIcon = icon,
                    title = title ?: label, text = text, accent = accent,
                    progress = frac, indeterminate = indeterminate || pMax == 0,
                    actions = actions.take(3), contentIntent = n.contentIntent,
                    contentHash = Objects.hash(title, (frac * 100).toInt()),
                ),
            )
        }

        if (ongoing || !canPeek) return Result.Ignore

        // ── A partir de aquí: avisos de unos segundos ────────────────────
        val all = listOfNotNull(title, text).joinToString(" ")

        // Códigos de verificación (idea de LiveBridge).
        if (s.otp) {
            extractOtp(all)?.let { code ->
                if (s.otpAutoCopy) copyToClipboard(ctx, code)
                return Result.Peek(
                    IslandItem(
                        id = id, kind = Kind.OTP, pkg = pkg, appLabel = label, appIcon = icon,
                        title = if (s.otpAutoCopy) "Código copiado" else "Código · $label",
                        text = all, otp = code, value = code,
                        contentIntent = n.contentIntent,
                        actions = listOf(IslandAction("Copiar", null, onClick = { copyToClipboard(ctx, code) })),
                        durationMs = maxOf(8000L, s.peekSeconds * 1000L),
                        contentHash = code.hashCode(),
                    ),
                )
            }
        }

        // Pagos con Wallet y similares.
        if (s.payments && pkg in PAYMENT_APPS && PAYMENT_WORDS.containsMatchIn(all)) {
            return Result.Peek(
                IslandItem(
                    id = id, kind = Kind.PAYMENT, pkg = pkg, appLabel = label, appIcon = icon,
                    title = "Pago hecho", text = all, value = AMOUNT.find(all)?.value ?: "Listo",
                    contentIntent = n.contentIntent, durationMs = 3500,
                    contentHash = all.hashCode(),
                ),
            )
        }

        if (s.notifications == NotificationMode.NONE) return Result.Ignore

        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val isMessage = cat == Notification.CATEGORY_MESSAGE || style != null
        if (s.notifications == NotificationMode.MESSAGES && !isMessage) return Result.Ignore

        var t = title
        var body = text
        var avatar = large
        val last = style?.messages?.lastOrNull()
        if (style != null && last != null) {
            val sender = last.person?.name?.toString()
            body = last.text?.toString()
            t = style.conversationTitle?.toString() ?: sender ?: title
            if (style.isGroupConversation && sender != null) body = "$sender: ${last.text}"
            runCatching { last.person?.icon?.loadDrawable(ctx)?.let { avatar = it.toBitmap(128, 128) } }
        }
        if (t == null && body == null) return Result.Ignore

        return Result.Peek(
            IslandItem(
                id = id, kind = Kind.NOTIFICATION, pkg = pkg, appLabel = label, appIcon = icon,
                title = t ?: label, text = body, image = avatar, accent = accent,
                actions = actions.take(3), contentIntent = n.contentIntent,
                durationMs = s.peekSeconds * 1000L,
                contentHash = Objects.hash(t, body),
            ),
        )
    }

    fun copyToClipboard(ctx: Context, text: String) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Código", text))
    }

    private fun largeIcon(ctx: Context, n: Notification): Bitmap? =
        runCatching { n.getLargeIcon()?.loadDrawable(ctx)?.toBitmap(128, 128) }.getOrNull()

    @Suppress("DEPRECATION")
    private fun personName(ex: Bundle): String? =
        runCatching { (ex.getParcelable("android.callPerson") as? android.app.Person)?.name?.toString() }.getOrNull()

    private fun styleFor(title: String): ActionStyle {
        val t = title.lowercase()
        return when {
            listOf("answer", "accept", "contestar", "responder llamada", "aceptar").any { it in t } -> ActionStyle.POSITIVE
            listOf("decline", "reject", "hang up", "end call", "rechazar", "colgar", "finalizar").any { it in t } -> ActionStyle.NEGATIVE
            else -> ActionStyle.NORMAL
        }
    }

    private fun toAction(ctx: Context, a: Notification.Action): IslandAction {
        val title = a.title?.toString() ?: ""
        val inputs = a.remoteInputs?.filter { it.allowFreeFormInput }
        return if (!inputs.isNullOrEmpty()) {
            val all = a.remoteInputs
            IslandAction(title = title, intent = a.actionIntent, onReply = { txt ->
                sendRemoteInput(ctx.applicationContext, a.actionIntent, all, txt)
            })
        } else {
            IslandAction(title = title, intent = a.actionIntent, style = styleFor(title))
        }
    }

    private fun sendRemoteInput(ctx: Context, pi: PendingIntent?, inputs: Array<RemoteInput>, text: String) {
        pi ?: return
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val results = Bundle()
        inputs.filter { it.allowFreeFormInput }.forEach { results.putCharSequence(it.resultKey, text) }
        RemoteInput.addResultsToIntent(inputs, intent, results)
        runCatching { pi.send(ctx, 0, intent) }
    }

    @Suppress("DEPRECATION")
    private fun callActions(ex: Bundle, fallback: List<IslandAction>): List<IslandAction> {
        fun pi(key: String) = runCatching { ex.getParcelable(key) as? PendingIntent }.getOrNull()
        val list = mutableListOf<IslandAction>()
        pi("android.declineIntent")?.let { list += IslandAction("Rechazar", it, ActionStyle.NEGATIVE) }
        pi("android.hangUpIntent")?.let { list += IslandAction("Colgar", it, ActionStyle.NEGATIVE) }
        pi("android.answerIntent")?.let { list += IslandAction("Contestar", it, ActionStyle.POSITIVE) }
        return if (list.isNotEmpty()) list + fallback.filter { it.style == ActionStyle.NORMAL }.take(2) else fallback.take(3)
    }
}
