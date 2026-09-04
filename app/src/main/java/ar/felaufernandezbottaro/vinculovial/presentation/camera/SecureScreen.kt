package ar.felaufernandezbottaro.vinculovial.presentation.camera

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Activa `FLAG_SECURE` mientras esta pantalla está compuesta, y lo retira al salir.
 *
 * Cámara y biometría son las únicas vistas por las que pasan el DNI, la selfie o el preview
 * en vivo (Zero Retention, ver CLAUDE.md); son las únicas que se benefician de bloquear
 * capturas de pantalla y la miniatura de "apps recientes". Aplicar el flag a nivel de
 * `Activity` completa bloqueaba capturas en toda la app -- el buzón incluido, donde no hay
 * nada que proteger.
 */
@Composable
fun SecureScreen() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
