package ar.felaufernandezbottaro.vinculovial

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import ar.felaufernandezbottaro.vinculovial.data.push.TokenSyncManager
import ar.felaufernandezbottaro.vinculovial.data.push.ensureAlertsChannelCreated
import ar.felaufernandezbottaro.vinculovial.data.session.LegacySessionMigration
import ar.felaufernandezbottaro.vinculovial.data.session.SessionLockCoordinator
import ar.felaufernandezbottaro.vinculovial.data.session.SessionLockLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VinculoVialApplication : Application() {

    // Nunca se usa directo: inyectarlo fuerza a Hilt a construirlo apenas arranca el
    // proceso, que es lo que dispara su `init` -- ahí vive el "arranque con sesión activa",
    // el tercer momento de sincronización del token FCM. Ver TokenSyncManager.
    @Inject lateinit var tokenSyncManager: TokenSyncManager

    @Inject lateinit var legacySessionMigration: LegacySessionMigration

    @Inject lateinit var sessionLockCoordinator: SessionLockCoordinator

    override fun onCreate() {
        super.onCreate()
        ensureAlertsChannelCreated(this)

        // US-12: al primer arranque tras actualizar se borran los tokens que se guardaban cifrados
        // con Tink y su keyset. Idempotente: sin restos, no hace nada.
        legacySessionMigration.run()

        // US-12: volver a primer plano tras demasiado tiempo en segundo plano bloquea la sesión.
        ProcessLifecycleOwner.get().lifecycle.addObserver(SessionLockLifecycleObserver(sessionLockCoordinator))
    }
}
