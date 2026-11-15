package ar.felaufernandezbottaro.vinculovial.presentation.settings

data class SettingsUiState(
    val isLoggingOut: Boolean = false,

    /** El interruptor de biometría se muestra: hay biometría fuerte enrolada, o ya está activada. */
    val isBiometricAvailable: Boolean = false,
    val isBiometricEnabled: Boolean = false,

    /** Acaba de entrar con la contraseña y puede activar la biometría: se le ofrece, una vez. */
    val showBiometricOffer: Boolean = false,

    /** El dispositivo no pudo crear la clave: se le avisa y sigue entrando con la contraseña. */
    val biometricActivationFailed: Boolean = false,
)
