package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult
import ar.felaufernandezbottaro.vinculovial.presentation.vivi.ViviMessages

fun StartAlertAttemptResult.Failure.message(): String = when (this) {
    StartAlertAttemptResult.UnderReview ->
        "Tu cuenta está en revisión. Por ahora no podés enviar alertas."

    StartAlertAttemptResult.InvalidPlate ->
        "La patente no es válida. Revisala e intentá de nuevo."

    StartAlertAttemptResult.RateLimited ->
        "Alcanzaste el límite de consultas. Esperá unos minutos antes de volver a intentar."

    StartAlertAttemptResult.Unavailable ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo en unos minutos."

    StartAlertAttemptResult.Unexpected ->
        "No pudimos verificar la patente. Intentá de nuevo en unos minutos."
}

fun SendAlertError.message(): String = when (this) {
    SendAlertError.InvalidEvidence ->
        "Las fotos no son las que pide este tipo de alerta. Volvé a sacarlas."

    SendAlertError.AlertTypeDisabled ->
        "Este tipo de alerta no está disponible por ahora."

    SendAlertError.InvalidAlertType ->
        "Este tipo de alerta no es válido."

    SendAlertError.InvalidData ->
        "No pudimos procesar el reporte. Volvé a intentarlo."

    SendAlertError.EvidenceTooLarge ->
        "Las fotos pesan demasiado para enviarse. Volvé a sacarlas."

    SendAlertError.UnderReview ->
        "Tu cuenta está en revisión. Por ahora no podés enviar alertas."

    SendAlertError.AttemptNotFound ->
        "Se perdió la verificación de la patente. Volvé a empezar."

    SendAlertError.PossiblySent -> ViviMessages.SENDER_POSSIBLY_SENT

    SendAlertError.AttemptExpired ->
        "La verificación de la patente venció. Intentá de nuevo."

    SendAlertError.RateLimited ->
        "Alcanzaste el límite de reportes. Esperá unos minutos antes de volver a intentar."

    SendAlertError.Unavailable ->
        "El servidor no está disponible en este momento. Intentá de nuevo en unos minutos."

    SendAlertError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."

    SendAlertError.Unexpected ->
        "No pudimos enviar el reporte. Intentá de nuevo en unos minutos."
}

/** Qué se le ofrece al usuario tras un error del envío. */
enum class SendAlertRecovery {
    /** Reintento manual con las mismas fotos: errores transitorios (429, 502/503/504, red). */
    RETRY,

    /** Empezar de nuevo desde la patente: el intento se perdió. */
    RESTART,

    /** Nada que reintentar con lo que hay: sólo cerrar y descartar. */
    CLOSE,
}

fun SendAlertError.recovery(): SendAlertRecovery = when (this) {
    SendAlertError.RateLimited,
    SendAlertError.Unavailable,
    SendAlertError.Network,
    SendAlertError.AttemptExpired,
    SendAlertError.Unexpected,
    -> SendAlertRecovery.RETRY

    SendAlertError.AttemptNotFound -> SendAlertRecovery.RESTART


    SendAlertError.PossiblySent,
    SendAlertError.InvalidEvidence,
    SendAlertError.AlertTypeDisabled,
    SendAlertError.InvalidAlertType,
    SendAlertError.InvalidData,
    SendAlertError.EvidenceTooLarge,
    SendAlertError.UnderReview,
    -> SendAlertRecovery.CLOSE
}
