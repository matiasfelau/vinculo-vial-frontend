package ar.felaufernandezbottaro.vinculovial.presentation.push

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El pedido del permiso de notificaciones tras el login: nunca bloquea (siempre termina avisando)
 * y sólo explica el motivo cuando el sistema todavía lo exige.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPermissionRequesterTest {

    @get:Rule
    val compose = createComposeRule()

    private var finished = 0

    private fun show() {
        compose.setContent { VinculoVialTheme { NotificationPermissionRequester(onFinished = { finished++ }) } }
        compose.waitForIdle()
    }

    @Test
    @Config(sdk = [32])
    fun `antes de Android 13 no hay nada que pedir y termina enseguida`() {
        show()

        assertEquals(1, finished)
        compose.onNodeWithText("Activá las notificaciones").assertDoesNotExist()
    }

    @Test
    fun `con el permiso ya concedido termina sin preguntar`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        show()

        assertEquals(1, finished)
        compose.onNodeWithText("Activá las notificaciones").assertDoesNotExist()
    }

    @Test
    fun `sin el permiso explica el motivo antes de pedirlo`() {
        show()

        compose.onNodeWithText("Activá las notificaciones").assertIsDisplayed()
        assertEquals("todavía no terminó", 0, finished)
    }

    @Test
    fun `ahora no termina sin pedir el permiso`() {
        show()

        compose.onNodeWithText("Ahora no").performClick()

        assertEquals(1, finished)
        compose.onNodeWithText("Activá las notificaciones").assertDoesNotExist()
    }

    @Test
    fun `activar cierra la explicacion y lanza el pedido del sistema`() {
        show()

        compose.onNodeWithText("Activar").performClick()

        compose.onNodeWithText("Activá las notificaciones").assertDoesNotExist()
    }
}
