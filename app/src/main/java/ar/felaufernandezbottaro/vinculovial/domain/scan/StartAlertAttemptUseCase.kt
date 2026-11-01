package ar.felaufernandezbottaro.vinculovial.domain.scan

import javax.inject.Inject

class StartAlertAttemptUseCase @Inject constructor(private val repository: AlertAttemptRepository) {
    suspend operator fun invoke(): StartAlertAttemptResult = repository.startAttempt()
}
