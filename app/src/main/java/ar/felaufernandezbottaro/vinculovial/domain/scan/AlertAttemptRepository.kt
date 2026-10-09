package ar.felaufernandezbottaro.vinculovial.domain.scan

interface AlertAttemptRepository {

    /** Cifra [patente] contra la Public Key de Vehicle y abre un intento de alerta. */
    suspend fun startAttempt(patente: String): StartAlertAttemptResult

    /**
     * Repite la llamada 1 con el [patEnc] que ya se había generado, sin volver a cifrar la
     * patente (la app ya no la tiene en claro): abre un intento nuevo cuando el anterior venció.
     */
    suspend fun renewAttempt(patEnc: String): StartAlertAttemptResult
}
