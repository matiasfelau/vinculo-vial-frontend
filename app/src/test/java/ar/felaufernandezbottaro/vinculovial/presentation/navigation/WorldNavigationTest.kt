package ar.felaufernandezbottaro.vinculovial.presentation.navigation

import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La regla de mundos (#9), sin Compose ni NavController. */
class WorldNavigationTest {

    private val publicRoutes = listOf(
        Route.WELCOME,
        Route.SIGN_IN,
        Route.SIGN_UP_GRAPH,
        Route.SIGN_UP_FORM,
        Route.SIGN_UP_CONSENT,
        Route.SIGN_UP_CAPTURE,
        Route.SIGN_UP_SUCCESS,
        Route.SIGN_UP_DOCUMENT_SCAN,
        Route.SIGN_UP_LIVENESS,
    )

    private val authenticatedRoutes = listOf(
        Route.SETTINGS,
        Route.DELETE_ACCOUNT,
        Route.LINK_VEHICLE,
        Route.SCAN_GRAPH,
        Route.SCAN_PLATE,
        Route.REPORT_CHAT,
        Route.EVIDENCE_CAPTURE,
        Route.INBOX,
        Route.INBOX_DETAIL,
        Route.SENT_CHAT,
    )

    // --- Splash decide ---

    @Test
    fun `el Splash manda a Desbloqueo si la sesion esta bloqueada`() {
        assertEquals(Route.UNLOCK, splashDestination(SessionState.LOCKED))
    }

    @Test
    fun `el Splash manda a Bienvenida si no hay sesion`() {
        assertEquals(Route.WELCOME, splashDestination(SessionState.NONE))
    }

    @Test
    fun `el Splash manda al Home si la sesion ya esta desbloqueada`() {
        assertEquals(Route.SETTINGS, splashDestination(SessionState.UNLOCKED))
    }

    @Test
    fun `el Splash no se redirige por su cuenta en ningun estado`() {
        SessionState.entries.forEach { assertNull(worldRedirect(it, Route.SPLASH)) }
    }

    @Test
    fun `sin ruta todavia no se redirige`() {
        SessionState.entries.forEach { assertNull(worldRedirect(it, null)) }
    }

    // --- Sin sesión ---

    @Test
    fun `sin sesion, el mundo publico se queda donde esta`() {
        publicRoutes.forEach { assertNull(it, worldRedirect(SessionState.NONE, it)) }
    }

    @Test
    fun `sin sesion, cualquier pantalla autenticada vuelve a Login`() {
        (authenticatedRoutes - Route.DELETE_ACCOUNT).forEach {
            assertEquals(it, Route.SIGN_IN, worldRedirect(SessionState.NONE, it))
        }
    }

    @Test
    fun `sin sesion, el Desbloqueo vuelve a Login (sesion vencida o huellas cambiadas)`() {
        assertEquals(Route.SIGN_IN, worldRedirect(SessionState.NONE, Route.UNLOCK))
    }

    @Test
    fun `sin sesion, la baja de cuenta decide su propia salida`() {
        assertNull(worldRedirect(SessionState.NONE, Route.DELETE_ACCOUNT))
    }

    // --- Bloqueada ---

    @Test
    fun `bloqueada, el Desbloqueo se queda donde esta`() {
        assertNull(worldRedirect(SessionState.LOCKED, Route.UNLOCK))
    }

    @Test
    fun `bloqueada, Usar contrasena puede quedarse en Login y en el alta`() {
        publicRoutes.forEach { assertNull(it, worldRedirect(SessionState.LOCKED, it)) }
    }

    @Test
    fun `bloqueada, cualquier pantalla autenticada va a Desbloqueo`() {
        authenticatedRoutes.forEach { assertEquals(it, Route.UNLOCK, worldRedirect(SessionState.LOCKED, it)) }
    }

    @Test
    fun `bloqueada, la baja de cuenta tambien va a Desbloqueo`() {
        assertEquals(Route.UNLOCK, worldRedirect(SessionState.LOCKED, Route.DELETE_ACCOUNT))
    }

    // --- Desbloqueada ---

    @Test
    fun `desbloqueada, el mundo autenticado se queda donde esta`() {
        authenticatedRoutes.forEach { assertNull(it, worldRedirect(SessionState.UNLOCKED, it)) }
    }

    @Test
    fun `desbloqueada, Login y Bienvenida y el alta llevan al Home (el login exitoso ya no deja Bienvenida debajo)`() {
        publicRoutes.forEach { assertEquals(it, Route.SETTINGS, worldRedirect(SessionState.UNLOCKED, it)) }
    }

    @Test
    fun `desbloqueada, el Desbloqueo lleva al Home`() {
        assertEquals(Route.SETTINGS, worldRedirect(SessionState.UNLOCKED, Route.UNLOCK))
    }

    // --- Consistencia ---

    @Test
    fun `ninguna redireccion termina en una ruta que vuelva a redirigir (sin ciclos)`() {
        val routes = publicRoutes + authenticatedRoutes + Route.UNLOCK
        for (state in SessionState.entries) {
            for (route in routes) {
                val target = worldRedirect(state, route) ?: continue
                assertNull("$state: $route -> $target", worldRedirect(state, target))
            }
        }
    }
}
