package dev.jotnar.materialisland.core

import android.app.AlarmManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dev.jotnar.materialisland.data.GlanceView
import dev.jotnar.materialisland.data.IslandSettings
import dev.jotnar.materialisland.data.League
import dev.jotnar.materialisland.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlin.math.abs

/**
 * Datos de At a glance: clima (Open-Meteo), marcadores (ESPN) y bolsa (Yahoo Finance).
 * Es lo único de la app que usa internet, solo si el usuario lo activa, sin cuentas ni rastreo.
 * Sin conexión simplemente no hay datos y el resto de la isla sigue igual.
 */
object Glance {
    private const val TAG = "Glance"
    private const val UA = "Mozilla/5.0 (Linux; Android) MaterialIsland"

    data class Hour(val time: Long, val temp: Double, val code: Int, val isDay: Boolean, val rainChance: Int)
    data class Weather(
        val temp: Double, val code: Int, val isDay: Boolean, val high: Double, val low: Double,
        val tomorrowLow: Double, val uvMax: Double, val place: String, val hours: List<Hour>, val fahrenheit: Boolean,
    )
    data class Team(val abbr: String, val name: String, val score: String?, val logo: Bitmap?, val favorite: Boolean)
    data class Game(
        val league: String, val sport: String, val home: Team, val away: Team,
        /** pre / in / post */
        val state: String, val detail: String, val start: Long,
    ) {
        val live get() = state == "in"
        val favorite get() = home.favorite || away.favorite
    }
    data class Quote(
        val symbol: String, val name: String, val price: Double, val changePct: Double, val currency: String,
        val closes: List<Float>, val prevClose: Double, val logo: Bitmap? = null,
    ) {
        val change get() = price - prevClose
    }
    enum class AlertKind { ALARM, RAIN, SNOW, UV, COLD, HEAT, LIVE, MARKET_UP, MARKET_DOWN }
    data class Alert(val kind: AlertKind, val title: String, val text: String)

    data class Data(
        val weather: Weather? = null,
        val games: List<Game> = emptyList(),
        val quotes: List<Quote> = emptyList(),
        val alerts: List<Alert> = emptyList(),
        val online: Boolean = true,
        val updatedAt: Long = 0,
    ) {
        val hasAny get() = weather != null || games.isNotEmpty() || quotes.isNotEmpty() || alerts.isNotEmpty()
        /** Partido en vivo para la píldora: primero los de equipos favoritos. */
        val liveGame get() = games.firstOrNull { it.live && it.favorite } ?: games.firstOrNull { it.live }
    }

    private val _data = MutableStateFlow(Data())
    val data: StateFlow<Data> = _data.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val kick = Channel<Boolean>(Channel.CONFLATED)
    @Volatile var cardOpen = false

    private var weatherAt = 0L
    private var sportsAt = 0L
    private var financeAt = 0L
    private var lastKey = ""
    private val logos = HashMap<String, Bitmap?>()

    fun start(ctx: Context) {
        if (loop != null) return
        val app = ctx.applicationContext
        loop = scope.launch {
            while (true) {
                runCatching { tick(app) }.onFailure { Log.w(TAG, "tick", it) }
                // Cada minuto, o antes si se abre la tarjeta o cambian los ajustes.
                withTimeoutOrNull(60_000) { kick.receive() }
            }
        }
    }

    fun stop() { loop?.cancel(); loop = null }

    /** Pide datos frescos (al abrir la tarjeta o cambiar los ajustes). */
    fun refresh(force: Boolean = false) {
        if (force) { weatherAt = 0; sportsAt = 0; financeAt = 0 }
        kick.trySend(true)
    }

    private fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun tick(ctx: Context) {
        val s = SettingsStore.current
        if (!s.glanceEnabled) {
            if (_data.value.hasAny) _data.value = Data()
            weatherAt = 0; sportsAt = 0; financeAt = 0
            return
        }
        // Si cambian los ajustes de At a glance, se vuelve a pedir todo.
        val key = listOf(s.glanceViews, s.glanceLat, s.glanceLon, s.glanceFahrenheit, s.glanceLeagues, s.glanceTeams, s.glanceSymbols).toString()
        if (key != lastKey) { lastKey = key; weatherAt = 0; sportsAt = 0; financeAt = 0 }

        val now = System.currentTimeMillis()
        if (!online(ctx)) {
            _data.value = _data.value.copy(online = false)
            return
        }
        var d = _data.value.copy(online = true)
        val views = s.glanceViews.toSet()
        val needWeather = GlanceView.WEATHER in views || GlanceView.ALERTS in views

        if (needWeather && s.glanceLat != null && s.glanceLon != null && now - weatherAt > 30 * 60_000) {
            fetchWeather(s)?.let { d = d.copy(weather = it); weatherAt = now }
        }
        if (!needWeather || s.glanceLat == null) d = d.copy(weather = null)

        if (GlanceView.SPORTS in views || GlanceView.ALERTS in views) {
            // En vivo: cada minuto. Sin partidos en juego: cada 10 minutos.
            val every = if (d.games.any { it.live }) 60_000L else 10 * 60_000L
            if (now - sportsAt > every - 5_000) {
                d = d.copy(games = fetchSports(s)); sportsAt = now
            }
        } else d = d.copy(games = emptyList())

        if (GlanceView.FINANCE in views || GlanceView.ALERTS in views) {
            val every = if (cardOpen) 2 * 60_000L else 15 * 60_000L
            if (now - financeAt > every - 5_000) {
                fetchQuotes(ctx, s).takeIf { it.isNotEmpty() }?.let { d = d.copy(quotes = it) }; financeAt = now
            }
        } else d = d.copy(quotes = emptyList())

        d = d.copy(alerts = buildAlerts(ctx, s, d), updatedAt = now)
        _data.value = d
    }

    // ── Red ─────────────────────────────────────────────────────────────

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }.onFailure { Log.w(TAG, "GET ${url.substringBefore('?')}: ${it.message}") }.getOrNull()

    private fun logo(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        synchronized(logos) { if (logos.containsKey(url)) return logos[url] }
        val bmp = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", UA)
            try {
                c.inputStream.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
            } finally { c.disconnect() }
        }.getOrNull()
        synchronized(logos) { logos[url] = bmp }
        return bmp
    }

    // ── Clima ───────────────────────────────────────────────────────────

    private fun fetchWeather(s: IslandSettings): Weather? {
        val unit = if (s.glanceFahrenheit) "&temperature_unit=fahrenheit" else ""
        val json = get(
            "https://api.open-meteo.com/v1/forecast?latitude=${s.glanceLat}&longitude=${s.glanceLon}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,uv_index_max" +
                "&timezone=auto&forecast_days=2&timeformat=unixtime$unit",
        ) ?: return null
        return runCatching {
            val o = JSONObject(json)
            val cur = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            val h = o.getJSONObject("hourly")
            val now = System.currentTimeMillis() / 1000
            val times = h.getJSONArray("time")
            val hours = (0 until times.length()).map { i ->
                Hour(
                    times.getLong(i) * 1000, h.getJSONArray("temperature_2m").optDouble(i),
                    h.getJSONArray("weather_code").optInt(i), h.getJSONArray("is_day").optInt(i) == 1,
                    h.getJSONArray("precipitation_probability").optInt(i),
                )
            }.filter { it.time / 1000 >= now - 1800 }.take(24)
            Weather(
                temp = cur.getDouble("temperature_2m"), code = cur.getInt("weather_code"), isDay = cur.optInt("is_day", 1) == 1,
                high = daily.getJSONArray("temperature_2m_max").getDouble(0),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0),
                tomorrowLow = daily.getJSONArray("temperature_2m_min").optDouble(1, Double.NaN),
                uvMax = daily.getJSONArray("uv_index_max").optDouble(0, 0.0),
                place = s.glancePlace, hours = hours, fahrenheit = s.glanceFahrenheit,
            )
        }.onFailure { Log.w(TAG, "weather parse", it) }.getOrNull()
    }

    /** Busca una ciudad (para los ajustes). Devuelve nombre, latitud y longitud. */
    fun searchCity(name: String): Triple<String, Double, Double>? {
        val json = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name.trim(), "UTF-8")) ?: return null
        return runCatching {
            val r = JSONObject(json).getJSONArray("results").getJSONObject(0)
            val label = listOfNotNull(r.optString("name").takeIf { it.isNotBlank() }, r.optString("admin1").takeIf { it.isNotBlank() })
                .distinct().joinToString(", ")
            Triple(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }.getOrNull()
    }

    // ── Deportes ────────────────────────────────────────────────────────

    private class RawGame(val game: Game, val homeLogo: String, val awayLogo: String)

    private fun fetchSports(s: IslandSettings): List<Game> {
        val fav = s.glanceTeams.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        fun isFav(abbr: String, name: String) = fav.any { f -> abbr.lowercase() == f || name.lowercase().contains(f) }
        val raw = League.entries.filter { it.path in s.glanceLeagues }.flatMap { lg ->
            val json = get("https://site.api.espn.com/apis/site/v2/sports/${lg.path}/scoreboard") ?: return@flatMap emptyList()
            runCatching {
                val evs = JSONObject(json).optJSONArray("events") ?: return@runCatching emptyList<RawGame>()
                (0 until evs.length()).mapNotNull { i ->
                    val ev = evs.getJSONObject(i)
                    val cs = ev.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors")
                    var home: Pair<Team, String>? = null
                    var away: Pair<Team, String>? = null
                    for (j in 0 until cs.length()) {
                        val c = cs.getJSONObject(j); val t = c.getJSONObject("team")
                        val abbr = t.optString("abbreviation")
                        val team = Team(
                            abbr, t.optString("shortDisplayName", t.optString("displayName")),
                            c.optString("score").takeIf { it.isNotBlank() }, null, isFav(abbr, t.optString("displayName")),
                        )
                        if (c.optString("homeAway") == "home") home = team to t.optString("logo") else away = team to t.optString("logo")
                    }
                    val h = home ?: return@mapNotNull null
                    val a = away ?: return@mapNotNull null
                    val st = ev.getJSONObject("status").getJSONObject("type")
                    // ESPN da "2026-11-25T20:00Z" (sin segundos).
                    val date = ev.optString("date").let { if (it.length == 17) it.dropLast(1) + ":00Z" else it }
                    RawGame(
                        Game(lg.label, lg.sport, h.first, a.first, st.optString("state"), st.optString("shortDetail"),
                            runCatching { Instant.parse(date).toEpochMilli() }.getOrDefault(0L)),
                        h.second, a.second,
                    )
                }
            }.onFailure { Log.w(TAG, "sports ${lg.path}", it) }.getOrDefault(emptyList())
        }
        val filtered = if (fav.isEmpty()) raw else raw.filter { it.game.favorite }
        // En vivo, luego próximos, luego terminados; favoritos primero dentro de cada grupo.
        val rank = mapOf("in" to 0, "pre" to 1, "post" to 2)
        return filtered
            .sortedWith(compareBy({ rank[it.game.state] ?: 3 }, { !it.game.favorite }, { if (it.game.state == "post") -it.game.start else it.game.start }))
            .take(4)
            .map { r -> r.game.copy(home = r.game.home.copy(logo = logo(r.homeLogo)), away = r.game.away.copy(logo = logo(r.awayLogo))) }
    }

    // ── Bolsa ───────────────────────────────────────────────────────────

    /** Dominios de empresas cuyo nombre no lleva a su web (para el logo). El resto se deduce del nombre. */
    private val domains = mapOf(
        "GOOGL" to "google.com", "GOOG" to "google.com", "META" to "meta.com", "BRK-B" to "berkshirehathaway.com",
        "BRK-A" to "berkshirehathaway.com", "AMD" to "amd.com", "DIS" to "disney.com", "KO" to "coca-cola.com",
        "PEP" to "pepsico.com", "JPM" to "jpmorganchase.com", "BAC" to "bankofamerica.com", "WFC" to "wellsfargo.com",
        "GS" to "goldmansachs.com", "MS" to "morganstanley.com", "V" to "visa.com", "MA" to "mastercard.com",
        "PG" to "pg.com", "JNJ" to "jnj.com", "UNH" to "unitedhealthgroup.com", "XOM" to "exxonmobil.com",
        "CVX" to "chevron.com", "WMT" to "walmart.com", "HD" to "homedepot.com", "MCD" to "mcdonalds.com",
        "SBUX" to "starbucks.com", "TSM" to "tsmc.com", "ASML" to "asml.com", "AVGO" to "broadcom.com",
        "CRM" to "salesforce.com", "ORCL" to "oracle.com", "IBM" to "ibm.com", "INTC" to "intel.com",
        "QCOM" to "qualcomm.com", "TXN" to "ti.com", "CSCO" to "cisco.com", "ADBE" to "adobe.com",
        "PYPL" to "paypal.com", "SQ" to "block.xyz", "XYZ" to "block.xyz", "UBER" to "uber.com", "ABNB" to "airbnb.com",
        "SHOP" to "shopify.com", "SPOT" to "spotify.com", "BABA" to "alibaba.com", "TM" to "toyota.com",
        "SONY" to "sony.com", "NTDOY" to "nintendo.com", "PLTR" to "palantir.com", "COIN" to "coinbase.com",
        "RIVN" to "rivian.com", "F" to "ford.com", "GM" to "gm.com", "T" to "att.com", "VZ" to "verizon.com",
        "TMUS" to "t-mobile.com", "RY" to "rbc.com", "TD" to "td.com", "SHOP.TO" to "shopify.com", "BB" to "blackberry.com",
        "LLY" to "lilly.com", "PFE" to "pfizer.com", "MRK" to "merck.com", "ABBV" to "abbvie.com", "NVO" to "novonordisk.com",
        "COST" to "costco.com", "TGT" to "target.com", "LULU" to "lululemon.com", "ARM" to "arm.com", "MU" to "micron.com",
        "SNOW" to "snowflake.com", "NET" to "cloudflare.com", "RBLX" to "roblox.com", "EA" to "ea.com",
        "TTWO" to "take2games.com", "SPY" to "ssga.com", "QQQ" to "invesco.com", "VOO" to "vanguard.com",
    )

    /** Web de la empresa: la tabla o, si no está, el primer nombre de la empresa + .com (Nvidia → nvidia.com). */
    private fun domainFor(symbol: String, name: String): String? {
        domains[symbol]?.let { return it }
        // Índices, cripto y divisas llevan su propio icono.
        if (symbol.startsWith("^") || symbol.endsWith("=X") || symbol.contains("-USD") || symbol.contains("-EUR")) return null
        val first = name.lowercase().split(' ', ',').firstOrNull { it.isNotBlank() } ?: return null
        val base = first.removeSuffix(".com").filter { it.isLetterOrDigit() || it == '-' }
        return base.takeIf { it.length >= 2 }?.let { "$it.com" }
    }

    /**
     * Logo real de la empresa con el servicio de iconos de Google (el mismo que usa Chrome). Se baja una vez y
     * se guarda en caché; si Google no lo conoce (404) queda la inicial de color.
     */
    private fun companyLogo(ctx: Context, symbol: String, name: String): Bitmap? {
        synchronized(logos) { if (logos.containsKey("q:$symbol")) return logos["q:$symbol"] }
        val dir = java.io.File(ctx.cacheDir, "logos").apply { mkdirs() }
        val file = java.io.File(dir, symbol.filter { it.isLetterOrDigit() || it == '-' || it == '.' } + ".png")
        val missing = java.io.File(dir, file.name + ".none")
        val week = 7 * 24 * 3_600_000L
        val bmp: Bitmap? = when {
            file.exists() -> BitmapFactory.decodeFile(file.path)
            missing.exists() && System.currentTimeMillis() - missing.lastModified() < week -> null
            else -> {
                val domain = domainFor(symbol, name)
                val got = domain?.let {
                    runCatching {
                        val c = URL(
                            "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&size=128&url=https://$it",
                        ).openConnection() as HttpURLConnection
                        c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", UA)
                        try {
                            if (c.responseCode != 200) null else c.inputStream.use { st -> st.readBytes() }
                        } finally { c.disconnect() }
                    }.getOrNull()
                }
                val decoded = got?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.takeIf { it.width >= 32 }
                if (decoded != null) file.writeBytes(got!!) else if (domain != null) missing.writeText("")
                decoded
            }
        }
        synchronized(logos) { logos["q:$symbol"] = bmp }
        return bmp
    }

    private fun fetchQuotes(ctx: Context, s: IslandSettings): List<Quote> =
        s.glanceSymbols.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.take(4).mapNotNull { sym ->
            val json = get("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(sym, "UTF-8") + "?range=1d&interval=15m")
                ?: return@mapNotNull null
            runCatching {
                val r = JSONObject(json).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val m = r.getJSONObject("meta")
                val price = m.getDouble("regularMarketPrice")
                val prev = m.optDouble("chartPreviousClose", m.optDouble("previousClose", price))
                val closesArr = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
                val closes = if (closesArr == null) emptyList() else (0 until closesArr.length())
                    .mapNotNull { i -> closesArr.optDouble(i).takeIf { !it.isNaN() }?.toFloat() }
                val name = m.optString("shortName").ifBlank { m.optString("longName") }.ifBlank { sym }
                Quote(
                    sym, name, price,
                    if (prev != 0.0) (price - prev) / prev * 100 else 0.0, m.optString("currency"), closes, prev,
                    companyLogo(ctx, sym, name),
                )
            }.onFailure { Log.w(TAG, "quote $sym", it) }.getOrNull()
        }

    // ── Alertas ─────────────────────────────────────────────────────────

    private fun buildAlerts(ctx: Context, s: IslandSettings, d: Data): List<Alert> = buildList {
        val tf = android.text.format.DateFormat.getTimeFormat(ctx)
        val now = System.currentTimeMillis()
        ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.let { a ->
            if (a.triggerTime - now in 0..24 * 3_600_000L) {
                val day = if (android.text.format.DateUtils.isToday(a.triggerTime)) "Today" else "Tomorrow"
                add(Alert(AlertKind.ALARM, "Alarm", "$day at ${tf.format(java.util.Date(a.triggerTime))}"))
            }
        }
        d.liveGame?.let { g ->
            add(Alert(AlertKind.LIVE, "Live · ${g.league}", "${g.away.abbr} ${g.away.score ?: ""} – ${g.home.score ?: ""} ${g.home.abbr} · ${g.detail}"))
        }
        d.weather?.let { w ->
            val deg = if (w.fahrenheit) "°F" else "°C"
            w.hours.take(12).firstOrNull { it.rainChance >= 50 }?.let { h ->
                val snow = h.code in 71..77 || h.code in 85..86
                add(Alert(if (snow) AlertKind.SNOW else AlertKind.RAIN, if (snow) "Snow likely" else "Rain likely",
                    "Around ${tf.format(java.util.Date(h.time))} · ${h.rainChance}% chance"))
            }
            if (w.uvMax >= 6) add(Alert(AlertKind.UV, "High UV today", "UV index up to ${w.uvMax.toInt()}"))
            val freeze = if (w.fahrenheit) 32.0 else 0.0
            val hot = if (w.fahrenheit) 90.0 else 32.0
            if (!w.tomorrowLow.isNaN() && w.tomorrowLow <= freeze) add(Alert(AlertKind.COLD, "Freezing temperatures", "Low of ${w.tomorrowLow.toInt()}$deg"))
            if (w.high >= hot) add(Alert(AlertKind.HEAT, "Hot day", "High of ${w.high.toInt()}$deg"))
        }
        d.quotes.filter { abs(it.changePct) >= 3 }.forEach { q ->
            add(Alert(if (q.changePct > 0) AlertKind.MARKET_UP else AlertKind.MARKET_DOWN, "${q.symbol} ${if (q.changePct > 0) "up" else "down"} ${"%.1f".format(abs(q.changePct))}%", q.name))
        }
    }

    // ── Texto del clima (códigos WMO) ───────────────────────────────────

    fun condition(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}
