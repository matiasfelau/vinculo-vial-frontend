package com.vinculovial.app.presentation.signup

import com.vinculovial.app.domain.signup.FakeDocumentPresenceSession
import com.vinculovial.app.domain.signup.FakeIdentityVerificationService
import com.vinculovial.app.domain.signup.FakeLivenessSession
import com.vinculovial.app.domain.signup.FakeSignUpRepository
import com.vinculovial.app.domain.signup.RegisterUserUseCase
import com.vinculovial.app.domain.signup.SignUpError
import com.vinculovial.app.domain.signup.SignUpResult
import com.vinculovial.app.domain.signup.model.CredentialField
import com.vinculovial.app.domain.signup.model.CredentialProblem
import com.vinculovial.app.domain.signup.model.DniFrontQuality
import com.vinculovial.app.domain.signup.model.EdgeResult
import com.vinculovial.app.util.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignUpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val verification = FakeIdentityVerificationService()
    private val repository = FakeSignUpRepository()
    private val livenessSession = FakeLivenessSession()
    private val presenceSession = FakeDocumentPresenceSession()

    private fun viewModel() = SignUpViewModel(
        registerUser = RegisterUserUseCase(verification, repository),
        identityVerification = verification,
        livenessSession = livenessSession,
        documentPresenceSession = presenceSession,
    )

    private fun SignUpViewModel.completarFormulario() {
        onDniChange("12345678")
        onEmailChange("usuario@ejemplo.com")
        onPasswordChange("Password123!")
        onPasswordConfirmationChange("Password123!")
    }

    /** La cámara real entrega bytes; en los tests alcanza con cualquier contenido. */
    private fun foto() = ByteArray(64) { it.toByte() }

    private fun SignUpViewModel.capturarTodo(livenessPassed: Boolean = true) {
        onDocumentScanned(foto())
        onSelfieCaptured(foto(), livenessPassed)
    }

    @Test
    fun `el dni solo acepta digitos y hasta 8`() {
        val viewModel = viewModel()

        viewModel.onDniChange("12a34-5678999")

        assertEquals("12345678", viewModel.uiState.value.form.dni)
    }

    @Test
    fun `validar un formulario incompleto marca los campos`() {
        val viewModel = viewModel()

        val valido = viewModel.validateForm()

        assertFalse(valido)
        assertEquals(
            CredentialProblem.REQUIRED,
            viewModel.uiState.value.fieldErrors[CredentialField.EMAIL],
        )
    }

    @Test
    fun `editar un campo limpia su error`() {
        val viewModel = viewModel()
        viewModel.validateForm()

        viewModel.onEmailChange("usuario@ejemplo.com")

        assertFalse(viewModel.uiState.value.fieldErrors.containsKey(CredentialField.EMAIL))
    }

    @Test
    fun `escanear el documento le entrega el frente al edge`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.onDocumentScanned(foto())

        assertEquals(1, verification.dniFrontCaptures)
        assertTrue(viewModel.uiState.value.isCaptured(CaptureTarget.DOCUMENT))
        assertEquals(null, viewModel.uiState.value.capturing)
    }

    @Test
    fun `canReadDniFront delega en el edge sin tocar el estado`() = runTest {
        verification.canReadDniFrontResult = DniFrontQuality.BARCODE_UNREADABLE
        val viewModel = viewModel()

        val quality = viewModel.canReadDniFront(foto())

        assertFalse(quality.isReadable)
        assertEquals(1, verification.canReadDniFrontCalls)
        // Es sólo una consulta: no debe acumularse como si fuera una captura aceptada.
        assertTrue(viewModel.uiState.value.completedCaptures.isEmpty())
    }

    @Test
    fun `canReadDniFront propaga el motivo para que la pantalla no invente el mensaje`() = runTest {
        verification.canReadDniFrontResult = DniFrontQuality.PHOTO_NOT_FOUND
        val viewModel = viewModel()

        // Se lee el código pero no se ve la cara: se corrige distinto que un código ilegible,
        // así que el motivo tiene que llegar intacto a la UI y no colapsar en un booleano.
        assertEquals(DniFrontQuality.PHOTO_NOT_FOUND, viewModel.canReadDniFront(foto()))
    }

    @Test
    fun `el veredicto de vida viaja junto a la selfie`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.onSelfieCaptured(foto(), livenessPassed = false)

        assertEquals(false, verification.lastLivenessPassed)
    }

    @Test
    fun `no se puede enviar hasta tener las dos capturas`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.onDocumentScanned(foto())

        viewModel.submit()

        assertTrue(repository.received.isEmpty())
        assertEquals(SignUpStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `con documento y selfie se habilita el envio`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.capturarTodo()

        assertTrue(viewModel.uiState.value.canSubmitCapture)
    }

    @Test
    fun `registro exitoso deja el estado en Registered y sin capturas`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(SignUpStatus.Registered, state.status)
        assertTrue(state.completedCaptures.isEmpty())
    }

    @Test
    fun `un rechazo del backend queda como error de dominio`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.AlreadyRegistered)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertEquals(SignUpError.AlreadyRegistered, viewModel.uiState.value.error)
    }

    @Test
    fun `un match facial negativo obliga a capturar de nuevo`() = runTest {
        verification.result = EdgeResult.Rejected(EdgeResult.Reason.FACE_MISMATCH)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(SignUpError.FaceMismatch, state.error)
        assertTrue("las capturas ya no existen en el Edge", state.completedCaptures.isEmpty())
    }

    @Test
    fun `descartar el error deja la pantalla lista para reintentar`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.Network)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()
        viewModel.submit()

        viewModel.onErrorDismissed()

        assertEquals(SignUpStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `volver atras descarta las imagenes pendientes`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.onCaptureRestarted()

        assertEquals(1, verification.discards)
        assertTrue(viewModel.uiState.value.completedCaptures.isEmpty())
    }

    @Test
    fun `si el edge rechaza el documento se informa sin dejar la pantalla trabada`() = runTest {
        verification.captureFailure = IllegalStateException("no se pudo leer el código")
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.onDocumentScanned(foto())

        val state = viewModel.uiState.value
        assertEquals(SignUpError.DocumentUnreadable, state.error)
        assertTrue(state.completedCaptures.isEmpty())
        assertEquals(null, state.capturing)
    }

    @Test
    fun `cancelar la captura no se muestra como error al usuario`() = runTest {
        // CancellationException hereda de IllegalStateException: si se la trata como
        // un fallo cualquiera, salir de la pantalla dispara un diálogo de error.
        verification.captureFailure = CancellationException("pantalla abandonada")
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.onDocumentScanned(foto())

        assertEquals(null, viewModel.uiState.value.error)
    }
}
