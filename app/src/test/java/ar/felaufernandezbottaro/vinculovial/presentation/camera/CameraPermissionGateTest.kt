package ar.felaufernandezbottaro.vinculovial.presentation.camera

import android.Manifest
import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import ar.felaufernandezbottaro.vinculovial.presentation.theme.VinculoVialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * La puerta del permiso de cámara: con permiso muestra el contenido, sin permiso pide uno y, si se
 * rechaza, explica el motivo y deja reintentar, ir a Ajustes o volver.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class CameraPermissionGateTest {

    @get:Rule
    val compose = createComposeRule()

    private var denied = 0

    /** Un registro que contesta de inmediato al pedido de permiso, en vez de abrir el diálogo del sistema. */
    private class AnsweringRegistry(private val grant: () -> Boolean) : ActivityResultRegistry() {
        var launches = 0

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launches++
            dispatchResult(requestCode, grant())
        }
    }

    private fun show(registry: ActivityResultRegistry, rationale: String? = null) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        compose.setContent {
            VinculoVialTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    if (rationale == null) {
                        CameraPermissionGate(onDenied = { denied++ }) { Text("CONTENIDO CON CÁMARA") }
                    } else {
                        CameraPermissionGate(
                            onDenied = { denied++ },
                            deniedRationale = rationale,
                            permanentlyDeniedRationale = rationale,
                        ) { Text("CONTENIDO CON CÁMARA") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `con el permiso ya concedido muestra el contenido y no pregunta`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
        val registry = AnsweringRegistry { true }

        show(registry)

        compose.onNodeWithText("CONTENIDO CON CÁMARA").assertIsDisplayed()
        assertEquals(0, registry.launches)
    }

    @Test
    fun `sin permiso lo pide y al concederlo muestra el contenido`() {
        val registry = AnsweringRegistry { true }

        show(registry)

        assertEquals(1, registry.launches)
        compose.onNodeWithText("CONTENIDO CON CÁMARA").assertIsDisplayed()
    }

    @Test
    fun `si se rechaza explica por que hace falta y no muestra el contenido`() {
        val registry = AnsweringRegistry { false }

        show(registry)

        compose.onNodeWithText("Necesitamos la cámara").assertIsDisplayed()
        compose.onNodeWithText("CONTENIDO CON CÁMARA").assertDoesNotExist()
    }

    @Test
    fun `el motivo del rechazo es el que se le pasa`() {
        show(AnsweringRegistry { false }, rationale = "Motivo propio del flujo")

        compose.onNodeWithText("Motivo propio del flujo").assertIsDisplayed()
    }

    @Test
    fun `volver desde el rechazo avisa`() {
        show(AnsweringRegistry { false })

        compose.onNodeWithText("Volver").performClick()

        assertEquals(1, denied)
    }

    @Test
    fun `un rechazo permanente manda a los ajustes de la app`() {
        // En Robolectric `shouldShowRequestPermissionRationale` es siempre falso: es el caso de
        // "no volver a preguntar", que sólo se arregla desde Ajustes.
        show(AnsweringRegistry { false })

        compose.onNodeWithText("Abrir ajustes").performClick()

        val started: Intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertTrue(started.data.toString().startsWith("package:"))
    }
}
