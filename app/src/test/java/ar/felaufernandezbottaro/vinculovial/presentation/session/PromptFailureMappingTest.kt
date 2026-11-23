package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cómo se traduce cada error de `BiometricPrompt` a lo que hace el desbloqueo. */
class PromptFailureMappingTest {

    @Test
    fun `cancelar por el usuario, el boton negativo o el sistema es CANCELLED`() {
        assertEquals(PromptFailure.CANCELLED, BiometricPrompt.ERROR_USER_CANCELED.toPromptFailure())
        assertEquals(PromptFailure.CANCELLED, BiometricPrompt.ERROR_NEGATIVE_BUTTON.toPromptFailure())
        assertEquals(PromptFailure.CANCELLED, BiometricPrompt.ERROR_CANCELED.toPromptFailure())
    }

    @Test
    fun `los bloqueos por intentos fallidos son LOCKED_OUT`() {
        assertEquals(PromptFailure.LOCKED_OUT, BiometricPrompt.ERROR_LOCKOUT.toPromptFailure())
        assertEquals(PromptFailure.LOCKED_OUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT.toPromptFailure())
    }

    @Test
    fun `cualquier otro error es OTHER`() {
        assertEquals(PromptFailure.OTHER, BiometricPrompt.ERROR_HW_UNAVAILABLE.toPromptFailure())
        assertEquals(PromptFailure.OTHER, 9999.toPromptFailure())
    }
}
