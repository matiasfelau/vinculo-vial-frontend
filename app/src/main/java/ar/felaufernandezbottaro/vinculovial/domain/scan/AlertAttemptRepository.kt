package ar.felaufernandezbottaro.vinculovial.domain.scan

interface AlertAttemptRepository {

    /**
     * Abre un intento de alerta, sin body. Sirve igual para el intento inicial y para renovarlo
     * cuando venció: el `pat_enc` ya no participa, viaja recién con la alerta.
     */
    suspend fun startAttempt(): StartAlertAttemptResult
}
