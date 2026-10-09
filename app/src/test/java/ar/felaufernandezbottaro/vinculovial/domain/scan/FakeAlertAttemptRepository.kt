package ar.felaufernandezbottaro.vinculovial.domain.scan

import java.time.Instant

/** Repositorio falso: registra las patentes recibidas y devuelve lo programado. */
class FakeAlertAttemptRepository(
    var result: StartAlertAttemptResult = StartAlertAttemptResult.Started(
        attemptId = "attempt-de-prueba",
        expiresAt = Instant.parse("2030-01-01T00:10:00Z"),
        patEnc = "pat-enc-de-prueba",
    ),
) : AlertAttemptRepository {

    val received = mutableListOf<String>()

    override suspend fun startAttempt(patente: String): StartAlertAttemptResult {
        received += patente
        return result
    }
}
