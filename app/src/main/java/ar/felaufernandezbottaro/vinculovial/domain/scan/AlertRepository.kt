package ar.felaufernandezbottaro.vinculovial.domain.scan

import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePhotoSet

interface AlertRepository {

    /**
     * Publica la alerta del intento [attemptId] con sus fotos. Las fotos se cifran acá, con
     * [attemptId] como `info`: con otro `attemptId` el resultado es otro criptograma, y es así
     * como se re-cifra tras renovar un intento vencido. [evidence] no se modifica ni se libera.
     */
    suspend fun sendAlert(attemptId: String, alertType: AlertType, evidence: EvidencePhotoSet): SendAlertResult
}
