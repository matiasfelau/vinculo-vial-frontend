package ar.felaufernandezbottaro.vinculovial.presentation.navigation

import ar.felaufernandezbottaro.vinculovial.data.session.SessionState

/*
 * La regla de mundos (#9). La app tiene tres:
 * - público: Splash, Bienvenida, Login y Alta;
 * - desbloqueo: `Route.UNLOCK`, un mundo propio;
 * - autenticado: Home (`Route.SETTINGS`) y todo lo demás.
 *
 * Toda transición entre mundos usa `popUpTo(0) { inclusive = true }`, así Back en Home o en
 * Desbloqueo nunca muestra una pantalla de otro mundo. Las funciones de este archivo deciden
 * *adónde* ir; son puras para probarlas sin Compose.
 */

/** Pantallas que no requieren sesión. */
internal fun isPublicRoute(route: String?): Boolean =
    route == null ||
        route == Route.SPLASH ||
        route == Route.WELCOME ||
        route == Route.SIGN_IN ||
        route.startsWith(Route.SIGN_UP_GRAPH)

/**
 * La baja de cuenta (US-11) borra la sesión local ella misma, y el destino de ese momento no es
 * Login sino la bienvenida. Si el redireccionamiento de [worldRedirect] también reaccionara,
 * las dos navegaciones competirían y ganaría la que corra última. Por eso, mientras el usuario
 * está en esa pantalla sin sesión, el redireccionamiento le deja la salida a ella: ver la ruta
 * `DELETE_ACCOUNT` en el nav host.
 */
private fun managesOwnSessionLoss(route: String?): Boolean = route == Route.DELETE_ACCOUNT

/** El Splash decide a qué mundo entra la app según el estado de la sesión. */
fun splashDestination(state: SessionState): String = when (state) {
    SessionState.LOCKED -> Route.UNLOCK
    SessionState.NONE -> Route.WELCOME
    SessionState.UNLOCKED -> Route.SETTINGS
}

/**
 * Adónde hay que mover al usuario si la pantalla actual no es de su mundo, o `null` si está bien
 * donde está. El destino se alcanza con `popUpTo(0) { inclusive = true }`.
 *
 * - Sin sesión ([SessionState.NONE]): sólo el mundo público. Cualquier otra pantalla vuelve a
 *   Login -- es lo que reacciona a una sesión perdida en segundo plano --, salvo la baja de
 *   cuenta, que decide ella misma (ver [managesOwnSessionLoss]).
 * - Bloqueada ([SessionState.LOCKED]): el Desbloqueo y el mundo público. Público porque "Usar
 *   contraseña" lleva a Login con la sesión todavía bloqueada. Una pantalla autenticada vuelve al
 *   Desbloqueo.
 * - Desbloqueada ([SessionState.UNLOCKED]): sólo el mundo autenticado. Login, Bienvenida y
 *   Desbloqueo -- lo que queda en pantalla justo cuando el login o el desbloqueo terminan --
 *   llevan al Home.
 *
 * El Splash decide por su cuenta ([splashDestination]).
 */
fun worldRedirect(state: SessionState, route: String?): String? {
    if (route == null || route == Route.SPLASH) return null
    return when (state) {
        SessionState.NONE ->
            if (isPublicRoute(route) || managesOwnSessionLoss(route)) null else Route.SIGN_IN

        SessionState.LOCKED ->
            if (isPublicRoute(route) || route == Route.UNLOCK) null else Route.UNLOCK

        SessionState.UNLOCKED ->
            if (isPublicRoute(route) || route == Route.UNLOCK) Route.SETTINGS else null
    }
}
