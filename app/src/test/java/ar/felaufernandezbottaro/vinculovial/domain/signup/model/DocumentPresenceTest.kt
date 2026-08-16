package ar.felaufernandezbottaro.vinculovial.domain.signup.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isReadyToCapture` es el gatillo del disparo automático: si devuelve `true` de más, la app
 * saca fotos de cualquier cosa; si devuelve `true` de menos, no saca ninguna y el usuario
 * queda esperando. Por eso se fija exactamente en qué estado dispara.
 */
class DocumentPresenceTest {

    @Test
    fun `solo dispara cuando la escena esta quieta`() {
        assertTrue(DocumentPresence(stage = DocumentPresenceStage.STEADY).isReadyToCapture)
    }

    @Test
    fun `no dispara mientras todavia se esta moviendo`() {
        // DETECTED significa que las dos geometrías están, pero la mano sigue moviéndose:
        // disparar acá es justo la foto movida que el auto-disparo viene a evitar.
        assertFalse(DocumentPresence(stage = DocumentPresenceStage.DETECTED).isReadyToCapture)
    }

    @Test
    fun `no dispara si no se ve nada`() {
        assertFalse(DocumentPresence().isReadyToCapture)
        assertFalse(DocumentPresence(stage = DocumentPresenceStage.SEARCHING).isReadyToCapture)
    }

    @Test
    fun `el estado inicial no dispara`() {
        // El default se publica al arrancar y al reanudar tras una foto rechazada; si
        // disparara, cada rechazo encadenaría otro disparo inmediato sobre la misma escena.
        assertFalse(DocumentPresence().isReadyToCapture)
    }

    @Test
    fun `las geometrias por si solas no alcanzan para disparar`() {
        val vistoPeroMoviendose = DocumentPresence(
            stage = DocumentPresenceStage.DETECTED,
            hasBarcode = true,
            hasFace = true,
        )

        assertFalse(vistoPeroMoviendose.isReadyToCapture)
    }
}
