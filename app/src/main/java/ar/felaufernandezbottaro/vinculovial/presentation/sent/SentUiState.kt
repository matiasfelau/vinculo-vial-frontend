package ar.felaufernandezbottaro.vinculovial.presentation.sent

import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert

/**
 * Los mismos cinco estados que el buzón, por la misma razón: "todavía no enviaste ninguna
 * alerta" es un resultado exitoso, no un error.
 */
sealed interface SentUiState {

    /** Primera carga en curso. */
    data object Loading : SentUiState

    /** `200` con `items` vacío. */
    data object Empty : SentUiState

    /** Hay alertas cargadas. [isLoadingMore] cubre la página siguiente sin reemplazar la lista. */
    data class Content(
        val items: List<SentAlert>,
        val nextCursor: String?,
        val isLoadingMore: Boolean = false,
    ) : SentUiState

    /** Falló la primera carga: todavía no hay nada para mostrar. */
    data class Error(val error: InboxError) : SentUiState

    /** Falló una página siguiente: la lista ya cargada se conserva y el error se muestra al pie. */
    data class ContentWithPageError(val items: List<SentAlert>, val error: InboxError) : SentUiState
}

/** Un efecto único del ViewModel de Enviados que la navegación tiene que ejecutar. */
sealed interface SentEvent {

    /** Abrir el chat del emisor de la alerta que quedó seleccionada. */
    data object OpenChat : SentEvent
}
