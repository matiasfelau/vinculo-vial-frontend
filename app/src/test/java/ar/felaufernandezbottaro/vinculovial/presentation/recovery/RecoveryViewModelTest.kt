package ar.felaufernandezbottaro.vinculovial.presentation.recovery

import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.recovery.CompletePasswordResetUseCase
import ar.felaufernandezbottaro.vinculovial.domain.recovery.FakePasswordResetRepository
import ar.felaufernandezbottaro.vinculovial.domain.recovery.FakeRecoverySessionCleaner
import ar.felaufernandezbottaro.vinculovial.domain.recovery.PasswordResetResult
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNotice
import ar.felaufernandezbottaro.vinculovial.domain.session.LoginNoticeStore
import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeDocumentScanSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeIdentityVerificationService
import ar.felaufernandezbottaro.vinculovial.domain.signup.FakeLivenessSession
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialField
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.CredentialProblem
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.DocumentIdentity
import ar.felaufernandezbottaro.vinculovial.domain.signup.model.EdgeResult
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePasswordResetRepository()
    private val verification = FakeIdentityVerificationService()
    private val sessionCleaner = FakeRecoverySessionCleaner()
    private val loginNotice = LoginNoticeStore()

    private val dni = "12345678"
    private val password = "Nueva-Clave-2026!"

    private fun viewModel(resendSeconds: Int = 3) = RecoveryViewModel(
        repository = repository,
        completePasswordReset = CompletePasswordResetUseCase(verification, repository),
        identityVerification = verification,
        sessionCleaner = sessionCleaner,
        loginNotice = loginNotice,
        resendSeconds = resendSeconds,
        livenessSession = FakeLivenessSession(),
        documentScanSession = FakeDocumentScanSession(),
    )

    private fun documento(number: String = dni) =
        DocumentIdentity(number = number, sex = "M", procedureNumber = "00307513148")

    private fun foto() = ByteArray(64) { it.toByte() }

    private fun TestScope.eventsOf(viewModel: RecoveryViewModel): MutableList<RecoveryEvent> {
        val events = mutableListOf<RecoveryEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events += it }
        }
        return events
    }

    /** Lleva el flujo hasta tener el `reset_token` y la contraseña nueva cargada. */
    private fun RecoveryViewModel.hastaLaContrasena() {
        onDniChange(dni)
        requestCode()
        onCodeChange("654321")
        confirmCode()
        onPasswordChange(password)
        onPasswordConfirmationChange(password)
    }

    private fun RecoveryViewModel.capturarTodo(livenessPassed: Boolean = true) {
        onDocumentScanned(documento())
        onSelfieCaptured(foto(), livenessPassed)
    }

    // --- Paso 1: DNI -------------------------------------------------------------------------

    @Test
    fun `al crearse descarta cualquier captura que haya quedado de otro flujo`() {
        viewModel()

        assertEquals(1, verification.discards)
    }

    @Test
    fun `el dni solo acepta digitos y hasta 8`() {
        val viewModel = viewModel()

        viewModel.onDniChange("12a34-5678999")

        assertEquals("12345678", viewModel.uiState.value.dni)
    }

    @Test
    fun `un dni mal formado marca el problema y no llama al backend`() {
        val viewModel = viewModel()
        viewModel.onDniChange("123")

        viewModel.requestCode()

        assertEquals(CredentialProblem.DNI_FORMAT, viewModel.uiState.value.dniProblem)
        assertTrue(repository.requestedDnis.isEmpty())
    }

    @Test
    fun `editar el dni limpia sus errores`() {
        val viewModel = viewModel()
        viewModel.onDniChange("123")
        viewModel.requestCode()

        viewModel.onDniChange("1234")

        assertNull(viewModel.uiState.value.dniProblem)
    }

    @Test
    fun `pedir el codigo avisa a la navegacion y arranca la cuenta regresiva`() = runTest {
        val viewModel = viewModel()
        val events = eventsOf(viewModel)
        viewModel.onDniChange(dni)

        viewModel.requestCode()

        assertEquals(listOf(dni), repository.requestedDnis)
        assertEquals(listOf<RecoveryEvent>(RecoveryEvent.CodeRequested), events)
        with(viewModel.uiState.value) {
            assertTrue(codeRequested)
            assertFalse(requesting)
            assertEquals(3, resendSeconds)
        }
    }

    @Test
    fun `si el pedido del codigo falla se queda en el paso del dni con el error`() = runTest {
        val viewModel = viewModel()
        val events = eventsOf(viewModel)
        viewModel.onDniChange(dni)

        repository.requestResult = CodeRequestResult.Failed(RequestFailure.NETWORK)
        viewModel.requestCode()
        assertEquals(CodeFlowError.Failure(RequestFailure.NETWORK), viewModel.uiState.value.requestError)

        repository.requestResult = CodeRequestResult.InvalidInput
        viewModel.requestCode()
        assertEquals(CodeFlowError.InvalidInput, viewModel.uiState.value.requestError)

        assertTrue("no debió navegar", events.isEmpty())
        assertFalse(viewModel.uiState.value.codeRequested)
    }

    // --- Paso 2: código ----------------------------------------------------------------------

    @Test
    fun `el codigo solo acepta digitos y hasta 6`() {
        val viewModel = viewModel()

        viewModel.onCodeChange("12a3-45 6789")

        assertEquals("123456", viewModel.uiState.value.code)
    }

    @Test
    fun `no se confirma sin el codigo completo ni antes de pedirlo`() {
        val viewModel = viewModel()
        viewModel.onCodeChange("654321")
        viewModel.confirmCode()
        assertTrue("sin pedir el código", repository.confirmations.isEmpty())

        viewModel.onDniChange(dni)
        viewModel.requestCode()
        viewModel.onCodeChange("65432")
        viewModel.confirmCode()
        assertTrue("con 5 dígitos", repository.confirmations.isEmpty())
    }

    @Test
    fun `un codigo correcto avisa a la navegacion y limpia el campo`() = runTest {
        val viewModel = viewModel()
        val events = eventsOf(viewModel)
        viewModel.onDniChange(dni)
        viewModel.requestCode()
        viewModel.onCodeChange("654321")

        viewModel.confirmCode()

        assertEquals(listOf(dni to "654321"), repository.confirmations)
        assertEquals(
            listOf<RecoveryEvent>(RecoveryEvent.CodeRequested, RecoveryEvent.CodeConfirmed),
            events,
        )
        assertEquals("", viewModel.uiState.value.code)
        assertFalse("el reset_token no se expone en el estado", viewModel.uiState.value.toString().contains("reset-token"))
    }

    @Test
    fun `cada resultado de confirmar distinto de correcto tiene su error y no navega`() = runTest {
        val esperados = mapOf(
            CodeConfirmResult.InvalidCode to CodeFlowError.InvalidCode,
            CodeConfirmResult.InvalidInput to CodeFlowError.InvalidInput,
            CodeConfirmResult.Failed(RequestFailure.UNAVAILABLE) to
                CodeFlowError.Failure(RequestFailure.UNAVAILABLE),
        )

        esperados.forEach { (result, error) ->
            repository.confirmResult = result
            val viewModel = viewModel()
            val events = eventsOf(viewModel)
            viewModel.onDniChange(dni)
            viewModel.requestCode()
            events.clear()
            viewModel.onCodeChange("654321")

            viewModel.confirmCode()

            assertEquals(error, viewModel.uiState.value.codeError)
            assertTrue("$result navegó", events.isEmpty())
        }
    }

    @Test
    fun `editar el codigo limpia el error`() {
        repository.confirmResult = CodeConfirmResult.InvalidCode
        val viewModel = viewModel()
        viewModel.onDniChange(dni)
        viewModel.requestCode()
        viewModel.onCodeChange("654321")
        viewModel.confirmCode()

        viewModel.onCodeChange("65432")

        assertNull(viewModel.uiState.value.codeError)
    }

    @Test
    fun `reenviar esta bloqueado durante la cuenta regresiva y despues pide otro codigo`() = runTest {
        val viewModel = viewModel(resendSeconds = 2)
        viewModel.onDniChange(dni)
        viewModel.requestCode()

        viewModel.resend()
        assertEquals("durante la cuenta no debió pedir otro", 1, repository.requestedDnis.size)

        advanceTimeBy(2_001)
        viewModel.onCodeChange("123")
        viewModel.resend()

        assertEquals(2, repository.requestedDnis.size)
        assertEquals("", viewModel.uiState.value.code)
        assertEquals(2, viewModel.uiState.value.resendSeconds)
    }

    @Test
    fun `si reenviar falla el error se muestra en el paso del codigo`() = runTest {
        val viewModel = viewModel(resendSeconds = 1)
        viewModel.onDniChange(dni)
        viewModel.requestCode()
        advanceTimeBy(1_001)

        repository.requestResult = CodeRequestResult.Failed(RequestFailure.RATE_LIMITED)
        viewModel.resend()

        assertEquals(CodeFlowError.Failure(RequestFailure.RATE_LIMITED), viewModel.uiState.value.codeError)
        assertNull(viewModel.uiState.value.requestError)
    }

    // --- Paso 3: contraseña nueva -------------------------------------------------------------

    @Test
    fun `una contrasena debil o que no coincide marca sus problemas`() {
        val viewModel = viewModel()
        viewModel.onPasswordChange("corta")
        viewModel.onPasswordConfirmationChange("otra")

        val valida = viewModel.validatePassword()

        assertFalse(valida)
        assertEquals(CredentialProblem.PASSWORD_TOO_SHORT, viewModel.uiState.value.passwordProblems[CredentialField.PASSWORD])
        assertEquals(
            CredentialProblem.PASSWORD_MISMATCH,
            viewModel.uiState.value.passwordProblems[CredentialField.PASSWORD_CONFIRMATION],
        )
    }

    @Test
    fun `una contrasena valida habilita la captura`() {
        val viewModel = viewModel()
        viewModel.onPasswordChange(password)
        viewModel.onPasswordConfirmationChange(password)

        assertTrue(viewModel.validatePassword())
        assertTrue(viewModel.uiState.value.passwordProblems.isEmpty())
    }

    @Test
    fun `editar un campo de la contrasena limpia solo su problema`() {
        val viewModel = viewModel()
        viewModel.validatePassword()

        viewModel.onPasswordChange(password)

        assertFalse(viewModel.uiState.value.passwordProblems.containsKey(CredentialField.PASSWORD))
        assertTrue(viewModel.uiState.value.passwordProblems.containsKey(CredentialField.PASSWORD_CONFIRMATION))

        viewModel.onPasswordConfirmationChange(password)
        assertTrue(viewModel.uiState.value.passwordProblems.isEmpty())
    }

    // --- Paso 4: captura ---------------------------------------------------------------------

    @Test
    fun `un dni escaneado distinto al ingresado se avisa sin guardar nada ni llamar al backend`() {
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()

        val sigue = viewModel.onDocumentScanned(documento(number = "87654321"))

        assertFalse(sigue)
        assertEquals(RecoveryStatus.Failed(RecoveryError.DniMismatch), viewModel.uiState.value.status)
        assertEquals("no debió guardarse el documento", 0, verification.documentCaptures)
        assertTrue(repository.completions.isEmpty())
    }

    @Test
    fun `un dni escaneado que coincide se acepta, aunque venga con ceros a la izquierda`() {
        val viewModel = viewModel()
        viewModel.onDniChange("1234567")

        val sigue = viewModel.onDocumentScanned(documento(number = "01234567"))

        assertTrue(sigue)
        assertTrue(viewModel.uiState.value.documentCaptured)
        assertEquals(1, verification.documentCaptures)
    }

    @Test
    fun `si no se puede guardar el documento o la selfie es una captura inservible`() {
        verification.captureFailure = IllegalStateException("sin memoria")
        val viewModel = viewModel()
        viewModel.onDniChange(dni)

        viewModel.onDocumentScanned(documento())
        assertEquals(RecoveryStatus.Failed(RecoveryError.CaptureUnusable), viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.documentCaptured)

        viewModel.onErrorDismissed()
        viewModel.onSelfieCaptured(foto(), livenessPassed = true)
        assertEquals(RecoveryStatus.Failed(RecoveryError.CaptureUnusable), viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.selfieCaptured)
    }

    @Test
    fun `la selfie sin documento no dispara el envio`() {
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()

        viewModel.onSelfieCaptured(foto(), livenessPassed = true)

        assertTrue(repository.completions.isEmpty())
        assertEquals(0, verification.verifications)
    }

    @Test
    fun `cancelar la captura descarta lo pendiente`() {
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()
        viewModel.onDocumentScanned(documento())
        val descartesAntes = verification.discards

        viewModel.onCaptureCancelled()

        assertEquals(descartesAntes + 1, verification.discards)
        assertFalse(viewModel.uiState.value.documentCaptured)
        assertEquals(RecoveryStatus.Idle, viewModel.uiState.value.status)
    }

    // --- Envío -------------------------------------------------------------------------------

    @Test
    fun `con las dos capturas el envio arranca solo con el reset_token y los datos del documento`() {
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()

        viewModel.capturarTodo()

        with(repository.completions.single()) {
            assertEquals("reset-token", resetToken)
            assertEquals(dni, this.dni)
            assertEquals("00307513148", procedureNumber)
            assertEquals(password, newPassword)
        }
    }

    @Test
    fun `al terminar bien borra la sesion local, deja el aviso para Login y navega`() = runTest {
        val viewModel = viewModel()
        val events = eventsOf(viewModel)
        viewModel.hastaLaContrasena()

        viewModel.capturarTodo()

        assertEquals(RecoveryStatus.Completed, viewModel.uiState.value.status)
        assertEquals("se borra blob y clave", 1, sessionCleaner.clears)
        assertEquals(LoginNotice.PASSWORD_RESET, loginNotice.notice.value)
        assertEquals(RecoveryEvent.Completed, events.last())
        assertEquals("la contraseña no queda en el estado", "", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.passwordConfirmation)
    }

    @Test
    fun `cada rechazo del backend tiene su error, no borra la sesion y obliga a recapturar`() {
        val esperados = mapOf(
            PasswordResetResult.IdentityNotVerified to RecoveryError.IdentityNotVerified,
            PasswordResetResult.PasswordRejected to RecoveryError.PasswordRejected,
            PasswordResetResult.InvalidData to RecoveryError.InvalidData,
            PasswordResetResult.InvalidResetToken to RecoveryError.InvalidResetToken,
            PasswordResetResult.Failed(RequestFailure.NETWORK) to RecoveryError.Failure(RequestFailure.NETWORK),
        )

        esperados.forEach { (result, error) ->
            repository.completeResult = result
            val viewModel = viewModel()
            viewModel.hastaLaContrasena()

            viewModel.capturarTodo()

            with(viewModel.uiState.value) {
                assertEquals("mapeo incorrecto para $result", RecoveryStatus.Failed(error), status)
                assertFalse("las capturas ya no existen", documentCaptured)
                assertFalse(selfieCaptured)
            }
        }
        assertEquals("ningún fallo borra la sesión", 0, sessionCleaner.clears)
        assertNull(loginNotice.notice.value)
    }

    @Test
    fun `los rechazos del edge tienen su error y no llegan al backend`() {
        val esperados = mapOf(
            EdgeResult.Reason.LIVENESS_FAILED to RecoveryError.LivenessFailed,
            EdgeResult.Reason.INCOMPLETE_CAPTURE to RecoveryError.CaptureUnusable,
        )

        esperados.forEach { (reason, error) ->
            verification.result = EdgeResult.Rejected(reason)
            val viewModel = viewModel()
            viewModel.hastaLaContrasena()

            viewModel.capturarTodo()

            assertEquals(RecoveryStatus.Failed(error), viewModel.uiState.value.status)
        }
        assertTrue(repository.completions.isEmpty())
    }

    @Test
    fun `si el dni del documento no coincide al enviar no llega al backend`() {
        verification.result = EdgeResult.Verified(documento(number = "87654321"), selfieBase64 = "c2VsZmll")
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()

        viewModel.capturarTodo()

        // El control local ya cortó antes de enviar, pero el caso de uso también lo valida.
        assertTrue(repository.completions.isEmpty())
    }

    @Test
    fun `sin reset_token no se envia y las capturas se descartan`() {
        val viewModel = viewModel()
        viewModel.onDniChange(dni)
        viewModel.onPasswordChange(password)
        viewModel.onPasswordConfirmationChange(password)
        val descartesAntes = verification.discards

        viewModel.capturarTodo()

        assertEquals(RecoveryStatus.Failed(RecoveryError.InvalidResetToken), viewModel.uiState.value.status)
        assertTrue(repository.completions.isEmpty())
        assertEquals(descartesAntes + 1, verification.discards)
    }

    @Test
    fun `una contrasena invalida al enviar se rechaza sin tocar el backend y descarta las capturas`() {
        val viewModel = viewModel()
        viewModel.onDniChange(dni)
        viewModel.requestCode()
        viewModel.onCodeChange("654321")
        viewModel.confirmCode()
        viewModel.onPasswordChange("corta")
        val descartesAntes = verification.discards

        viewModel.capturarTodo()

        assertEquals(RecoveryStatus.Failed(RecoveryError.InvalidData), viewModel.uiState.value.status)
        assertTrue(repository.completions.isEmpty())
        assertEquals(descartesAntes + 1, verification.discards)
    }

    @Test
    fun `cerrar el error deja la pantalla lista para reintentar`() {
        repository.completeResult = PasswordResetResult.IdentityNotVerified
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()
        viewModel.capturarTodo()

        viewModel.onErrorDismissed()

        assertEquals(RecoveryStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `cerrar el error sin error no cambia nada`() {
        val viewModel = viewModel()

        viewModel.onErrorDismissed()

        assertEquals(RecoveryStatus.Idle, viewModel.uiState.value.status)
    }

    // --- Token vencido -----------------------------------------------------------------------

    @Test
    fun `volver a pedir el codigo reinicia el flujo y mantiene el dni`() = runTest {
        repository.completeResult = PasswordResetResult.InvalidResetToken
        val viewModel = viewModel()
        val events = eventsOf(viewModel)
        viewModel.hastaLaContrasena()
        viewModel.capturarTodo()
        events.clear()
        val descartesAntes = verification.discards

        viewModel.onRestartRequested()

        assertEquals(listOf<RecoveryEvent>(RecoveryEvent.RestartRequested), events)
        assertEquals(descartesAntes + 1, verification.discards)
        with(viewModel.uiState.value) {
            assertEquals(dni, this.dni)
            assertFalse(codeRequested)
            assertEquals("", code)
            assertEquals(RecoveryStatus.Idle, status)
            assertEquals(0, resendSeconds)
        }
    }

    @Test
    fun `tras volver a pedir el codigo el token viejo ya no se reutiliza`() {
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()
        viewModel.onRestartRequested()
        viewModel.onPasswordChange(password)
        viewModel.onPasswordConfirmationChange(password)

        viewModel.capturarTodo()

        assertEquals(RecoveryStatus.Failed(RecoveryError.InvalidResetToken), viewModel.uiState.value.status)
        assertTrue(repository.completions.isEmpty())
    }

    // --- Ciclo de vida -----------------------------------------------------------------------

    @Test
    fun `si el grafo muere se descartan las capturas y el token`() {
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()
        val descartesAntes = verification.discards

        RecoveryViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
            .invoke(viewModel)

        assertEquals(descartesAntes + 1, verification.discards)
        viewModel.capturarTodo()
        assertEquals(
            "sin token no hay envío",
            RecoveryStatus.Failed(RecoveryError.InvalidResetToken),
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun `el estado nunca se renderiza con datos personales`() {
        val viewModel = viewModel()
        viewModel.hastaLaContrasena()

        val rendered = viewModel.uiState.value.toString()

        assertEquals("RecoveryUiState(***)", rendered)
        assertFalse(rendered.contains(dni))
        assertFalse(rendered.contains(password))
    }
}
