package ar.felaufernandezbottaro.vinculovial

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ar.felaufernandezbottaro.vinculovial.data.push.EXTRA_ALERT_ID
import ar.felaufernandezbottaro.vinculovial.data.push.EXTRA_ALERT_TYPE
import ar.felaufernandezbottaro.vinculovial.data.push.EXTRA_PUSH_TYPE
import ar.felaufernandezbottaro.vinculovial.presentation.navigation.VinculoVialNavHost
import ar.felaufernandezbottaro.vinculovial.presentation.push.PushTarget
import ar.felaufernandezbottaro.vinculovial.presentation.push.parsePushTarget
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingPush by mutableStateOf<PushTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingPush = intent?.toPushTarget()

        setContent {
            VinculoVialTheme {
                VinculoVialNavHost(
                    pendingPush = pendingPush,
                    onPendingPushConsumed = { pendingPush = null },
                )
            }
        }
    }

    /**
     * Tocar la notificación con la app ya abierta reusa esta misma instancia (ver
     * `launchMode="singleTop"` en el manifiesto) en vez de apilar una `MainActivity` nueva
     * sobre la anterior -- sin esto, `onCreate` no vuelve a correr y la navegación al destino
     * nuevo se pierde.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPush = intent.toPushTarget()
    }

    private fun Intent.toPushTarget(): PushTarget? = parsePushTarget(
        type = getStringExtra(EXTRA_PUSH_TYPE),
        alertType = getStringExtra(EXTRA_ALERT_TYPE),
        alertId = getStringExtra(EXTRA_ALERT_ID),
    )
}
