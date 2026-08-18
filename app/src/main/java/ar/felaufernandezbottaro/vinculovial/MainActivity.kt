package ar.felaufernandezbottaro.vinculovial

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ar.felaufernandezbottaro.vinculovial.presentation.navigation.VinculoVialNavHost
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Zero Retention también contra la propia plataforma: bloquea capturas de
        // pantalla, grabación y la miniatura de "apps recientes". Por acá pasan el DNI,
        // la contraseña y la vista previa de la cámara.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            VinculoVialTheme {
                VinculoVialNavHost()
            }
        }
    }
}
