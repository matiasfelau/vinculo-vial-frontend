package ar.felaufernandezbottaro.vinculovial.domain.scan

import javax.inject.Inject

/**
 * Sin `HpkeEncryptor` ni clave pública entre sus dependencias: estructuralmente no puede
 * generar un nuevo `pat_enc`, sólo reusar el que ya produjo `StartAlertAttemptUseCase`.
 */
class SendAlertUseCase @Inject constructor(private val repository: AlertRepository) {
    suspend operator fun invoke(alertType: AlertType, patEnc: String): SendAlertResult =
        repository.sendAlert(alertType, patEnc)
}
