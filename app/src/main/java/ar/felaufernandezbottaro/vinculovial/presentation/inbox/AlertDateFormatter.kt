package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val SHORT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "AR"))
private val LONG_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "AR"))

/**
 * Convierte `created_at` (UTC) a la hora local del dispositivo y lo presenta en formato
 * amigable ("hace 2 horas", "ayer 14:30", "18 ago"). [now] y [zone] son parámetros, no lecturas
 * directas del reloj del sistema, para que el resultado sea determinístico en tests con
 * distintas zonas y en el cambio de día.
 */
fun Instant.toFriendlyAlertDate(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val local = atZone(zone).toLocalDateTime()
    val nowLocal = now.atZone(zone).toLocalDateTime()

    val daysBetween = ChronoUnit.DAYS.between(local.toLocalDate(), nowLocal.toLocalDate()).coerceAtLeast(0)
    val minutesBetween = ChronoUnit.MINUTES.between(local, nowLocal).coerceAtLeast(0)

    return when {
        daysBetween == 0L && minutesBetween < 1 -> "Hace instantes"
        daysBetween == 0L && minutesBetween < 60 -> "Hace $minutesBetween min"
        daysBetween == 0L -> {
            val hours = minutesBetween / 60
            if (hours == 1L) "Hace 1 hora" else "Hace $hours horas"
        }
        daysBetween == 1L -> "Ayer ${local.format(TIME_FORMATTER)}"
        // CLDR abrevia "agosto" como "ago." (con punto); el formato acordado es sin punto.
        else -> local.format(SHORT_DATE_FORMATTER).replace(".", "")
    }
}

/**
 * La hora de una fila del buzón, como en un chat: la hora si es de hoy ("10:42"), "Ayer" si es
 * de ayer y la fecha corta ("3 oct") si es anterior. Como [toFriendlyAlertDate], [now] y [zone]
 * son parámetros para que el resultado sea determinístico en tests.
 */
fun Instant.toInboxListTime(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val local = atZone(zone).toLocalDateTime()
    val daysBetween = ChronoUnit.DAYS.between(local.toLocalDate(), now.atZone(zone).toLocalDate())

    return when {
        daysBetween <= 0L -> local.format(TIME_FORMATTER)
        daysBetween == 1L -> "Ayer"
        else -> local.format(SHORT_DATE_FORMATTER).replace(".", "")
    }
}

/** El sello de día que abre una conversación: "Hoy 10:42", "Ayer 10:42" o "3 oct 10:42". */
fun Instant.toChatDayStamp(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val local = atZone(zone).toLocalDateTime()
    val daysBetween = ChronoUnit.DAYS.between(local.toLocalDate(), now.atZone(zone).toLocalDate())
    val time = local.format(TIME_FORMATTER)

    return when {
        daysBetween <= 0L -> "Hoy $time"
        daysBetween == 1L -> "Ayer $time"
        else -> "${local.format(SHORT_DATE_FORMATTER).replace(".", "")} $time"
    }
}

/** "9 de noviembre": hasta cuándo se conserva la evidencia redactada. */
fun Instant.toLongDate(zone: ZoneId = ZoneId.systemDefault()): String = atZone(zone).format(LONG_DATE_FORMATTER)
