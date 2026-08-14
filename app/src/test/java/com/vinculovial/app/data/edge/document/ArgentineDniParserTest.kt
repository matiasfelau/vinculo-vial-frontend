package com.vinculovial.app.data.edge.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El parser es el punto más frágil del pipeline: si el RENAPER emite una disposición
 * nueva, se rompe acá y en ningún otro lado. Por eso se cubren las dos disposiciones en
 * circulación y varias formas de basura.
 */
class ArgentineDniParserTest {

    @Test
    fun `lee la disposicion con el tramite adelante`() {
        val raw = "00307513148@GARCIA@JUAN CARLOS@M@12345678@A@27/01/1990@04/03/2016"

        val dni = ArgentineDniParser.parse(raw)

        assertEquals("12345678", dni?.number)
        assertEquals("GARCIA", dni?.surname)
        assertEquals("JUAN CARLOS", dni?.givenNames)
        assertEquals("M", dni?.sex)
    }

    @Test
    fun `lee la disposicion con el documento adelante`() {
        val raw = "12345678@A@GARCIA@MARIA@F@12345678@00307513148@27/01/1990"

        assertEquals("12345678", ArgentineDniParser.parse(raw)?.number)
    }

    @Test
    fun `conserva los ceros a la izquierda`() {
        // Normalizar es responsabilidad del dominio, no del formato del código.
        val raw = "00307513148@GARCIA@JUAN@M@01234567@A@27/01/1990@04/03/2016"

        assertEquals("01234567", ArgentineDniParser.parse(raw)?.number)
    }

    @Test
    fun `acepta apellidos con acentos y particulas`() {
        val raw = "00307513148@DE LA PEÑA@JOSÉ MARÍA@M@12345678@A@27/01/1990@04/03/2016"

        val dni = ArgentineDniParser.parse(raw)

        assertEquals("DE LA PEÑA", dni?.surname)
        assertEquals("JOSÉ MARÍA", dni?.givenNames)
    }

    @Test
    fun `rechaza un codigo de otro tipo`() {
        assertNull(ArgentineDniParser.parse("https://ejemplo.com/algo"))
        assertNull(ArgentineDniParser.parse(""))
        assertNull(ArgentineDniParser.parse("@@@@@@"))
    }

    @Test
    fun `rechaza si no hay campo de sexo`() {
        val raw = "00307513148@GARCIA@JUAN@X@12345678@A@27/01/1990@04/03/2016"

        assertNull(ArgentineDniParser.parse(raw))
    }

    @Test
    fun `rechaza si faltan campos`() {
        assertNull(ArgentineDniParser.parse("GARCIA@JUAN@M@12345678"))
    }

    @Test
    fun `rechaza si el numero no tiene forma de dni`() {
        val raw = "00307513148@GARCIA@JUAN@M@123@A@27/01/1990@04/03/2016"

        assertNull(ArgentineDniParser.parse(raw))
    }

    @Test
    fun `no expone los datos del titular al renderizarse`() {
        val raw = "00307513148@GARCIA@JUAN@M@12345678@A@27/01/1990@04/03/2016"

        val rendered = ArgentineDniParser.parse(raw).toString()

        assertEquals(true, rendered.contains("***"))
        assertEquals(false, rendered.contains("GARCIA"))
        assertEquals(false, rendered.contains("12345678"))
    }
}
