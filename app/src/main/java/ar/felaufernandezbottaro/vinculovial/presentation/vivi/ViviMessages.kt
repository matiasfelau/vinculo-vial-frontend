package ar.felaufernandezbottaro.vinculovial.presentation.vivi

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertState
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

    /** Lo que ViVIAna cierra en todas las alertas: no distingue entre más o menos validadas. */
    const val OWNER_REVIEW_PROMPT = "Revisá las fotos antes de actuar."

    /** Pill de cada fila de Recibidos: invita a abrir el mensaje. */
    const val INBOX_VIEW_PHOTOS = "Ver fotos"

    const val EVIDENCE_REDACTED_NOTE = "Ocultamos rostros y otras patentes."
    const val EVIDENCE_UNAVAILABLE = "La evidencia ya no está disponible"
    const val EVIDENCE_EMPTY = "Esta alerta no tiene evidencia para mostrar."

    const val FEEDBACK_PROMPT = "¿Te sirvió?"
    const val REPORT_SENDER = "Reportar emisor"
    const val REPORTED = "Reportado"

    // -- Buzón: pestaña Enviados --------------------------------------------------------------

    const val SENT_EMPTY_TITLE = "Todavía no enviaste alertas"
    const val SENT_EMPTY_BODY = "Cuando avises sobre un vehículo, vas a ver acá cómo va tu alerta."

    const val SENT_STATE_IN_VALIDATION = "En validación"
    const val SENT_STATE_VALIDATED = "Validada"
    const val SENT_STATE_SEEN = "Leída"
    const val SENT_STATE_REJECTED = "Rechazada"
    const val SENT_STATE_NO_RESPONSE = "Sin respuesta"

    // -- Chat del emisor ---------------------------------------------------------------------

    const val SENDER_GREETING = "Hola, soy ViVIAna. Ya tengo la patente."
    const val SENDER_PROMPT = "¿Qué querés avisar? Elegí uno de estos mensajes."
    const val SENDER_OPTIONS_HEADER = "Mensajes disponibles"
    const val SENDER_FIXED_NOTE = "Los mensajes son fijos para que el dueño no pueda identificarte."
    const val SENDER_SENT_RECEIPT =
        "Recibí todo. El sistema va a validar la información y, si la aprueba, la dejo en el buzón de esa patente."

    /**
     * Lo que ViVIAna agrega en el chat del emisor con la alerta `ACTIVE`. Es igual para todas: el
     * emisor nunca se entera de si la patente tiene dueños, y ViVIAna sólo dice lo que es cierto.
     */
    const val SENDER_VALIDATED = "Validé tu alerta y la dejé en el buzón de esa patente."
    const val SENDER_SEEN = "El dueño leyó tu mensaje."
    const val SENDER_REJECTED = "No pudimos validar tu alerta."
    const val SENDER_NO_RESPONSE = "Tu alerta no tuvo respuesta."

    const val SENDER_POSSIBLY_SENT = "No pudimos confirmar el envío. Puede que tu alerta se haya enviado."

    /** La intro de la captura guiada: qué fotos se piden según el tipo. */
    fun senderPhotosIntro(alertType: AlertType): String = when (alertType) {
        AlertType.CRASHED ->
            "Para avisarle necesito fotos. Te guío con la cámara: el auto con la patente visible, " +
                "la patente de cerca y una foto del daño."
        else ->
            "Para avisarle necesito fotos. Te guío con la cámara: el auto con la patente visible, " +
                "la patente de cerca y el auto junto a la rampa, desde la vereda."
    }

    const val SENDER_TAKE_PHOTOS = "Tomar fotos"

    /** "3 fotos cifradas adjuntas": el único texto del adjunto del emisor, sin detalle por foto. */
    fun senderAttachmentSummary(photoCount: Int): String =
        if (photoCount == 1) "1 foto cifrada adjunta" else "$photoCount fotos cifradas adjuntas"
}

/** La etiqueta de la pill de una alerta enviada. */
fun SentAlertState.label(): String = when (this) {
    SentAlertState.IN_VALIDATION -> ViviMessages.SENT_STATE_IN_VALIDATION
    SentAlertState.VALIDATED -> ViviMessages.SENT_STATE_VALIDATED
    SentAlertState.SEEN -> ViviMessages.SENT_STATE_SEEN
    SentAlertState.REJECTED -> ViviMessages.SENT_STATE_REJECTED
    SentAlertState.NO_RESPONSE -> ViviMessages.SENT_STATE_NO_RESPONSE
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
 * hizo el usuario (reportó, valoró); si no hizo nada y no hay evidencia, eso; si no, el mismo
 * pedido de revisar las fotos que cierra el chat.
 */
fun AlertItem.inboxPreview(): String = when {
    reported -> "Reportaste al emisor."
    myFeedback == FeedbackRating.UP -> "Marcaste que el aviso te sirvió."
    myFeedback == FeedbackRating.DOWN -> "Marcaste que el aviso no te sirvió."
    evidence.isEmpty() -> "${ViviMessages.EVIDENCE_UNAVAILABLE}."
    else -> "${ViviMessages.NAME}: ${ViviMessages.OWNER_REVIEW_PROMPT}"
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
