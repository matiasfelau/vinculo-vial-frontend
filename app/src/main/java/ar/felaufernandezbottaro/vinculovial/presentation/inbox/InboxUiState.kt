package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError

/**
 * Cinco estados, no dos: colapsar [Empty] dentro de [Error] es el defecto más común en esta
 * pantalla -- una bandeja sin incidentes es un resultado exitoso, no una falla.
 */
sealed interface InboxUiState {

    /** Primera carga en curso. */
    data object Loading : InboxUiState

    /** `200` con `items` vacío: resultado exitoso, no un error. */
    data object Empty : InboxUiState

    /**
     * Hay alertas cargadas. [isLoadingMore] cubre la carga de la página siguiente sin
     * reemplazar la lista ya visible; [nextCursor] es `null` cuando se agotó el historial.
     */
    data class Content(
        val items: List<AlertItem>,
        val nextCursor: String?,
        val isLoadingMore: Boolean = false,
    ) : InboxUiState

    /** Fallo de red o `429` en la primera carga: todavía no hay nada para mostrar. */
    data class Error(val error: InboxError) : InboxUiState

    /**
     * Falló la carga de una página siguiente: la lista ya cargada se conserva y el error se
     * muestra al pie, con opción de reintentar -- nunca se reemplaza la pantalla completa por
     * un error porque falló la página 3.
     */
    data class ContentWithPageError(val items: List<AlertItem>, val error: InboxError) : InboxUiState
}
