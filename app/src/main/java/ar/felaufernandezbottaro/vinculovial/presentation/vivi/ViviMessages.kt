package ar.felaufernandezbottaro.vinculovial.presentation.vivi

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertConfidence
import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType
import ar.felaufernandezbottaro.vinculovial.presentation.inbox.toLongDate
import java.time.Instant
import java.time.ZoneId

/**
 * ViVIAna es sólo presentación: no es un bot ni un servicio. Cada cosa que "dice" es una
 * plantilla de esta app según el estado de la alerta, y no viaja ni llega del servidor.
 */
object ViviMessages {

    const val NAME = "ViVIAna"
    const val TAGLINE = "Asistente de Vínculo Vial"

    /** Pie de las pantallas del buzón. */
    const val INBOX_FOOTNOTE = "Hablás siempre con ViVIAna. Nunca se comparte quién envió el aviso."

    // -- Chat del dueño ----------------------------------------------------------------------

    const val OWNER_GREETING = "Hola, soy ViVIAna. Tenés un nuevo mensaje sobre uno de tus vehículos."

    const val CONFIDENCE_HIGH_LABEL = "Confianza alta"
    const val CONFIDENCE_MEDIUM_LABEL = "Confianza media"

    const val CONFIDENCE_HIGH_MESSAGE = "Validé las fotos y coinciden con lo que dice el mensaje."
    const val CONFIDENCE_MEDIUM_MESSAGE = "Validé las fotos, pero no estoy del todo segura de que muestren lo que " +
        "dice el mensaje. Revisalas con cuidado antes de actuar."

    const val REVIEW_BADGE = "No verificada por completo"

    const val EVIDENCE_REDACTED_NOTE = "Ocultamos rostros y otras patentes."
    const val EVIDENCE_UNAVAILABLE = "La evidencia ya no está disponible"
    const val EVIDENCE_EMPTY = "Esta alerta no tiene evidencia para mostrar."

    const val FEEDBACK_PROMPT = "¿Te sirvió?"
    const val REPORT_SENDER = "Reportar emisor"
    const val REPORTED = "Reportado"

    // -- Buzón: pestaña Enviados --------------------------------------------------------------

    const val SENT_UNDER_CONSTRUCTION_TITLE = "En construcción"
    const val SENT_UNDER_CONSTRUCTION_BODY =
        "Pronto vas a ver acá tus alertas enviadas, si fueron validadas y cuándo las leyeron."

    // -- Chat del emisor ---------------------------------------------------------------------

    const val SENDER_GREETING = "Hola, soy ViVIAna. Ya identifiqué el vehículo."
    const val SENDER_PROMPT = "¿Qué le querés avisar a su dueño? Elegí uno de estos mensajes."
    const val SENDER_OPTIONS_HEADER = "Mensajes disponibles"
    const val SENDER_FIXED_NOTE = "Los mensajes son fijos para que el dueño no pueda identificarte."
    const val SENDER_SENT_RECEIPT =
        "Recibí todo. El sistema va a validar la información y, si la aprueba, le aviso al dueño."
    const val SENDER_POSSIBLY_SENT = "No pudimos confirmar el envío. Puede que tu alerta se haya enviado."

    /** La intro de la captura guiada: qué fotos se piden según el tipo. */
    fun senderPhotosIntro(alertType: AlertType): String = when (alertType) {
        AlertType.CRASHED ->
            "Para avisarle necesito fotos. Te guío con la cámara: el auto con la patente visible, " +
                "la patente de cerca y el daño."
        else ->
            "Para avisarle necesito fotos. Te guío con la cámara: el auto con la patente visible, " +
                "la patente de cerca y el auto junto a la rampa, desde la vereda."
    }

    const val SENDER_TAKE_PHOTOS = "Tomar fotos"

    /** "3 fotos cifradas adjuntas": el único texto del adjunto del emisor, sin detalle por foto. */
    fun senderAttachmentSummary(photoCount: Int): String =
        if (photoCount == 1) "1 foto cifrada adjunta" else "$photoCount fotos cifradas adjuntas"
}

/** La etiqueta de la confianza: "Confianza alta" / "Confianza media". */
fun AlertConfidence.label(): String = when (this) {
    AlertConfidence.HIGH -> ViviMessages.CONFIDENCE_HIGH_LABEL
    AlertConfidence.MEDIUM -> ViviMessages.CONFIDENCE_MEDIUM_LABEL
}

/** Lo que ViVIAna dice sobre la validación en el chat del dueño. */
fun AlertConfidence.message(): String = when (this) {
    AlertConfidence.HIGH -> ViviMessages.CONFIDENCE_HIGH_MESSAGE
    AlertConfidence.MEDIUM -> ViviMessages.CONFIDENCE_MEDIUM_MESSAGE
}

/**
 * El mensaje fijo que elige el emisor, tal cual lo ve el dueño en el chat. Son frases cerradas
 * a propósito: sin texto libre, el dueño no puede reconocer a quien avisó.
 */
fun AlertType.senderMessage(): String = when (this) {
    AlertType.PARKED_ON_GARAGE -> "Tu auto está bloqueando una rampa (garage o accesibilidad)."
    AlertType.CRASHED -> "Tu auto fue chocado."
    AlertType.PARKED_TOO_CLOSE -> "Tu auto está estacionado muy cerca de otro."
}

private val SHORT_TITLES = mapOf(
    AlertType.PARKED_ON_GARAGE to "Tu auto está bloqueando una rampa",
    AlertType.CRASHED to "Tu auto fue chocado",
    AlertType.PARKED_TOO_CLOSE to "Tu auto está estacionado muy cerca de otro",
)

private const val UNKNOWN_ALERT_MESSAGE = "Tenés un aviso sobre tu auto."
private const val UNKNOWN_ALERT_TITLE = "Tenés un aviso sobre tu auto"

private fun alertTypeOf(rawAlertType: String): AlertType? = AlertType.entries.firstOrNull { it.wireValue == rawAlertType }

/**
 * El `alert_type` crudo del backend como mensaje del chat. Un tipo que la app todavía no conoce
 * cae en un mensaje genérico, nunca rompe la pantalla: el catálogo puede crecer del lado del
 * servidor antes de que la app se actualice.
 */
fun alertMessage(rawAlertType: String): String = alertTypeOf(rawAlertType)?.senderMessage() ?: UNKNOWN_ALERT_MESSAGE

/** El título corto de la fila del buzón (sin el aclaratorio entre paréntesis). */
fun alertTitle(rawAlertType: String): String = alertTypeOf(rawAlertType)?.let(SHORT_TITLES::get) ?: UNKNOWN_ALERT_TITLE

/**
 * La línea del último mensaje de ViVIAna que se ve en la fila del buzón. Gana lo último que
 * hizo el usuario (reportó, valoró); si no hizo nada y no hay evidencia, eso; si no, lo que
 * ViVIAna concluyó sobre la validación.
 */
fun AlertItem.inboxPreview(): String = when {
    reported -> "Reportaste al emisor."
    myFeedback == FeedbackRating.UP -> "Marcaste que el aviso te sirvió."
    myFeedback == FeedbackRating.DOWN -> "Marcaste que el aviso no te sirvió."
    evidence.isEmpty() -> "${ViviMessages.EVIDENCE_UNAVAILABLE}."
    confidence == AlertConfidence.MEDIUM -> "${ViviMessages.NAME}: Revisá la evidencia con cuidado antes de actuar."
    else -> "${ViviMessages.NAME}: Validé las fotos y coinciden con el aviso."
}

/**
 * La nota al pie de la tarjeta con la evidencia: qué se ocultó y hasta cuándo se puede ver.
 * Sin fecha de vencimiento, sólo lo primero.
 */
fun evidenceNote(expiresAt: Instant?, zone: ZoneId = ZoneId.systemDefault()): String =
    if (expiresAt == null) {
        ViviMessages.EVIDENCE_REDACTED_NOTE
    } else {
        "${ViviMessages.EVIDENCE_REDACTED_NOTE} Disponible hasta el ${expiresAt.toLongDate(zone)}."
    }

/**
 * La etiqueta de una foto de evidencia: "General", "Patente", "Contexto" y "Daño" (con número si
 * hay varias, "Daño 1"). Un rol que la app no conoce todavía se llama "Foto".
 */
fun evidenceTileLabel(role: String, index: Int, countInRole: Int): String {
    val base = when (role) {
        "GENERAL" -> "General"
        "PLATE" -> "Patente"
        "DETAIL" -> "Daño"
        "CONTEXT" -> "Contexto"
        else -> "Foto"
    }
    return if (countInRole > 1) "$base ${index + 1}" else base
}
