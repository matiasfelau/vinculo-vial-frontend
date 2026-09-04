package ar.felaufernandezbottaro.vinculovial.presentation.push

import android.os.Build

enum class NotificationPermissionStep {
    /** Nada que pedir: versiones previas a Android 13 no requieren el permiso en runtime, o ya está concedido. */
    NOT_NEEDED,

    /** Corresponde mostrar el porqué antes de disparar el diálogo del sistema. */
    SHOW_RATIONALE,
}

/**
 * Decisión pura, sin tocar `Context` ni lanzar nada: separada de `NotificationPermissionRequester`
 * para poder probarla en JVM sin Robolectric, igual que el resto de la lógica de este proyecto.
 */
fun decideNotificationPermissionStep(sdkInt: Int, alreadyGranted: Boolean): NotificationPermissionStep = when {
    sdkInt < Build.VERSION_CODES.TIRAMISU -> NotificationPermissionStep.NOT_NEEDED
    alreadyGranted -> NotificationPermissionStep.NOT_NEEDED
    else -> NotificationPermissionStep.SHOW_RATIONALE
}
