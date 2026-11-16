package ar.felaufernandezbottaro.vinculovial.presentation.signup

import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeConfirmResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.CodeRequestResult
import ar.felaufernandezbottaro.vinculovial.domain.auth.RequestFailure
import ar.felaufernandezbottaro.vinculovial.domain.emailverification.EmailVerificationRepository
import ar.felaufernandezbottaro.vinculovial.domain.recovery.FakeEmailVerificationRepository
import ar.felaufernandezbottaro.vinculovial.presentation.common.CodeFlowError
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class VerifyEmailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeEmailVerificationRepository()
    private val email = "usuario@ejemplo.com"

    private fun viewModel(
        repository: EmailVerificationRepository = this.repository,
        resendSeconds: Int = 3,
    ) = VerifyEmailViewModel(repository, resendSeconds)

    private fun VerifyEmailViewModel.conCodigo(code: String = "123456") = apply {
        start(email)
        onCodeChange(code)
    }

    // --- Pedir el código --------------------------------------------------------------------

    @Test
    fun `al empezar pide el codigo al email del formulario y arranca la cuenta regresiva`() = runTest {
        val viewModel = viewModel()

        viewModel.start(email)

        assertEquals(listOf(email), repository.requestedEmails)
        with(viewModel.uiState.value) {
            assertEquals(email, this.email)
            assertTrue(codeRequested)
            assertFalse(requesting)
            assertEquals(3, resendSeconds)
        }
    }

    @Test
    fun `volver a componer con el mismo email no pide otro codigo`() = runTest {
        val viewModel = viewModel()

        viewModel.start(email)
        viewModel.start(email)

        assertEquals(1, repository.requestedEmails.size)
    }

    @Test
    fun `con otro email se pide otro codigo y se reinicia el estado`() = runTest {
        val viewModel = viewModel()
        viewModel.conCodigo()

        viewModel.start("otro@ejemplo.com")

        assertEquals(listOf(email, "otro@ejemplo.com"), repository.requestedEmails)
        assertEquals("", viewModel.uiState.value.code)
        assertEquals("otro@ejemplo.com", viewModel.uiState.value.email)
    }

    @Test
    fun `si el pedido falla no queda codigo pedido y se puede reintentar enseguida`() = runTest {
        repository.requestResult = CodeRequestResult.Failed(RequestFailure.NETWORK)
        val viewModel = viewModel()

        viewModel.start(email)

        with(viewModel.uiState.value) {
            assertFalse(codeRequested)
            assertEquals(CodeFlowError.Failure(RequestFailure.NETWORK), error)
            assertTrue(canResend)
        }
    }

    @Test
    fun `un email rechazado por formato muestra entrada invalida`() = runTest {
        repository.requestResult = CodeRequestResult.InvalidInput
        val viewModel = viewModel()

        viewModel.start(email)

        assertEquals(CodeFlowError.InvalidInput, viewModel.uiState.value.error)
    }

    // --- El campo del código ----------------------------------------------------------------

    @Test
    fun `el codigo solo acepta digitos y hasta 6`() = runTest {
        val viewModel = viewModel()

        viewModel.onCodeChange("12a3-45 6789")

        assertEquals("123456", viewModel.uiState.value.code)
    }

    @Test
    fun `no se puede confirmar sin el codigo completo ni antes de pedirlo`() = runTest {
        val viewModel = viewModel()
        viewModel.onCodeChange("123456")
        assertFalse("sin pedir el código", viewModel.uiState.value.canConfirm)

        viewModel.start(email)
        viewModel.onCodeChange("12345")
        assertFalse("con 5 dígitos", viewModel.uiState.value.canConfirm)

        viewModel.onCodeChange("123456")
        assertTrue(viewModel.uiState.value.canConfirm)
    }

    @Test
    fun `confirmar sin poder confirmar no llama al backend`() = runTest {
        val viewModel = viewModel()
        viewModel.start(email)

        viewModel.confirm()

        assertTrue(repository.confirmations.isEmpty())
    }

    // --- Confirmar --------------------------------------------------------------------------

    @Test
    fun `un codigo correcto entrega el token por evento y limpia el campo`() = runTest {
        val viewModel = viewModel().conCodigo()
        val events = mutableListOf<VerifyEmailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.confirm()

        assertEquals(listOf(email to "123456"), repository.confirmations)
        assertEquals(
            listOf<VerifyEmailEvent>(VerifyEmailEvent.Verified(email, "token-de-verificacion")),
            events,
        )
        assertEquals("", viewModel.uiState.value.code)
        assertFalse(viewModel.uiState.value.confirming)
    }

    @Test
    fun `el token nunca queda en el estado de la pantalla`() = runTest {
        val viewModel = viewModel().conCodigo()

        viewModel.confirm()

        assertFalse(viewModel.uiState.value.toString().contains("token-de-verificacion"))
        assertEquals("Verified(***)", VerifyEmailEvent.Verified(email, "token-de-verificacion").toString())
    }

    @Test
    fun `un codigo invalido muestra el error y deja corregir`() = runTest {
        repository.confirmResult = CodeConfirmResult.InvalidCode
        val viewModel = viewModel().conCodigo()

        viewModel.confirm()

        assertEquals(CodeFlowError.InvalidCode, viewModel.uiState.value.error)
        assertEquals("123456", viewModel.uiState.value.code)
        assertFalse(viewModel.uiState.value.confirming)
    }

    @Test
    fun `editar el codigo limpia el error`() = runTest {
        repository.confirmResult = CodeConfirmResult.InvalidCode
        val viewModel = viewModel().conCodigo()
        viewModel.confirm()

        viewModel.onCodeChange("12345")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `cada resultado de confirmar distinto de correcto tiene su error y no emite evento`() = runTest {
        val esperados = mapOf(
            CodeConfirmResult.InvalidInput to CodeFlowError.InvalidInput,
            CodeConfirmResult.Failed(RequestFailure.RATE_LIMITED) to
                CodeFlowError.Failure(RequestFailure.RATE_LIMITED),
        )

        esperados.forEach { (result, error) ->
            repository.confirmResult = result
            val viewModel = viewModel().conCodigo()
            val events = mutableListOf<VerifyEmailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

            viewModel.confirm()

            assertEquals(error, viewModel.uiState.value.error)
            assertTrue("$result emitió un evento", events.isEmpty())
        }
    }

    @Test
    fun `un doble toque en verificar manda un solo pedido`() = runTest {
        val gate = CompletableDeferred<CodeConfirmResult>()
        var calls = 0
        val gated = object : EmailVerificationRepository {
            override suspend fun requestCode(email: String) = CodeRequestResult.Sent

            override suspend fun confirmCode(email: String, code: String): CodeConfirmResult {
                calls++
                return gate.await()
            }
        }
        val viewModel = viewModel(repository = gated).conCodigo()

        viewModel.confirm()
        viewModel.confirm()
        gate.complete(CodeConfirmResult.InvalidCode)

        assertEquals(1, calls)
    }

    // --- Reenviar ---------------------------------------------------------------------------

    @Test
    fun `reenviar esta bloqueado durante la cuenta regresiva`() = runTest {
        val viewModel = viewModel()
        viewModel.start(email)
        assertFalse(viewModel.uiState.value.canResend)

        viewModel.resend()

        assertEquals("no debió pedir otro código", 1, repository.requestedEmails.size)
    }

    @Test
    fun `al terminar la cuenta se puede reenviar y arranca otra`() = runTest {
        val viewModel = viewModel(resendSeconds = 2)
        viewModel.start(email)
        viewModel.onCodeChange("123")

        advanceTimeBy(2_001)
        assertEquals(0, viewModel.uiState.value.resendSeconds)
        assertTrue(viewModel.uiState.value.canResend)

        viewModel.resend()

        assertEquals(2, repository.requestedEmails.size)
        assertEquals("el campo se limpia al reenviar", "", viewModel.uiState.value.code)
        assertEquals(2, viewModel.uiState.value.resendSeconds)
    }

    @Test
    fun `la cuenta regresiva baja de a un segundo`() = runTest {
        val viewModel = viewModel(resendSeconds = 3)
        viewModel.start(email)

        advanceTimeBy(1_001)

        assertEquals(2, viewModel.uiState.value.resendSeconds)
    }
}
