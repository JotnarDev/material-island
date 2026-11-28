package dev.jotnar.materialisland.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateUtils
import java.util.Calendar
import java.util.TimeZone

/**
 * Próximos eventos del calendario del teléfono, que es donde Google Calendar sincroniza tus cuentas de Google.
 * Se leen en el propio teléfono con CalendarContract: sin internet, sin API ni claves, y nada sale del teléfono.
 */
object CalendarReader {
    data class Event(
        val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean,
        val color: Int, val location: String?, val calendar: String, val meetUrl: String?,
    ) {
        fun ongoing(now: Long) = !allDay && now in begin until end
    }

    private val MEET = Regex("https://(meet\\.google\\.com/[a-z0-9-]+|[\\w.-]*zoom\\.us/j/[\\w?=&.-]+|teams\\.microsoft\\.com/l/meetup-join/\\S+)", RegexOption.IGNORE_CASE)

    fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Eventos que aún no terminan, de hoy y mañana: primero los que tienen hora, ordenados. */
    fun upcoming(ctx: Context, allDayToo: Boolean, max: Int = 4): List<Event> {
        if (!allowed(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val endOfTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - DateUtils.DAY_IN_MILLIS)
            ContentUris.appendId(it, endOfTomorrow)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.DESCRIPTION,
        )
        // Calendarios visibles y sin los eventos que rechazaste.
        val sel = "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        val out = ArrayList<Event>()
        runCatching {
            ctx.contentResolver.query(uri, cols, sel, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    var begin = c.getLong(2); var end = c.getLong(3)
                    // Los de todo el día vienen en UTC: se pasan a la medianoche local.
                    if (allDay) { begin = utcToLocalDay(begin); end = utcToLocalDay(end) }
                    if (end <= now || begin > endOfTomorrow) continue
                    if (allDay && !allDayToo) continue
                    val loc = c.getString(6)?.trim()?.takeIf { it.isNotEmpty() }
                    val desc = c.getString(8).orEmpty()
                    out += Event(
                        id = c.getLong(0), title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        begin = begin, end = end, allDay = allDay, color = c.getInt(5),
                        location = loc?.takeUnless { MEET.matches(it) }, calendar = c.getString(7).orEmpty(),
                        meetUrl = MEET.find(loc.orEmpty())?.value ?: MEET.find(desc)?.value,
                    )
                }
            }
        }
        return out.sortedWith(compareBy<Event> { it.allDay }.thenBy { it.begin }).take(max)
    }

    private fun utcToLocalDay(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Abre el evento en Google Calendar (o la app de calendario que haya). */
    fun openIntent(e: Event): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)

    /** Entra a la videollamada (Google Meet, Zoom o Teams). */
    fun joinIntent(e: Event): Intent? = e.meetUrl?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
}
