package ar.felaufernandezbottaro.vinculovial.domain.benefits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenefitTest {

    @Test
    fun `solo se abre un https con host`() {
        assertTrue(benefit(openUrl = "https://partner.example/app").canOpen)
        assertTrue(benefit(openUrl = "HTTPS://partner.example/app").canOpen)
        assertTrue(benefit(openUrl = "https://play.google.com/store/apps/details?id=ar.partner").canOpen)
    }

    @Test
    fun `cualquier otro esquema, o una URL rota, no se ofrece`() {
        listOf(
            "http://partner.example/app",
            "intent://scan/#Intent;scheme=zxing;end",
            "javascript:alert(1)",
            "content://contacts/people",
            "file:///sdcard/x",
            "partner://app",
            "https://",
            "https:///solo-path",
            "no es una url",
            "https://partner example/",
            "",
        ).forEach { url -> assertFalse(url, benefit(openUrl = url).canOpen) }
    }

    @Test
    fun `el toString no imprime el codigo`() {
        val text = benefit(code = "SECRETO-9999").toString()

        assertFalse("SECRETO-9999" in text)
        assertTrue("code=***" in text)
        assertEquals(true, "code=null" in benefit(code = null).toString())
    }
}
