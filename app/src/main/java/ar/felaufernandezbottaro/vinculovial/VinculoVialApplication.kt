package ar.felaufernandezbottaro.vinculovial

import android.app.Application
import ar.felaufernandezbottaro.vinculovial.data.push.ensureAlertsChannelCreated
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VinculoVialApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ensureAlertsChannelCreated(this)
    }
}
