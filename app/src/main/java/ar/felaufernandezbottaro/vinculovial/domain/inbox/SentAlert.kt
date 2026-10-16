package ar.felaufernandezbottaro.vinculovial.domain.inbox

import java.time.Instant

/** El `status` de `GET /alerts/sent`, tal cual lo expone Alert. */
enum class SentAlertStatus {
    PENDING_VALIDATION,
    ACTIVE,
    REJECTED,
    EXPIRED,
}

/** En qué punto está una alerta enviada, según lo que el emisor puede saber. */
enum class SentAlertState {
    IN_VALIDATION,
    VALIDATED,
    SEEN,
    REJECTED,
    NO_RESPONSE,
}

/**
 * Una alerta que envió el usuario, tal como la expone el contrato de `/alerts/sent`: sólo el
 * tipo, la fecha, el estado y cuándo la leyó el dueño. A propósito no hay nada más: ni veredicto,
 * ni motivo de rechazo, ni evidencia, ni datos de los dueños. El emisor sabe *cuándo* se leyó,
 * nunca *quién* la leyó.
 */
data class SentAlert(
    val id: String,
    val alertType: String,
    val createdAt: Instant,
    val status: SentAlertStatus,
    val seenAt: Instant? = null,
) {
    /** `ACTIVE` con `seen_at` es "leída"; sin `seen_at`, "validada". */
    val state: SentAlertState
        get() = when (status) {
            SentAlertStatus.PENDING_VALIDATION -> SentAlertState.IN_VALIDATION
            SentAlertStatus.ACTIVE -> if (seenAt != null) SentAlertState.SEEN else SentAlertState.VALIDATED
            SentAlertStatus.REJECTED -> SentAlertState.REJECTED
            SentAlertStatus.EXPIRED -> SentAlertState.NO_RESPONSE
        }
}

/** Una página de Enviados. [nextCursor] es opaco, igual que en el buzón: `null` si no hay más. */
data class SentPage(
    val items: List<SentAlert>,
    val nextCursor: String?,
)

sealed interface GetSentResult {

    data class Success(val page: SentPage) : GetSentResult

    data class Failed(val error: InboxError) : GetSentResult
}

/** Resultado de `PUT /alerts/{id}/seen`. */
sealed interface SeenResult {

    /** 204. */
    data object Marked : SeenResult

    data class Failed(val error: InboxError) : SeenResult
}
