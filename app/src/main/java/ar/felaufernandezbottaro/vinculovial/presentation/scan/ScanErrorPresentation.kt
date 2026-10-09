package ar.felaufernandezbottaro.vinculovial.presentation.scan

import ar.felaufernandezbottaro.vinculovial.domain.scan.SendAlertError
import ar.felaufernandezbottaro.vinculovial.domain.scan.StartAlertAttemptResult

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
    SendAlertError.InvalidData ->
        "No pudimos procesar el reporte. Volvé a intentarlo."

    SendAlertError.RateLimited ->
        "Alcanzaste el límite de reportes. Esperá unos minutos antes de volver a intentar."

    SendAlertError.Unavailable ->
        "El servidor no está disponible en este momento. Intentá de nuevo en unos minutos."

    SendAlertError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."

    SendAlertError.Unexpected ->
        "No pudimos enviar el reporte. Intentá de nuevo en unos minutos."
}
