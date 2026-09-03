package ar.felaufernandezbottaro.vinculovial

import android.app.Application
import ar.felaufernandezbottaro.vinculovial.data.push.TokenSyncManager
import ar.felaufernandezbottaro.vinculovial.data.push.ensureAlertsChannelCreated
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VinculoVialApplication : Application() {

    // Nunca se usa directo: inyectarlo fuerza a Hilt a construirlo apenas arranca el
    // proceso, que es lo que dispara su `init` -- ahí vive el "arranque con sesión activa",
    // el tercer momento de sincronización del token FCM. Ver TokenSyncManager.
    @Inject lateinit var tokenSyncManager: TokenSyncManager

    override fun onCreate() {
        super.onCreate()
        ensureAlertsChannelCreated(this)
    }
}
