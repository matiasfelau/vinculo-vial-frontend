package ar.felaufernandezbottaro.vinculovial

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ar.felaufernandezbottaro.vinculovial.data.push.EXTRA_ALERT_TYPE
import ar.felaufernandezbottaro.vinculovial.presentation.navigation.VinculoVialNavHost
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingAlertType by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingAlertType = intent?.getStringExtra(EXTRA_ALERT_TYPE)

        setContent {
            VinculoVialTheme {
                VinculoVialNavHost(
                    pendingAlertType = pendingAlertType,
                    onPendingAlertTypeConsumed = { pendingAlertType = null },
                )
            }
        }
    }

    /**
     * Tocar la notificación con la app ya abierta reusa esta misma instancia (ver
     * `launchMode="singleTop"` en el manifiesto) en vez de apilar una `MainActivity` nueva
     * sobre la anterior -- sin esto, `onCreate` no vuelve a correr y la rotación al alert_type
     * nuevo se pierde.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAlertType = intent.getStringExtra(EXTRA_ALERT_TYPE)
    }
}
