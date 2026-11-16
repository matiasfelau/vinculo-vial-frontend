package ar.felaufernandezbottaro.vinculovial.domain.scan

interface AlertAttemptRepository {

    /**
     * Abre un intento de alerta. Lleva el [patEnc] (la patente cifrada para Vehicle, el mismo
     * criptograma del envío) para que el servidor avise ya, antes de sacar las fotos, si el cupo
     * de la patente está ocupado ([StartAlertAttemptResult.DailyLimit]). Sirve igual para el
     * intento inicial y para renovarlo cuando venció.
     */
    suspend fun startAttempt(patEnc: String): StartAlertAttemptResult
}
