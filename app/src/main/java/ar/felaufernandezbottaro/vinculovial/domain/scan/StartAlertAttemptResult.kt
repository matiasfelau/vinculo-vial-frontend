package ar.felaufernandezbottaro.vinculovial.domain.scan

import java.time.Instant

/**
 * Resultado de `POST /alerts/attempts` visto desde el dominio. `presentation` nunca ve un
 * código HTTP ni una excepción cruda: sólo estos casos.
 */
sealed interface StartAlertAttemptResult {

    /**
     * 201: hay al menos un dueño que no es el emisor. Lleva el `pat_enc` ya generado para esta
     * patente -- el envío de la alerta lo reusa tal cual, sin volver a cifrar -- y el
     * [attemptId] que ata las fotos de evidencia. El intento vence en [expiresAt].
     */
    data class Started(
        val attemptId: String,
        val expiresAt: Instant,
        val patEnc: String,
    ) : StartAlertAttemptResult {
        override fun toString(): String = "Started(attemptId=$attemptId, expiresAt=$expiresAt, patEnc=***)"
    }

    /** 404: el vehículo no está en la red. La UI muestra un mensaje neutro, sin ofrecer alertar igual. */
    data object NotInNetwork : StartAlertAttemptResult

    /** Los casos en los que el intento no arrancó y la UI muestra un error. */
    sealed interface Failure : StartAlertAttemptResult

    /** 403 `ISSUER_UNDER_REVIEW`: la cuenta del emisor está en revisión y no puede crear alertas. */
    data object UnderReview : Failure

    /** 400: Vehicle rechazó el criptograma o el formato de la patente. */
    data object InvalidPlate : Failure

    /** 429: límite de Kong alcanzado (10 req/min, 30 req/hora por `X-User-Id`). */
    data object RateLimited : Failure

    /** 502/503/504, circuito abierto o sin conexión: el servidor no está disponible. Transitorio. */
    data object Unavailable : Failure

    /** Cualquier otra cosa, incluido un 401 que TokenAuthenticator no pudo resolver y la configuración de build. */
    data object Unexpected : Failure
}
