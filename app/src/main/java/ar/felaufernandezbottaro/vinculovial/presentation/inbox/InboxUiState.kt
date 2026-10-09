package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.AlertItem
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
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

/** Cómo va la descarga de una foto de evidencia redactada. */
sealed interface EvidenceLoad {

    data object Loading : EvidenceLoad

    /** Los bytes del JPEG, sólo en memoria. El `ViewModel` los sobreescribe con ceros al cerrar el detalle. */
    class Loaded(val bytes: ByteArray) : EvidenceLoad

    /** 410: la retención venció. La UI muestra "La evidencia ya no está disponible". */
    data object Expired : EvidenceLoad

    data class Failed(val error: InboxError) : EvidenceLoad
}

data class EvidenceSlot(val ref: EvidenceRef, val load: EvidenceLoad)

/** El reporte al emisor del detalle. Que ya esté reportado vive en [AlertItem.reported]. */
sealed interface ReportUiState {
    data object Idle : ReportUiState
    data object Sending : ReportUiState
    data class Failed(val error: InboxError) : ReportUiState
}

/**
 * El detalle de una alerta del buzón: la evidencia redactada, la valoración del dueño y el
 * reporte al emisor. [item] se mantiene al día con la valoración y el reporte, y la lista se
 * actualiza con los mismos valores.
 *
 * @property feedbackFailed la última valoración no se pudo guardar y se deshizo.
 */
data class AlertDetailUiState(
    val item: AlertItem,
    val evidence: List<EvidenceSlot>,
    val feedbackFailed: Boolean = false,
    val report: ReportUiState = ReportUiState.Idle,
)
