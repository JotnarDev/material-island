package dev.jotnar.materialisland.overlay

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import dev.jotnar.materialisland.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jotnar.materialisland.core.CalendarReader
import dev.jotnar.materialisland.core.CalendarReader.Event
import java.util.Date
import java.util.Locale

private fun timeOf(ctx: android.content.Context, t: Long): String = java.text.SimpleDateFormat(
    DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(ctx)) "Hm" else "hm"), Locale.getDefault(),
).format(Date(t))

private fun dayOf(t: Long): String = when {
    DateUtils.isToday(t) -> "Today"
    DateUtils.isToday(t - DateUtils.DAY_IN_MILLIS) -> "Tomorrow"
    else -> java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(Date(t))
}

/** Lo que sale en el icono junto a la cámara: "Now", "25m", "10:30" o el día. */
internal fun calendarChipText(ctx: android.content.Context, e: Event?): String? {
    e ?: return null
    val now = System.currentTimeMillis()
    return when {
        e.allDay -> if (DateUtils.isToday(e.begin) || e.begin < now) null else dayOf(e.begin)
        e.ongoing(now) -> "Now"
        e.begin - now < 60 * 60_000L -> "${((e.begin - now) / 60_000).coerceAtLeast(1)}m"
        // En el icono, la hora corta sin "a. m./p. m." para que quepa.
        DateUtils.isToday(e.begin) -> java.text.SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", Locale.getDefault()).format(Date(e.begin))
        else -> dayOf(e.begin)
    }
}

private fun countdown(e: Event, now: Long): String = when {
    e.allDay -> "All day"
    e.ongoing(now) -> "${((e.end - now) / 60_000).coerceAtLeast(1)} min left"
    e.begin - now < 60 * 60_000L -> "in ${((e.begin - now) / 60_000).coerceAtLeast(1)} min"
    DateUtils.isToday(e.begin) || e.begin - now < 6 * 60 * 60_000L -> "in ${((e.begin - now) + 1_800_000) / 3_600_000} h"
    else -> dayOf(e.begin)
}

/** Página de calendario: el próximo evento grande, con cuenta atrás y botón para unirse, y los siguientes. */
@Composable
internal fun CalendarPage(events: List<Event>, allowed: Boolean, p: IslandPalette, open: (android.content.Intent) -> Unit, empty: @Composable (String, String) -> Unit) {
    if (!allowed) return empty("Calendar access needed", "Allow it in Material Island › At a glance › Calendar.")
    if (events.isEmpty()) return empty("Nothing coming up", "No events left today or tomorrow.")
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val next = events.first()
    val color = Color(next.color)

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(18.dp))
            .background(p.accentContainer.copy(alpha = 0.4f)).clickable { open(CalendarReader.openIntent(next)) }
            .padding(12.dp),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(next.title, style = MaterialTheme.typography.titleMedium, color = p.content, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    countdown(next, now), color = if (next.ongoing(now) || next.begin - now < 15 * 60_000L) color else p.accent, maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.merge(Tabular).copy(fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (next.allDay) "${dayOf(next.begin)} · All day" else "${dayOf(next.begin)} · ${timeOf(ctx, next.begin)} – ${timeOf(ctx, next.end)}",
                style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = p.variant, maxLines = 1,
            )
            next.location?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = p.variant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = p.variant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (next.ongoing(now)) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ((now - next.begin).toFloat() / (next.end - next.begin).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp), color = color, trackColor = color.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                )
            }
            CalendarReader.joinIntent(next)?.let { join ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(CircleShape).background(color).clickable { open(join) }.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Videocam, null, tint = Color.Black.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if ("meet.google" in next.meetUrl.orEmpty()) "Join Meet" else "Join call",
                        style = MaterialTheme.typography.labelLarge, color = Color.Black.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
    val rest = events.drop(1).take(2)
    if (rest.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rest.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open(CalendarReader.openIntent(e)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(e.color)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (e.allDay) "All day" else timeOf(ctx, e.begin), style = MaterialTheme.typography.labelLarge.merge(Tabular),
                        color = p.variant, modifier = Modifier.widthIn(min = 52.dp), maxLines = 1,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(e.title, style = MaterialTheme.typography.bodyMedium, color = p.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (!DateUtils.isToday(e.begin) && !e.allDay) Text(dayOf(e.begin), style = MaterialTheme.typography.labelSmall, color = p.variant)
                }
            }
        }
    }
}
