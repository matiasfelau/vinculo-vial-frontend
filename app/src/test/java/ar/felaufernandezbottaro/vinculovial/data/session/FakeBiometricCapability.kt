package ar.felaufernandezbottaro.vinculovial.data.session

/** [BiometricCapability] para pruebas: el dispositivo tiene o no biometría fuerte enrolada. */
class FakeBiometricCapability(var strongEnrolled: Boolean = true) : BiometricCapability {
    override fun isStrongBiometricEnrolled(): Boolean = strongEnrolled
}
