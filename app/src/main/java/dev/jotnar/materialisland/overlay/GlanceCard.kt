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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

private val Up = Color(0xFF6DD58C)
private val Down = Color(0xFFFF8A80)
private val Live = Color(0xFFFF5449)
private val Sun = Color(0xFFFFC95C)
private val PageH = 156.dp
private val Num = TextStyle(fontFeatureSettings = "tnum")

// ── Icono junto a la cámara ───────────────────────────────────────────

/** Círculo a la izquierda de la cámara: el clima de ahora, o un destello si no hay datos. */
@Composable
internal fun GlanceChip(data: Glance.Data, size: Dp, s: IslandSettings, cb: Callbacks, modifier: Modifier) {
    val w = data.weather?.takeIf { GlanceView.WEATHER in s.glanceViews }
    val live = data.liveGame != null && GlanceView.SPORTS in s.glanceViews
    Box(
        modifier.size(size).pointerInput(Unit) { detectTapGestures { cb.onGlanceTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
            when {
                w != null -> Icon(weatherIcon(w.code, w.isDay), null, tint = weatherTint(w.code, w.isDay), modifier = Modifier.size(size * 0.58f))
                live -> Icon(sportIcon(data.liveGame!!.sport), null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
                else -> Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.5f))
            }
        }
        // Punto rojo latiendo si hay un partido en vivo.
        if (live) {
            val t = rememberInfiniteTransition(label = "live")
            val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "liveDot")
            Box(
                Modifier.align(Alignment.TopEnd).size(size * 0.3f).clip(CircleShape).background(Color.Black)
                    .padding(size * 0.06f).clip(CircleShape).background(Live.copy(alpha = a)),
            )
        }
    }
}

private typealias Callbacks = IslandCallbacks

// ── Tarjeta ───────────────────────────────────────────────────────────

/** Tarjeta deslizable con las vistas que el usuario eligió, en su orden. */
@Composable
internal fun GlanceCard(s: IslandSettings, p: IslandPalette, d: Dims, data: Glance.Data, cb: IslandCallbacks) {
    val views = GlanceView.entries.filter { it in s.glanceViews }
    Column(Modifier.width(d.cardW).padding(top = Sp.edge, bottom = Sp.group)) {
        if (views.isEmpty()) {
            Text(
                "Choose views in At a glance settings.", style = MaterialTheme.typography.bodyMedium, color = p.variant,
                modifier = Modifier.padding(horizontal = Sp.edge),
            )
            return@Column
        }
        val pager = rememberPagerState { views.size }
        LaunchedEffect(pager.currentPage) { if (pager.currentPage != 0 || pager.settledPage != 0) cb.onGlancePage() }
        HorizontalPager(pager, Modifier.fillMaxWidth(), key = { views[it].name }) { i ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Sp.edge)) {
                Header(views[i], data, p)
                Spacer(Modifier.height(Sp.group))
                Box(Modifier.fillMaxWidth().height(PageH)) {
                    if (!data.online && !data.hasAny) Offline(p)
                    else when (views[i]) {
                        GlanceView.WEATHER -> WeatherPage(data.weather, s, p)
                        GlanceView.SPORTS -> SportsPage(data.games, p)
                        GlanceView.FINANCE -> FinancePage(data.quotes, p)
                        GlanceView.ALERTS -> AlertsPage(data.alerts, p)
                    }
                }
            }
        }
        if (views.size > 1) {
            Spacer(Modifier.height(Sp.iconText))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                views.indices.forEach { i ->
                    val on = i == pager.currentPage
                    val c by animateColorAsState(if (on) p.content else p.content.copy(alpha = 0.3f), label = "dot")
                    val w by androidx.compose.animation.core.animateDpAsState(if (on) 16.dp else 6.dp, label = "dotW")
                    Box(Modifier.padding(horizontal = 3.dp).width(w).height(6.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}

@Composable
private fun Header(v: GlanceView, data: Glance.Data, p: IslandPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(viewIcon(v), null, tint = p.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Caption(v.label, p.variant)
        Spacer(Modifier.weight(1f))
        val note = when {
            !data.online -> "Offline"
            data.updatedAt > 0 && System.currentTimeMillis() - data.updatedAt < 60_000 -> "Updated just now"
            data.updatedAt > 0 -> "Updated " + DateUtils.getRelativeTimeSpanString(
                data.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString().lowercase(Locale.getDefault())
            else -> "Loading…"
        }
        Text(note, style = MaterialTheme.typography.labelSmall, color = p.variant.copy(alpha = 0.7f))
    }
}

@Composable
private fun Offline(p: IslandPalette) = Empty(Icons.Rounded.CloudOff, "No internet", "At a glance comes back when you're online.", p)

@Composable
private fun Empty(icon: ImageVector, title: String, text: String, p: IslandPalette) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(Sp.action).clip(CircleShape).background(p.accentContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.onAccentContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(Sp.iconText))
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.content)
        Spacer(Modifier.height(Sp.lines))
        Text(text, style = MaterialTheme.typography.bodySmall, color = p.variant, maxLines = 2)
    }
}

// ── Clima ─────────────────────────────────────────────────────────────

@Composable
private fun WeatherPage(w: Glance.Weather?, s: IslandSettings, p: IslandPalette) {
    if (s.glanceLat == null) return Empty(Icons.Rounded.LocationOn, "No location", "Pick a city in At a glance settings.", p)
    if (w == null) return Empty(Icons.Rounded.Cloud, "Loading weather…", "", p)
    val ctx = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(weatherIcon(w.code, w.isDay), null, tint = weatherTint(w.code, w.isDay), modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(Sp.group))
            Text("${w.temp.roundToInt()}°", style = MaterialTheme.typography.displayMedium.merge(Num), color = p.content)
            Spacer(Modifier.width(Sp.group))
            Column(Modifier.weight(1f)) {
                Text(Glance.condition(w.code), style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Sp.lines))
                Text(
                    "H ${w.high.roundToInt()}°  ·  L ${w.low.roundToInt()}°", style = MaterialTheme.typography.bodyMedium.merge(Num),
                    color = p.variant,
                )
                if (w.place.isNotBlank()) Text(
                    w.place, style = MaterialTheme.typography.bodySmall, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(Sp.group))
        val pattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(ctx)) "HH" else "ha") }
        val fmt = remember(pattern) { java.text.SimpleDateFormat(pattern, Locale.getDefault()) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.accentContainer.copy(alpha = 0.45f))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            w.hours.drop(1).take(6).forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt.format(Date(h.time)), style = MaterialTheme.typography.labelSmall, color = p.variant)
                    Spacer(Modifier.height(4.dp))
                    Icon(weatherIcon(h.code, h.isDay), null, tint = weatherTint(h.code, h.isDay), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("${h.temp.roundToInt()}°", style = MaterialTheme.typography.labelLarge.merge(Num), color = p.content)
                }
            }
        }
    }
}

private fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> if (day) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    3, 45, 48 -> Icons.Rounded.Cloud
    in 51..57 -> Icons.Rounded.Grain
    in 61..67, in 80..82 -> Icons.Rounded.Umbrella
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

private fun weatherTint(code: Int, day: Boolean): Color = when {
    code <= 2 && day -> Sun
    code <= 2 -> Color(0xFFC5CAF5)
    code in 61..67 || code in 80..82 || code in 51..57 -> Color(0xFF8AB4F8)
    code in 95..99 -> Color(0xFFFFD54F)
    else -> Color(0xFFDCE3EA)
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

// ── Bolsa ─────────────────────────────────────────────────────────────

@Composable
private fun FinancePage(quotes: List<Glance.Quote>, p: IslandPalette) {
    if (quotes.isEmpty()) return Empty(Icons.Rounded.ShowChart, "No quotes", "Add symbols in At a glance settings.", p)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        quotes.take(4).forEach { q ->
            val up = q.changePct >= 0
            val c = if (up) Up else Down
            Row(Modifier.fillMaxWidth().height(37.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(q.symbol.removePrefix("^"), style = MaterialTheme.typography.titleSmall, color = p.content, maxLines = 1)
                    Text(q.name, style = MaterialTheme.typography.labelSmall, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Sparkline(q.closes, c, Modifier.width(64.dp).height(24.dp))
                Spacer(Modifier.width(Sp.group))
                Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                    Text(price(q.price), style = MaterialTheme.typography.titleSmall.merge(Num), color = p.content, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, null, tint = c, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            (if (up) "+" else "−") + String.format(Locale.getDefault(), "%.2f%%", abs(q.changePct)),
                            style = MaterialTheme.typography.labelSmall.merge(Num), color = c,
                        )
                    }
                }
            }
        }
    }
}

private fun price(v: Double) = if (v >= 1000) String.format(Locale.getDefault(), "%,.0f", v) else String.format(Locale.getDefault(), "%,.2f", v)

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    if (values.size < 2) return Spacer(modifier)
    Canvas(modifier) {
        val min = values.min(); val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - (v - min) / span * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(path, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = Offset(size.width, size.height - (values.last() - min) / span * size.height)
        drawCircle(color, 2.2.dp.toPx(), last)
    }
}

// ── Alertas ───────────────────────────────────────────────────────────

@Composable
private fun AlertsPage(alerts: List<Glance.Alert>, p: IslandPalette) {
    if (alerts.isEmpty()) return Empty(Icons.Rounded.CheckCircle, "All clear", "No alarms, weather alerts or big market moves.", p)
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
    Glance.AlertKind.ALARM -> Icons.Rounded.Alarm
    Glance.AlertKind.RAIN -> Icons.Rounded.Umbrella
    Glance.AlertKind.SNOW -> Icons.Rounded.AcUnit
    Glance.AlertKind.UV -> Icons.Rounded.WbSunny
    Glance.AlertKind.COLD -> Icons.Rounded.Thermostat
    Glance.AlertKind.HEAT -> Icons.Rounded.LocalFireDepartment
    Glance.AlertKind.LIVE -> Icons.Rounded.SportsScore
    Glance.AlertKind.MARKET -> Icons.Rounded.ShowChart
}

private fun alertTint(k: Glance.AlertKind): Color = when (k) {
    Glance.AlertKind.ALARM -> Color(0xFFD0BCFF)
    Glance.AlertKind.RAIN -> Color(0xFF8AB4F8)
    Glance.AlertKind.SNOW, Glance.AlertKind.COLD -> Color(0xFFA8E1FF)
    Glance.AlertKind.UV -> Sun
    Glance.AlertKind.HEAT -> Color(0xFFFFB68A)
    Glance.AlertKind.LIVE -> Live
    Glance.AlertKind.MARKET -> Up
}

private fun viewIcon(v: GlanceView): ImageVector = when (v) {
    GlanceView.WEATHER -> Icons.Rounded.WbSunny
    GlanceView.SPORTS -> Icons.Rounded.EmojiEvents
    GlanceView.FINANCE -> Icons.Rounded.ShowChart
    GlanceView.ALERTS -> Icons.Rounded.NotificationsActive
}
