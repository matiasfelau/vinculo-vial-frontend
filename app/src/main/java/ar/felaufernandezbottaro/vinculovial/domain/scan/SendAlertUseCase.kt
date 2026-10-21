package ar.felaufernandezbottaro.vinculovial.domain.scan

import ar.felaufernandezbottaro.vinculovial.domain.evidence.EvidencePhotoSet
import java.time.Clock
import javax.inject.Inject

/**
 * Resultado del envío más el intento que quedó vigente: si hubo que renovarlo, es el nuevo, y
 * es el que hay que usar en un reintento manual.
 */
data class SendAlertOutcome(
    val result: SendAlertResult,
    val attempt: StartAlertAttemptResult.Started,
)

/**
 * Envía la alerta del [StartAlertAttemptResult.Started] con sus fotos y el `pat_enc` ya cifrado.
 * Sin `HpkeEncryptor` ni clave pública entre sus dependencias: estructuralmente no puede generar
 * un `pat_enc` nuevo, sólo reenviar el que recibe (la patente en claro ya no existe en este punto).
 *
 * Si el intento ya venció antes de enviar, o el servidor responde 410 `ATTEMPT_EXPIRED`, lo
 * renueva repitiendo la llamada 1 y reenvía **una sola vez**: nunca hay un bucle de renovaciones.
 * El repositorio cifra las fotos con el `attempt_id` que recibe, así que el reenvío sale
 * re-cifrado con el intento nuevo.
 */
class SendAlertUseCase @Inject constructor(
    private val repository: AlertRepository,
    private val attemptRepository: AlertAttemptRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        attempt: StartAlertAttemptResult.Started,
        patEnc: String,
        alertType: AlertType,
        evidence: EvidencePhotoSet,
    ): SendAlertOutcome {
        var current = attempt
        var renewed = false

        if (!current.expiresAt.isAfter(clock.instant())) {
            when (val renewal = renew()) {
                is Renewal.Renewed -> current = renewal.attempt
                is Renewal.Failed -> return SendAlertOutcome(SendAlertResult.Failed(renewal.error), current)
            }
            renewed = true
        }

        var result = repository.sendAlert(current.attemptId, patEnc, alertType, evidence)

        if (result == SendAlertResult.Failed(SendAlertError.AttemptExpired) && !renewed) {
            when (val renewal = renew()) {
                is Renewal.Renewed -> {
                    current = renewal.attempt
                    result = repository.sendAlert(current.attemptId, patEnc, alertType, evidence)
                }
                is Renewal.Failed -> result = SendAlertResult.Failed(renewal.error)
            }
        }

        return SendAlertOutcome(result, current)
    }

    private suspend fun renew(): Renewal =
        when (val result = attemptRepository.startAttempt()) {
            is StartAlertAttemptResult.Started -> Renewal.Renewed(result)
            StartAlertAttemptResult.UnderReview -> Renewal.Failed(SendAlertError.UnderReview)
            StartAlertAttemptResult.RateLimited -> Renewal.Failed(SendAlertError.RateLimited)
            StartAlertAttemptResult.Unavailable -> Renewal.Failed(SendAlertError.Unavailable)
            StartAlertAttemptResult.Unexpected -> Renewal.Failed(SendAlertError.Unexpected)
        }

    private sealed interface Renewal {
        data class Renewed(val attempt: StartAlertAttemptResult.Started) : Renewal
        data class Failed(val error: SendAlertError) : Renewal
    }
}
