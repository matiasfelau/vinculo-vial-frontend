package ar.felaufernandezbottaro.vinculovial.presentation.signup

import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeDocumentScanSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeIdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeLivenessSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeSignUpRepository
import ar.felaufernandezbottaro.vinculovial.domain.signup.RegisterUserUseCase
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpError
import ar.felaufernandezbottaro.vinculovial.domain.signup.SignUpResult
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialProblem
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignUpViewModelTest {

    private companion object {
        const val TOKEN = "token-de-verificacion"
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val verification = FakeIdentityVerificationService()
    private val repository = FakeSignUpRepository()
    private val livenessSession = FakeLivenessSession()
    private val scanSession = FakeDocumentScanSession()

    private fun viewModel() = SignUpViewModel(
        registerUser = RegisterUserUseCase(verification, repository),
        identityVerification = verification,
        livenessSession = livenessSession,
        documentScanSession = scanSession,
    )

    private fun SignUpViewModel.completarFormulario() {
        onDniChange("12345678")
        onEmailChange("usuario@ejemplo.com")
        onPasswordChange("Password123!")
        onPasswordConfirmationChange("Password123!")
        onTermsAcceptedChange(true)
        onEmailVerified("usuario@ejemplo.com", TOKEN)
    }

    /** La cámara real entrega bytes; en los tests alcanza con cualquier contenido. */
    private fun foto() = ByteArray(64) { it.toByte() }

    /** El escaneo en vivo entrega campos ya parseados, no una imagen. */
    private fun documento() =
        DocumentIdentity(number = "12345678", sex = "M", procedureNumber = "00307513148")

    private fun SignUpViewModel.capturarTodo(livenessPassed: Boolean = true) {
        onDocumentScanned(documento())
        onSelfieCaptured(foto(), livenessPassed)
    }

    // --- Verificación del email (US-13) ---

    @Test
    fun `el email verificado se reconoce y el token no queda en el estado`() = runTest {
        val viewModel = viewModel()
        viewModel.onEmailChange("usuario@ejemplo.com")

        viewModel.onEmailVerified("usuario@ejemplo.com", TOKEN)

        assertTrue(viewModel.uiState.value.isEmailVerified)
        assertEquals("usuario@ejemplo.com", viewModel.uiState.value.verifiedEmail)
        assertFalse(viewModel.uiState.value.toString().contains(TOKEN))
    }

    @Test
    fun `cambiar el email descarta la verificacion`() = runTest {
        val viewModel = viewModel()
        viewModel.onEmailChange("usuario@ejemplo.com")
        viewModel.onEmailVerified("usuario@ejemplo.com", TOKEN)

        viewModel.onEmailChange("otro@ejemplo.com")

        assertFalse(viewModel.uiState.value.isEmailVerified)
        assertEquals(null, viewModel.uiState.value.verifiedEmail)
    }

    @Test
    fun `editar otro campo no descarta la verificacion`() = runTest {
        val viewModel = viewModel()
        viewModel.onEmailChange("usuario@ejemplo.com")
        viewModel.onEmailVerified("usuario@ejemplo.com", TOKEN)

        viewModel.onPasswordChange("OtraClave123!")
        viewModel.onEmailChange("  usuario@ejemplo.com  ")

        assertTrue(viewModel.uiState.value.isEmailVerified)
    }

    @Test
    fun `el token verificado viaja en el registro`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertEquals(TOKEN, repository.received.single().emailVerificationToken)
    }

    @Test
    fun `sin verificar el email no se llega al backend y se pide verificar`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.onEmailChange("usuario@ejemplo.com ") // mismo email: no descarta
        viewModel.onEmailChange("nuevo@ejemplo.com") // otro email: descarta la verificación
        viewModel.capturarTodo()

        viewModel.submit()

        assertEquals(
            SignUpStatus.Failed(SignUpError.EmailNotVerified),
            viewModel.uiState.value.status,
        )
        assertTrue(repository.received.isEmpty())
    }

    @Test
    fun `si el backend dice EMAIL_NOT_VERIFIED se descarta la verificacion`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.EmailNotVerified)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertEquals(SignUpStatus.Failed(SignUpError.EmailNotVerified), viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isEmailVerified)
    }

    @Test
    fun `un registro exitoso consume el token`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertEquals(SignUpStatus.Registered, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isEmailVerified)
    }

    @Test
    fun `otro error del backend no descarta la verificacion`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.Network)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertTrue(viewModel.uiState.value.isEmailVerified)
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

    // --- Términos y Condiciones (US-19) ---

    @Test
    fun `la casilla de los terminos arranca sin marcar`() {
        assertFalse(viewModel().uiState.value.form.termsAccepted)
    }

    @Test
    fun `con el formulario completo pero sin la casilla no se puede avanzar`() {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.onTermsAcceptedChange(false)

        assertFalse(viewModel.validateForm())
    }

    @Test
    fun `con la casilla marcada y el formulario completo se avanza`() {
        val viewModel = viewModel()
        viewModel.completarFormulario()

        assertTrue(viewModel.validateForm())
    }

    @Test
    fun `solo la casilla no alcanza si el formulario esta incompleto`() {
        val viewModel = viewModel()
        viewModel.onTermsAcceptedChange(true)

        assertFalse(viewModel.validateForm())
    }

    @Test
    fun `desmarcar la casilla vuelve a bloquear el avance`() {
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.onTermsAcceptedChange(false)

        assertFalse(viewModel.uiState.value.form.termsAccepted)
    }

    @Test
    fun `editar otro campo no desmarca la casilla`() {
        val viewModel = viewModel()
        viewModel.onTermsAcceptedChange(true)

        viewModel.onEmailChange("usuario@ejemplo.com")
        viewModel.onPasswordChange("Password123!")

        assertTrue(viewModel.uiState.value.form.termsAccepted)
    }

    @Test
    fun `si el servidor rechaza la version la casilla queda sin marcar para aceptarlos de nuevo`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.TermsNotAccepted)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertEquals(SignUpStatus.Failed(SignUpError.TermsNotAccepted), viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.form.termsAccepted)
        assertFalse(viewModel.validateForm())
    }

    @Test
    fun `otro error del registro no toca la casilla`() = runTest {
        repository.result = SignUpResult.Failed(SignUpError.Network)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        assertTrue(viewModel.uiState.value.form.termsAccepted)
    }

    @Test
    fun `editar un campo limpia su error`() {
        val viewModel = viewModel()
        viewModel.validateForm()

        viewModel.onEmailChange("usuario@ejemplo.com")

        assertFalse(viewModel.uiState.value.fieldErrors.containsKey(CredentialField.EMAIL))
    }

    @Test
    fun `escanear el documento le entrega los campos leidos al edge`() = runTest {
        val viewModel = viewModel()
        viewModel.completarFormulario()

        viewModel.onDocumentScanned(documento())

        assertEquals(1, verification.documentCaptures)
        assertTrue(viewModel.uiState.value.isCaptured(CaptureTarget.DOCUMENT))
        assertEquals(null, viewModel.uiState.value.capturing)
    }

    @Test
    fun `del documento viajan los campos leidos, no una imagen`() = runTest {
        // El escaneo en vivo nunca produce una foto del documento: lo que cruza esta
        // frontera son tres campos de texto, y ese es el contrato que hay que preservar.
        val viewModel = viewModel()

        viewModel.onDocumentScanned(documento())

        assertEquals(documento(), verification.lastDocument)
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
        viewModel.onDocumentScanned(documento())

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
    fun `un rechazo del edge obliga a capturar de nuevo`() = runTest {
        verification.result = EdgeResult.Rejected(EdgeResult.Reason.LIVENESS_FAILED)
        val viewModel = viewModel()
        viewModel.completarFormulario()
        viewModel.capturarTodo()

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(SignUpError.LivenessFailed, state.error)
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

        viewModel.onDocumentScanned(documento())

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

        viewModel.onDocumentScanned(documento())

        assertEquals(null, viewModel.uiState.value.error)
    }
}
