package ar.felaufernandezbottaro.vinculovial.domain.scan

import javax.inject.Inject

class StartAlertAttemptUseCase @Inject constructor(private val repository: AlertAttemptRepository) {
    suspend operator fun invoke(patente: String): StartAlertAttemptResult = repository.startAttempt(patente)
}
