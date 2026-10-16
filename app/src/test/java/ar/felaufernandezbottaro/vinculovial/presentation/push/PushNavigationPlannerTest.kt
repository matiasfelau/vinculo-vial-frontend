package ar.felaufernandezbottaro.vinculovial.presentation.push

import ar.felaufernandezbottaro.vinculovial.presentation.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushNavigationPlannerTest {

    private val newAlert = PushTarget.NewAlert(alertType = "CRASHED", alertId = null)

    @Test
    fun `sin destino pendiente no reconstruye nada`() {
        assertEquals(emptyList<String>(), planPushNavigation(hasSession = true, target = null))
    }

    @Test
    fun `sin sesion no reconstruye nada, el guard de sesion ya se encarga de mandar a Login`() {
        assertEquals(emptyList<String>(), planPushNavigation(hasSession = false, target = newAlert))
        assertEquals(emptyList<String>(), planPushNavigation(hasSession = false, target = PushTarget.SentUpdated("a")))
    }

    @Test
    fun `con sesion y alerta nueva reconstruye Settings antes de Inbox`() {
        assertEquals(listOf(Route.SETTINGS, Route.INBOX), planPushNavigation(hasSession = true, target = newAlert))
    }

    @Test
    fun `con sesion y novedades de una alerta enviada reconstruye el mismo stack`() {
        assertEquals(
            listOf(Route.SETTINGS, Route.INBOX),
            planPushNavigation(hasSession = true, target = PushTarget.SentUpdated("alerta-1")),
        )
    }

    /** Caso obligatorio de CLAUDE.md: un alert_type desconocido navega igual, sin excepción. */
    @Test
    fun `un alert_type desconocido reconstruye el mismo stack, sin excepcion`() {
        assertEquals(
            listOf(Route.SETTINGS, Route.INBOX),
            planPushNavigation(hasSession = true, target = PushTarget.NewAlert("TIPO_NUEVO_DESCONOCIDO", null)),
        )
    }

    // -- parsePushTarget ---------------------------------------------------------------------

    @Test
    fun `type SENT_UPDATED con alert_id abre el chat del emisor de esa alerta`() {
        assertEquals(PushTarget.SentUpdated("alerta-1"), parsePushTarget("SENT_UPDATED", null, "alerta-1"))
    }

    @Test
    fun `type SENT_UPDATED sin alert_id solo lleva a la lista de Enviados`() {
        assertEquals(PushTarget.SentUpdated(null), parsePushTarget("SENT_UPDATED", null, null))
        assertEquals(PushTarget.SentUpdated(null), parsePushTarget("SENT_UPDATED", null, "  "))
    }

    @Test
    fun `alert_type con alert_id abre el chat del dueño de esa alerta`() {
        assertEquals(PushTarget.NewAlert("CRASHED", "alerta-1"), parsePushTarget(null, "CRASHED", "alerta-1"))
    }

    @Test
    fun `alert_type sin alert_id solo lleva al buzon`() {
        assertEquals(PushTarget.NewAlert("CRASHED", null), parsePushTarget(null, "CRASHED", null))
    }

    @Test
    fun `SENT_UPDATED gana sobre alert_type si vienen los dos`() {
        assertEquals(PushTarget.SentUpdated("a"), parsePushTarget("SENT_UPDATED", "CRASHED", "a"))
    }

    @Test
    fun `un type desconocido sin alert_type no abre nada`() {
        assertNull(parsePushTarget("OTRO_TIPO", null, "a"))
        assertNull(parsePushTarget(null, null, null))
    }

    @Test
    fun `un type desconocido con alert_type se trata como alerta nueva`() {
        assertEquals(PushTarget.NewAlert("CRASHED", null), parsePushTarget("OTRO_TIPO", "CRASHED", null))
    }
}
