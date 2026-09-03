package ar.felaufernandezbottaro.vinculovial.presentation.push

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionPolicyTest {

    private companion object {
        const val ANDROID_12 = 32
        const val ANDROID_13 = 33
    }

    @Test
    fun `antes de Android 13 no hace falta pedir nada, este o no concedido`() {
        assertEquals(
            NotificationPermissionStep.NOT_NEEDED,
            decideNotificationPermissionStep(sdkInt = ANDROID_12, alreadyGranted = false),
        )
        assertEquals(
            NotificationPermissionStep.NOT_NEEDED,
            decideNotificationPermissionStep(sdkInt = ANDROID_12, alreadyGranted = true),
        )
    }

    @Test
    fun `en Android 13+ ya concedido no vuelve a mostrar la explicacion`() {
        assertEquals(
            NotificationPermissionStep.NOT_NEEDED,
            decideNotificationPermissionStep(sdkInt = ANDROID_13, alreadyGranted = true),
        )
    }

    @Test
    fun `en Android 13+ sin conceder corresponde mostrar el porque antes de pedirlo`() {
        assertEquals(
            NotificationPermissionStep.SHOW_RATIONALE,
            decideNotificationPermissionStep(sdkInt = ANDROID_13, alreadyGranted = false),
        )
    }
}
