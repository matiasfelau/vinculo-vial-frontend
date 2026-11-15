package ar.felaufernandezbottaro.vinculovial.data.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ar.felaufernandezbottaro.vinculovial.di.ApplicationScope
import ar.felaufernandezbottaro.vinculovial.di.SessionLockAfterBackgroundSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Reloj monotónico: no lo mueve el usuario cambiando la hora del dispositivo. */
fun interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}

/**
 * Los disparadores de bloqueo de US-12 que no son la muerte del proceso (esa no necesita
 * código: la memoria desaparece sola y el próximo arranque queda LOCKED o NONE según haya blob).
 *
 * - La Activity termina (`isFinishing` y sin cambio de configuración): [onActivityFinished].
 * - La app vuelve a primer plano tras `SESSION_LOCK_AFTER_BACKGROUND_SECONDS` o más en segundo
 *   plano: [onAppBackgrounded] y [onAppForegrounded], que llama el observador de
 *   `ProcessLifecycleOwner`.
 *
 * El bloqueo en sí lo hace [SessionManager.lock] bajo su lock; acá sólo se decide cuándo, en el
 * alcance de la aplicación para que un `lifecycleScope` cancelado no lo corte a mitad de camino.
 */
@Singleton
class SessionLockCoordinator @Inject constructor(
    private val sessionManager: SessionManager,
    private val clock: MonotonicClock,
    @SessionLockAfterBackgroundSeconds lockAfterBackgroundSeconds: Long,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val lockAfterBackgroundMillis = TimeUnit.SECONDS.toMillis(lockAfterBackgroundSeconds)

    @Volatile
    private var backgroundedAtMillis: Long? = null

    fun onAppBackgrounded() {
        backgroundedAtMillis = clock.elapsedRealtimeMillis()
    }

    fun onAppForegrounded() {
        val since = backgroundedAtMillis ?: return
        backgroundedAtMillis = null
        if (clock.elapsedRealtimeMillis() - since >= lockAfterBackgroundMillis) lockNow()
    }

    fun onActivityFinished() = lockNow()

    private fun lockNow() {
        scope.launch { sessionManager.lock() }
    }
}

/** Puente entre `ProcessLifecycleOwner` y [SessionLockCoordinator]. */
class SessionLockLifecycleObserver(
    private val coordinator: SessionLockCoordinator,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) = coordinator.onAppForegrounded()

    override fun onStop(owner: LifecycleOwner) = coordinator.onAppBackgrounded()
}
