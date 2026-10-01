package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError

fun InboxError.message(): String = when (this) {
    InboxError.RateLimited ->
        "Alcanzaste el límite de consultas. Esperá unos minutos antes de volver a intentar."

    InboxError.Unavailable ->
        "El servidor no está disponible en este momento. Intentá de nuevo en unos minutos."

    InboxError.Network ->
        "No pudimos comunicarnos con el servidor. Revisá tu conexión e intentá de nuevo."

    InboxError.Unexpected ->
        "No pudimos cargar tus alertas. Intentá de nuevo en unos minutos."
}
