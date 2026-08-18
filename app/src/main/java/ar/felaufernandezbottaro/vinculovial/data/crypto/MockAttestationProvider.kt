package ar.felaufernandezbottaro.vinculovial.data.crypto

import javax.inject.Inject

/**
 * Modo de desarrollo: firma localmente un JWT que imita el veredicto de Play Integrity.
 *
 * Sirve para trabajar sin Play Store (emuladores, CI) y para que el Gateway pueda
 * probarse sin credenciales de Google. Nunca debe usarse en release.
 */
class MockAttestationProvider @Inject constructor(
    private val generator: AttestationTokenGenerator,
) : AttestationProvider {

    override suspend fun tokenFor(requestBody: ByteArray): String = try {
        generator.generate(requestHash = requestHashOf(requestBody))
    } catch (e: CryptoConfigurationException) {
        throw AttestationUnavailableException(
            message = "El mock de attestation no está configurado",
            reason = AttestationUnavailableException.Reason.MISCONFIGURED,
            cause = e,
        )
    }
}
