package ar.felaufernandezbottaro.vinculovial.domain.scan

interface AlertAttemptRepository {

    /** Cifra [patente] contra la Public Key de Vehicle y abre un intento de alerta. */
    suspend fun startAttempt(patente: String): StartAlertAttemptResult
}
