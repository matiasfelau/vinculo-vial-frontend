package com.vinculovial.app.data.renaper

import com.vinculovial.app.domain.signup.model.DocumentIdentity
import com.vinculovial.app.domain.signup.model.IdentityVerificationRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El stub tiene que fallar ruidosamente, igual que `ElectronicDniQrParser`.
 *
 * El riesgo que cubre este test es el peor de todos: que alguien "complete" el stub
 * devolviendo un `Match` de mentira para poder seguir trabajando, y que eso llegue a
 * producción dando por verificada una identidad que nadie verificó. Que la única salida sea
 * una excepción hace que ese atajo sea imposible de tomar sin darse cuenta.
 */
class RenaperIdentityVerificationProviderTest {

    private val provider = RenaperIdentityVerificationProvider()

    @Test
    fun `no verifica nada y lo dice explicitamente`() = runTest {
        val error = runCatching { provider.verify(request()) }.exceptionOrNull()

        assertTrue("Debe fallar con NotImplementedError", error is NotImplementedError)
        assertEquals("Pendiente de credenciales API del Estado", error?.message)
    }

    @Test
    fun `falla aun con un documento completo, incluido el tramite`() = runTest {
        // Que el request traiga todo lo que RENAPER pediría no lo vuelve implementado.
        val error = runCatching {
            provider.verify(request(procedureNumber = "00307513148"))
        }.exceptionOrNull()

        assertTrue(error is NotImplementedError)
    }

    private fun request(procedureNumber: String? = null) = IdentityVerificationRequest(
        document = DocumentIdentity(number = "12345678", sex = "M", procedureNumber = procedureNumber),
        documentFrontJpeg = ByteArray(16),
        selfieJpeg = ByteArray(16),
    )
}
