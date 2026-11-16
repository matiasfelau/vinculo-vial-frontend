package ar.felaufernandezbottaro.vinculovial.data.recovery

import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import ar.felaufernandezbottaro.vinculovial.domain.recovery.RecoverySessionCleaner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Borra la sesión local con el mismo camino que la baja de cuenta: bajo el `Mutex` de
 * [SessionManager], así un refresh en vuelo termina antes de borrar y no puede resucitar una
 * sesión que el servidor ya revocó. Incluye el blob y la clave biométrica.
 */
@Singleton
class RecoverySessionCleanerImpl @Inject constructor(
    private val sessionManager: SessionManager,
) : RecoverySessionCleaner {

    override suspend fun clear() = sessionManager.clearSessionForAccountDeletion()
}
