package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BenefitTextsTest {

    @Test
    fun `la vigencia es Hasta el dd barra mm`() {
        assertEquals("Hasta el 30/11", BenefitTexts.validUntil(Instant.parse("2030-11-30T15:00:00Z")))
        assertEquals("Hasta el 05/01", BenefitTexts.validUntil(Instant.parse("2031-01-05T15:00:00Z")))
    }

    @Test
    fun `la vigencia se cuenta en hora argentina y no en UTC`() {
        // 01:00 UTC del 1/12 es 22:00 del 30/11 en Buenos Aires.
        assertEquals("Hasta el 30/11", BenefitTexts.validUntil(Instant.parse("2030-12-01T01:00:00Z")))
        assertEquals("Hasta el 01/12", BenefitTexts.validUntil(Instant.parse("2030-12-01T01:00:00Z"), ZoneId.of("UTC")))
    }

    @Test
    fun `la indicacion lleva el nombre del partner`() {
        assertEquals("Ingresá el código en la aplicación de Estación de prueba", BenefitTexts.enterCodeIn("Estación de prueba"))
    }

    @Test
    fun `los textos fijos son los del contrato`() {
        assertEquals("Obtener código", BenefitTexts.GET_CODE)
        assertEquals("Copiar", BenefitTexts.COPY)
        assertEquals("Abrir", BenefitTexts.OPEN)
        assertEquals("Código copiado", BenefitTexts.CODE_COPIED)
    }
}
