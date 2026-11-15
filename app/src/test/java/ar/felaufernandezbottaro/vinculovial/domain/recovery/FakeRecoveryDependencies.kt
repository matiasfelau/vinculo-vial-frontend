package ar.felaufernandezbottaro.vinculovial.domain.recovery

import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.emailverification.EmailVerificationRepository

/** Repositorio falso de la verificación del email: registra los pedidos y devuelve lo programado. */
class FakeEmailVerificationRepository(
    var requestResult: CodeRequestResult = CodeRequestResult.Sent,
    var confirmResult: CodeConfirmResult = CodeConfirmResult.Confirmed("token-de-verificacion"),
) : EmailVerificationRepository {

    val requestedEmails = mutableListOf<String>()
    val confirmations = mutableListOf<Pair<String, String>>()

    override suspend fun requestCode(email: String): CodeRequestResult {
        requestedEmails += email
        return requestResult
    }

    override suspend fun confirmCode(email: String, code: String): CodeConfirmResult {
        confirmations += email to code
        return confirmResult
    }
}

/** Repositorio falso de la recuperación: registra los pedidos y devuelve lo programado. */
class FakePasswordResetRepository(
    var requestResult: CodeRequestResult = CodeRequestResult.Sent,
    var confirmResult: CodeConfirmResult = CodeConfirmResult.Confirmed("reset-token"),
    var completeResult: PasswordResetResult = PasswordResetResult.Done,
) : PasswordResetRepository {

    val requestedDnis = mutableListOf<String>()
    val confirmations = mutableListOf<Pair<String, String>>()
    val completions = mutableListOf<PasswordResetCompletion>()

    override suspend fun requestCode(dni: String): CodeRequestResult {
        requestedDnis += dni
        return requestResult
    }

    override suspend fun confirmCode(dni: String, code: String): CodeConfirmResult {
        confirmations += dni to code
        return confirmResult
    }

    override suspend fun complete(request: PasswordResetCompletion): PasswordResetResult {
        completions += request
        return completeResult
    }
}

/** Limpieza de sesión falsa: cuenta cuántas veces se borró lo local. */
class FakeRecoverySessionCleaner : RecoverySessionCleaner {

    var clears = 0

    override suspend fun clear() {
        clears++
    }
}
