package dev.jotnar.materialisland.overlay

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import dev.jotnar.materialisland.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jotnar.materialisland.core.Glance
import dev.jotnar.materialisland.data.GlanceView
import dev.jotnar.materialisland.data.IslandSettings
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.PathEffect

private val Live = Color(0xFFFF5449)
private val Sun = Color(0xFFFFC21A)
private val Moon = Color(0xFFCBC4FF)
private val CloudLight = Color(0xFFE9EDF2)
private val CloudDark = Color(0xFF9AA4B1)
private val RainBlue = Color(0xFF5CB8FF)
private val TempBlue = Color(0xFF8CC8FF)
private val Num = TextStyle(fontFeatureSettings = "tnum")

// ── Mini píldora a la derecha de la cámara ───────────────────────────

/**
 * La vista elegida en pequeño (clima: icono y grados; deportes: marcador; bolsa: variación; alertas: cuántas).
 * Deslizar de lado cambia de vista; tocar abre esa vista debajo.
 */
@Composable
internal fun GlanceChip(ui: IslandUi, h: Dp, cb: IslandCallbacks, modifier: Modifier) {
    val v = ui.glanceView ?: return
    val data = ui.glanceData
    val live = data.liveGame != null
    var drag by remember { mutableFloatStateOf(0f) }
    Box(modifier) {
        Box(
            Modifier.height(h).widthIn(min = h).clip(CircleShape).background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { cb.onGlanceTap() } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (abs(drag) > 14.dp.toPx()) cb.onGlanceSwipe(if (drag < 0) 1 else -1) },
                    ) { c, d -> c.consume(); drag += d }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                v,
                transitionSpec = {
                    val dir = ui.glanceDir
                    (slideInHorizontally(spring(0.8f, 600f)) { it * dir / 2 } + fadeIn(tween(160, 40)))
                        .togetherWith(slideOutHorizontally(tween(140)) { -it * dir / 2 } + fadeOut(tween(90)))
                        .using(SizeTransform(clip = true) { _, _ -> spring(0.8f, 600f) })
                },
                contentAlignment = Alignment.Center,
                label = "glanceChip",
            ) { view -> ChipContent(view, data, h) }
        }
        // Punto rojo latiendo si hay un partido en vivo y el icono muestra otra cosa.
        if (live && v != GlanceView.SPORTS) {
            val t = rememberInfiniteTransition(label = "live")
            val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "liveDot")
            Box(
                Modifier.align(Alignment.TopEnd).size(h * 0.3f).clip(CircleShape).background(Color.Black)
                    .padding(h * 0.06f).clip(CircleShape).background(Live.copy(alpha = a)),
            )
        }
    }
}

@Composable
private fun ChipContent(v: GlanceView, data: Glance.Data, h: Dp) {
    val ctx = LocalContext.current
    val icon = h * 0.58f
    var text: String? = null
    var textColor = Color.White
    val lead: @Composable () -> Unit = when (v) {
        GlanceView.WEATHER -> {
            val w = data.weather
            if (w != null) {
                text = "${w.temp.roundToInt()}°"
                ({ WeatherGlyph(w.code, w.isDay, icon) })
            } else ({ Icon(Icons.Rounded.WbSunny, null, tint = Sun, modifier = Modifier.size(icon)) })
        }
        GlanceView.CALENDAR -> {
            val e = data.events.firstOrNull()
            text = calendarChipText(ctx, e)
            val c = e?.let { Color(it.color) } ?: Color.White
            ({ Icon(Icons.Rounded.CalendarToday, null, tint = c, modifier = Modifier.size(icon * 0.82f)) })
        }
        GlanceView.SPORTS -> {
            val g = data.liveGame ?: data.games.firstOrNull()
            when {
                g == null -> ({ Icon(Icons.Rounded.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(icon)) })
                g.live -> {
                    text = "${g.away.score ?: 0}–${g.home.score ?: 0}"
                    ({
                        val t = rememberInfiniteTransition(label = "live")
                        val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "liveDot")
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Live.copy(alpha = a)))
                    })
                }
                else -> {
                    text = if (g.state == "pre") {
                        java.text.SimpleDateFormat(
                            DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(ctx)) "Hm" else "hm"),
                            Locale.getDefault(),
                        ).format(Date(g.start))
                    } else "${g.away.score ?: 0}–${g.home.score ?: 0}"
                    ({ Icon(sportIcon(g.sport), null, tint = Color.White, modifier = Modifier.size(icon * 0.85f)) })
                }
            }
        }
        GlanceView.ALERTS -> {
            val first = data.alerts.firstOrNull()
            if (first != null) {
                if (data.alerts.size > 1) text = "${data.alerts.size}"
                ({ Icon(alertIcon(first.kind), null, tint = alertTint(first.kind), modifier = Modifier.size(icon * 0.9f)) })
            } else ({ Icon(Icons.Rounded.NotificationsNone, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(icon * 0.9f)) })
        }
    }
    if (text == null) {
        Box(Modifier.size(h), contentAlignment = Alignment.Center) { lead() }
    } else {
        Row(Modifier.height(h).padding(start = 7.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            lead()
            Spacer(Modifier.width(5.dp))
            Text(
                text!!, color = textColor, maxLines = 1,
                style = MaterialTheme.typography.labelLarge.merge(Num).copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

// ── Tarjeta ───────────────────────────────────────────────────────────

/** La vista elegida, abierta debajo de la cámara. Deslizar de lado cambia de vista. */
@Composable
internal fun GlanceCard(s: IslandSettings, p: IslandPalette, d: Dims, ui: IslandUi, cb: IslandCallbacks) {
    val data = ui.glanceData
    val views = GlanceView.entries.filter { it in s.glanceViews }
    val v = ui.glanceView
    Box(Modifier.width(d.cardW)) {
        if (v == null) {
            Text(
                "Choose views in At a glance settings.", style = MaterialTheme.typography.bodyMedium, color = p.variant,
                modifier = Modifier.padding(Sp.edge),
            )
            return@Box
        }
        AnimatedContent(
            v,
            transitionSpec = {
                val dir = ui.glanceDir
                (slideInHorizontally(spring(0.85f, 500f)) { it * dir / 3 } + fadeIn(tween(200, 60)))
                    .togetherWith(slideOutHorizontally(tween(160)) { -it * dir / 3 } + fadeOut(tween(100)))
                    .using(SizeTransform(clip = true) { _, _ -> spring(0.85f, 420f) })
            },
            label = "glanceCard",
        ) { view ->
            if (view == GlanceView.CALENDAR) Padded(view, data, p) {
                CalendarPage(data.events, data.calendarAllowed, p, cb::onGlanceOpen) { t, x -> Empty(Icons.Rounded.EventAvailable, t, x, p) }
            }
            else if (!data.online && !data.hasAny) Padded(view, data, p) { Offline(p) }
            else when (view) {
                GlanceView.CALENDAR -> Unit
                GlanceView.WEATHER -> WeatherCard(data, s, p)
                GlanceView.SPORTS -> Padded(view, data, p) { SportsPage(data.games, p) }
                GlanceView.ALERTS -> Padded(view, data, p) { AlertsPage(data.alerts, p) }
            }
        }
        if (views.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)) {
                views.forEachIndexed { i, it ->
                    val on = it == v
                    val c by animateColorAsState(if (on) Color.White else Color.White.copy(alpha = 0.3f), label = "dot")
                    val w by androidx.compose.animation.core.animateDpAsState(if (on) 16.dp else 6.dp, label = "dotW")
                    Box(Modifier.padding(horizontal = 3.dp).width(w).height(6.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}

@Composable
private fun Padded(v: GlanceView, data: Glance.Data, p: IslandPalette, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = Sp.edge, end = Sp.edge, top = Sp.edge, bottom = 28.dp)) {
        Header(v, data, p)
        Spacer(Modifier.height(Sp.group))
        content()
    }
}

@Composable
private fun Header(v: GlanceView, data: Glance.Data, p: IslandPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(viewIcon(v), null, tint = p.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Caption(v.label, p.variant)
        Spacer(Modifier.weight(1f))
        if (v != GlanceView.CALENDAR) Text(updatedNote(data), style = MaterialTheme.typography.labelSmall, color = p.variant.copy(alpha = 0.7f))
    }
}

private fun updatedNote(data: Glance.Data): String = when {
    !data.online -> "Offline"
    data.updatedAt == 0L -> "Loading…"
    System.currentTimeMillis() - data.updatedAt < 60_000 -> "Updated just now"
    else -> "Updated " + DateUtils.getRelativeTimeSpanString(
        data.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString().lowercase(Locale.getDefault())
}

@Composable
private fun Offline(p: IslandPalette) = Empty(Icons.Rounded.CloudOff, "No internet", "At a glance comes back when you're online.", p)

@Composable
private fun Empty(icon: ImageVector, title: String, text: String, p: IslandPalette) {
    Column(
        Modifier.fillMaxWidth().height(132.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(Sp.action).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.onAccentContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(Sp.iconText))
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.content)
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(Sp.lines))
            Text(text, style = MaterialTheme.typography.bodySmall, color = p.variant, maxLines = 2)
        }
    }
}

// ── Clima ─────────────────────────────────────────────────────────────

/** Tarjeta de clima: lugar, estado y temperatura grande; debajo las próximas horas sobre un degradado. */
@Composable
private fun WeatherCard(data: Glance.Data, s: IslandSettings, p: IslandPalette) {
    val w = data.weather
    if (s.glanceLat == null || w == null) {
        return Padded(GlanceView.WEATHER, data, p) {
            if (s.glanceLat == null) Empty(Icons.Rounded.LocationOn, "No location", "Pick a city in At a glance settings.", p)
            else Empty(Icons.Rounded.Cloud, "Loading weather…", "", p)
        }
    }
    val ctx = LocalContext.current
    val tint = skyTint(w.code, w.isDay)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Transparent, 1f to tint))
            .padding(start = Sp.edge + 4.dp, end = Sp.edge + 4.dp, top = Sp.edge + 2.dp, bottom = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(w.code, w.isDay, 58.dp)
            Spacer(Modifier.width(Sp.group + 2.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    w.place.ifBlank { "Weather" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Sp.lines))
                Text(
                    "${Glance.condition(w.code)} · H ${w.high.roundToInt()}° L ${w.low.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium.merge(Num), color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Sp.iconText))
            Text(
                "${w.temp.roundToInt()}°", color = TempBlue,
                style = MaterialTheme.typography.displayMedium.merge(Num).copy(fontWeight = FontWeight.Normal),
            )
        }
        Spacer(Modifier.height(Sp.group + 6.dp))
        val pattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(ctx)) "Hm" else "h a") }
        val fmt = remember(pattern) { java.text.SimpleDateFormat(pattern, Locale.getDefault()) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            w.hours.drop(1).take(5).forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt.format(Date(h.time)), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(6.dp))
                    WeatherGlyph(h.code, h.isDay, 30.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("${h.temp.roundToInt()}°", style = MaterialTheme.typography.titleSmall.merge(Num), color = Color.White)
                    Text(
                        "${h.rainChance}%", style = MaterialTheme.typography.labelSmall.merge(Num),
                        color = if (h.rainChance >= 20) RainBlue else Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

/** Icono de clima a dos tonos, montado con varias piezas (sol o luna, nube, gotas, rayo, copo). */
@Composable
private fun WeatherGlyph(code: Int, day: Boolean, size: Dp) {
    val sky = if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    val skyTint = if (day) Sun else Moon
    Box(Modifier.size(size)) {
        when (code) {
            0 -> Icon(sky, null, tint = skyTint, modifier = Modifier.fillMaxSize())
            1, 2 -> {
                Icon(sky, null, tint = skyTint, modifier = Modifier.size(size * 0.62f).align(Alignment.TopEnd))
                Icon(Icons.Rounded.Cloud, null, tint = CloudLight, modifier = Modifier.size(size * 0.78f).align(Alignment.BottomStart))
            }
            3, 45, 48 -> {
                Icon(Icons.Rounded.Cloud, null, tint = CloudDark, modifier = Modifier.size(size * 0.62f).align(Alignment.TopEnd))
                Icon(Icons.Rounded.Cloud, null, tint = CloudLight, modifier = Modifier.size(size * 0.82f).align(Alignment.BottomStart))
            }
            in 95..99 -> {
                Icon(Icons.Rounded.Cloud, null, tint = CloudDark, modifier = Modifier.size(size * 0.86f).align(Alignment.TopCenter))
                Icon(Icons.Rounded.Bolt, null, tint = Color(0xFFFFB300), modifier = Modifier.size(size * 0.46f).align(Alignment.BottomCenter))
            }
            in 71..77, 85, 86 -> {
                Icon(Icons.Rounded.Cloud, null, tint = CloudLight, modifier = Modifier.size(size * 0.86f).align(Alignment.TopCenter))
                Icon(Icons.Rounded.AcUnit, null, tint = Color(0xFFBFE6FF), modifier = Modifier.size(size * 0.34f).align(Alignment.BottomCenter))
            }
            else -> { // llovizna, lluvia, chubascos
                if (code in 80..82) Icon(sky, null, tint = skyTint, modifier = Modifier.size(size * 0.5f).align(Alignment.TopEnd))
                Icon(
                    Icons.Rounded.Cloud, null, tint = CloudLight,
                    modifier = Modifier.size(size * 0.84f).align(if (code in 80..82) Alignment.TopStart else Alignment.TopCenter)
                        .offset(y = -(size * 0.04f)),
                )
                Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(-(size * 0.04f))) {
                    repeat(if (code in 51..57) 2 else 3) {
                        Icon(Icons.Rounded.WaterDrop, null, tint = RainBlue, modifier = Modifier.size(size * 0.24f))
                    }
                }
            }
        }
    }
}

/** Color del degradado de la tarjeta según el cielo. */
private fun skyTint(code: Int, day: Boolean): Color = when {
    code in 95..99 -> Color(0xFF3F3272)
    code in 51..67 || code in 80..82 -> Color(0xFF26467A)
    code in 71..77 || code in 85..86 -> Color(0xFF41607F)
    code == 3 || code == 45 || code == 48 -> Color(0xFF39475C)
    !day -> Color(0xFF2E3577)
    else -> Color(0xFF34589F)
}

// ── Deportes ──────────────────────────────────────────────────────────

@Composable
private fun SportsPage(games: List<Glance.Game>, p: IslandPalette) {
    if (games.isEmpty()) return Empty(Icons.Rounded.EmojiEvents, "No games right now", "Follow leagues and teams in At a glance settings.", p)
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        games.take(3).forEach { g ->
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (g.live) Live.copy(alpha = 0.14f) else p.accentContainer.copy(alpha = 0.4f))
                    .padding(horizontal = Sp.group),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamSide(g.away, g, p, Modifier.weight(1f), alignEnd = false)
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (g.state == "pre") {
                        val tf = DateFormat.getTimeFormat(ctx)
                        Text(tf.format(Date(g.start)), style = MaterialTheme.typography.titleSmall.merge(Num), color = p.content)
                        Text(
                            if (DateUtils.isToday(g.start)) g.league else DateUtils.formatDateTime(ctx, g.start, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY),
                            style = MaterialTheme.typography.labelSmall, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            "${g.away.score ?: "-"}  –  ${g.home.score ?: "-"}",
                            style = MaterialTheme.typography.titleMedium.merge(Num).copy(fontWeight = FontWeight.SemiBold), color = p.content,
                        )
                        if (g.live) Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Live))
                            Spacer(Modifier.width(4.dp))
                            Text(g.detail, style = MaterialTheme.typography.labelSmall, color = Live, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else Text(g.detail, style = MaterialTheme.typography.labelSmall, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TeamSide(g.home, g, p, Modifier.weight(1f), alignEnd = true)
            }
        }
    }
}

@Composable
private fun TeamSide(t: Glance.Team, g: Glance.Game, p: IslandPalette, modifier: Modifier, alignEnd: Boolean) {
    Row(modifier, horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        val logo = @Composable {
            val bmp = t.logo
            if (bmp != null) {
                val img = remember(bmp) { bmp.asImageBitmap() }
                Image(img, null, Modifier.size(26.dp))
            } else Icon(sportIcon(g.sport), null, tint = p.variant, modifier = Modifier.size(22.dp))
        }
        val name = @Composable {
            Text(
                t.abbr, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (t.favorite) FontWeight.Bold else FontWeight.Medium),
                color = if (t.favorite) p.accent else p.content, maxLines = 1,
            )
        }
        if (alignEnd) { name(); Spacer(Modifier.width(Sp.iconText)); logo() } else { logo(); Spacer(Modifier.width(Sp.iconText)); name() }
    }
}

private fun sportIcon(sport: String): ImageVector = when (sport) {
    "soccer" -> Icons.Rounded.SportsSoccer
    "basketball" -> Icons.Rounded.SportsBasketball
    "football" -> Icons.Rounded.SportsFootball
    "baseball" -> Icons.Rounded.SportsBaseball
    "hockey" -> Icons.Rounded.SportsHockey
    else -> Icons.Rounded.EmojiEvents
}

// ── Alertas ───────────────────────────────────────────────────────────

@Composable
private fun AlertsPage(alerts: List<Glance.Alert>, p: IslandPalette) {
    if (alerts.isEmpty()) return Empty(Icons.Rounded.CheckCircle, "All clear", "No events soon, alarms or weather alerts.", p)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        alerts.take(3).forEach { a ->
            val tint = alertTint(a.kind)
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(p.accentContainer.copy(alpha = 0.4f))
                    .padding(horizontal = Sp.iconText),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(alertIcon(a.kind), null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(Sp.iconText + 2.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.titleSmall, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.text, style = MaterialTheme.typography.labelSmall.merge(Num), color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun alertIcon(k: Glance.AlertKind): ImageVector = when (k) {
    Glance.AlertKind.EVENT -> Icons.Rounded.Event
    Glance.AlertKind.ALARM -> Icons.Rounded.Alarm
    Glance.AlertKind.RAIN -> Icons.Rounded.Umbrella
    Glance.AlertKind.SNOW -> Icons.Rounded.AcUnit
    Glance.AlertKind.UV -> Icons.Rounded.WbSunny
    Glance.AlertKind.COLD -> Icons.Rounded.Thermostat
    Glance.AlertKind.HEAT -> Icons.Rounded.LocalFireDepartment
    Glance.AlertKind.LIVE -> Icons.Rounded.SportsScore
}

private fun alertTint(k: Glance.AlertKind): Color = when (k) {
    Glance.AlertKind.EVENT -> Color(0xFFA8C7FA)
    Glance.AlertKind.ALARM -> Color(0xFFD0BCFF)
    Glance.AlertKind.RAIN -> Color(0xFF8AB4F8)
    Glance.AlertKind.SNOW, Glance.AlertKind.COLD -> Color(0xFFA8E1FF)
    Glance.AlertKind.UV -> Sun
    Glance.AlertKind.HEAT -> Color(0xFFFFB68A)
    Glance.AlertKind.LIVE -> Live
}

private fun viewIcon(v: GlanceView): ImageVector = when (v) {
    GlanceView.WEATHER -> Icons.Rounded.WbSunny
    GlanceView.CALENDAR -> Icons.Rounded.CalendarToday
    GlanceView.SPORTS -> Icons.Rounded.EmojiEvents
    GlanceView.ALERTS -> Icons.Rounded.NotificationsActive
}
