package ar.felaufernandezbottaro.vinculovial.domain.emailverification

import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult

/**
 * Verificación del email en el alta (US-13). Puerto de salida: la implementación (cifrado HPKE
 * contra Identity + HTTP) vive en `data`.
 */
interface EmailVerificationRepository {

    /** `POST /auth/email-verifications`: pide el código. Siempre 202 si el email es válido. */
    suspend fun requestCode(email: String): CodeRequestResult

    /** `POST /auth/email-verifications/confirm`: devuelve el `verification_token` del alta. */
    suspend fun confirmCode(email: String, code: String): CodeConfirmResult
}
