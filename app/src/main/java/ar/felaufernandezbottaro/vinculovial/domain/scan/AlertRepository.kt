package ar.felaufernandezbottaro.vinculovial.domain.scan

interface AlertRepository {

    /**
     * Publica la alerta con [patEnc] tal cual se recibió -- nunca vuelve a cifrar la patente.
     * [patEnc] tiene que ser el mismo que ya devolvió `/vehicles/check`, según el flujo de dos
     * pasos de CLAUDE.md.
     */
    suspend fun sendAlert(alertType: AlertType, patEnc: String): SendAlertResult
}
