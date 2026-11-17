package ar.felaufernandezbottaro.vinculovial.domain.scan

import java.time.Instant

/**
 * Resultado de `POST /alerts/attempts` visto desde el dominio. `presentation` nunca ve un
 * código HTTP ni una excepción cruda: sólo estos casos. El intento no resuelve dueños: la app
 * nunca se entera de si el vehículo tiene dueños (el cupo diario es igual con dueño o sin él).
 */
sealed interface StartAlertAttemptResult {

    /**
     * 201: el emisor puede alertar. El [attemptId] ata las fotos de evidencia (es el `info` del
     * HPKE) y se vuelve el `alert_id`. El intento vence en [expiresAt].
     */
    data class Started(
        val attemptId: String,
        val expiresAt: Instant,
    ) : StartAlertAttemptResult

    /** Los casos en los que el intento no arrancó y la UI muestra un error. */
    sealed interface Failure : StartAlertAttemptResult

    /** 403 `ISSUER_UNDER_REVIEW`: la cuenta del emisor está en revisión y no puede crear alertas. */
    data object UnderReview : Failure

    /**
     * 409 `ALERT_DAILY_LIMIT`: el emisor ya tiene una alerta para esta patente dentro de la
     * ventana de 24 h. [availableAt] es cuándo se libera el cupo (RFC 3339 en el contrato).
     */
    data class DailyLimit(val availableAt: Instant) : Failure

    /** 429: límite de Kong alcanzado (10 req/min, 30 req/hora por `X-User-Id`). */
    data object RateLimited : Failure

    /** 502/503/504, circuito abierto o sin conexión: el servidor no está disponible. Transitorio. */
    data object Unavailable : Failure

    /** Cualquier otra cosa, incluido un 401 que TokenAuthenticator no pudo resolver y la configuración de build. */
    data object Unexpected : Failure
}
