package ar.felaufernandezbottaro.vinculovial.domain.scan

import java.time.Instant

/**
 * Repositorio falso: devuelve [result] en cada llamada y cuenta cuántas hubo. Como el intento
 * inicial y su renovación son la misma llamada, un test que quiere otro intento en la renovación
 * cambia [result] después del primero.
 */
class FakeAlertAttemptRepository(
    var result: StartAlertAttemptResult = started(),
) : AlertAttemptRepository {

    /** Cuántas veces se pidió un intento (el inicial y cada renovación). */
    var calls = 0
        private set

    /** El `pat_enc` de cada llamada, en orden. */
    val receivedPatEnc = mutableListOf<String>()

    override suspend fun startAttempt(patEnc: String): StartAlertAttemptResult {
        calls++
        receivedPatEnc += patEnc
        return result
    }

    companion object {
        fun started(
            attemptId: String = "attempt-de-prueba",
            expiresAt: Instant = Instant.parse("2030-01-01T00:10:00Z"),
        ) = StartAlertAttemptResult.Started(attemptId = attemptId, expiresAt = expiresAt)
    }
}
