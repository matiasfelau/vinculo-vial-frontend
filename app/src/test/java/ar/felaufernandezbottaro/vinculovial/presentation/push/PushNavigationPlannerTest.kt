package ar.felaufernandezbottaro.vinculovial.presentation.push

import ar.felaufernandezbottaro.vinculovial.presentation.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class PushNavigationPlannerTest {

    @Test
    fun `sin alert_type pendiente no reconstruye nada`() {
        assertEquals(
            emptyList<String>(),
            planPushNavigation(hasSession = true, pendingAlertType = null),
        )
    }

    @Test
    fun `sin sesion no reconstruye nada, el guard de sesion ya se encarga de mandar a Login`() {
        assertEquals(
            emptyList<String>(),
            planPushNavigation(hasSession = false, pendingAlertType = "CRASHED"),
        )
    }

    @Test
    fun `con sesion y alert_type pendiente reconstruye Settings antes de Inbox`() {
        assertEquals(
            listOf(Route.SETTINGS, Route.INBOX),
            planPushNavigation(hasSession = true, pendingAlertType = "CRASHED"),
        )
    }

    /** Caso obligatorio de CLAUDE.md: un alert_type desconocido navega igual, sin excepción. */
    @Test
    fun `un alert_type desconocido reconstruye el mismo stack, sin excepcion`() {
        assertEquals(
            listOf(Route.SETTINGS, Route.INBOX),
            planPushNavigation(hasSession = true, pendingAlertType = "TIPO_NUEVO_DESCONOCIDO"),
        )
    }
}
