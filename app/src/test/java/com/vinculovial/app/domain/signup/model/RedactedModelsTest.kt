package com.vinculovial.app.domain.signup.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `Credentials` y `DocumentIdentity` cargan datos personales (DNI, sexo, trámite, email,
 * contraseña, la selfie en Base64). Un `toString()` por defecto los volcaría enteros al
 * primer log o reporte de crash que capture el objeto — por eso los dos lo pisan. Este test
 * es la guarda contra que alguien lo borre sin darse cuenta al tocar estas clases.
 */
class RedactedModelsTest {

    @Test
    fun `Credentials no expone ningun campo al renderizarse`() {
        val credentials = Credentials(
            dni = "12345678",
            procedureNumber = "00307513148",
            sex = "M",
            email = "usuario@ejemplo.com",
            password = "Password123!",
            selfieBase64 = "c2VsZmll",
        )

        val rendered = credentials.toString()

        assertEquals("Credentials(***)", rendered)
        listOf("12345678", "00307513148", "usuario@ejemplo.com", "Password123!", "c2VsZmll").forEach {
            assertFalse("el toString filtró '$it'", rendered.contains(it))
        }
    }

    @Test
    fun `DocumentIdentity no expone ningun campo al renderizarse`() {
        val identity = DocumentIdentity(number = "12345678", sex = "M", procedureNumber = "00307513148")

        val rendered = identity.toString()

        assertEquals("DocumentIdentity(***)", rendered)
        listOf("12345678", "00307513148").forEach {
            assertFalse("el toString filtró '$it'", rendered.contains(it))
        }
    }
}
