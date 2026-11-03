package ar.felaufernandezbottaro.vinculovial.presentation.account

import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountError
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountResult
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountUseCase
import ar.felaufernandezbottaro.vinculovial.domain.account.FakeAccountRepository
import ar.felaufernandezbottaro.vinculovial.domain.account.FakeLocalAccountDataEraser
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeAccountRepository
    private lateinit var eraser: FakeLocalAccountDataEraser

    @Before
    fun setUp() {
        repository = FakeAccountRepository()
        eraser = FakeLocalAccountDataEraser()
    }

    private fun viewModel() = DeleteAccountViewModel(DeleteAccountUseCase(repository, eraser))

    private fun DeleteAccountViewModel.typeAndSubmit(password: String = "mi-clave") {
        onPasswordChange(password)
        submit()
    }

    // --- Estado inicial y edición ---

    @Test
    fun `arranca en Idle con el campo vacio`() {
        val state = viewModel().uiState.value

        assertEquals(DeleteAccountStatus.Idle, state.status)
        assertEquals("", state.password)
        assertFalse(state.passwordMissing)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `onPasswordChange actualiza el campo`() {
        val viewModel = viewModel()

        viewModel.onPasswordChange("mi-clave")

        assertEquals("mi-clave", viewModel.uiState.value.password)
    }

    // --- Fail-Fast ---

    @Test
    fun `confirmar con el campo vacio marca passwordMissing y no llama al backend`() {
        val viewModel = viewModel()

        viewModel.submit()

        assertTrue(viewModel.uiState.value.passwordMissing)
        assertEquals(DeleteAccountStatus.Idle, viewModel.uiState.value.status)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun `una contrasena de solo espacios cuenta como vacia`() {
        val viewModel = viewModel()

        viewModel.typeAndSubmit("   ")

        assertTrue(viewModel.uiState.value.passwordMissing)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun `tipear despues de passwordMissing lo limpia`() {
        val viewModel = viewModel()
        viewModel.submit()

        viewModel.onPasswordChange("m")

        assertFalse(viewModel.uiState.value.passwordMissing)
    }

    // --- Baja exitosa ---

    @Test
    fun `una baja exitosa termina en Deleted y limpia lo local`() = runTest {
        val viewModel = viewModel()

        viewModel.typeAndSubmit("mi-clave")

        assertEquals(DeleteAccountStatus.Deleted, viewModel.uiState.value.status)
        assertEquals(listOf("mi-clave"), repository.receivedPasswords)
        assertEquals(1, eraser.completedCount)
    }

    @Test
    fun `tras la baja la contrasena ya no queda en el estado`() = runTest {
        val viewModel = viewModel()

        viewModel.typeAndSubmit("mi-clave")

        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `despues de Deleted confirmar de nuevo no llama otra vez al backend`() = runTest {
        val viewModel = viewModel()
        viewModel.typeAndSubmit("mi-clave")

        viewModel.submit()

        assertEquals(1, repository.callCount)
    }

    @Test
    fun `despues de Deleted tipear no reabre el campo`() = runTest {
        val viewModel = viewModel()
        viewModel.typeAndSubmit("mi-clave")

        viewModel.onPasswordChange("otra")

        assertEquals("", viewModel.uiState.value.password)
        assertEquals(DeleteAccountStatus.Deleted, viewModel.uiState.value.status)
    }

    // --- Errores ---

    @Test
    fun `contrasena incorrecta queda en Failed InvalidPassword y no limpia lo local`() = runTest {
        repository.result = DeleteAccountResult.Failed(DeleteAccountError.InvalidPassword)
        val viewModel = viewModel()

        viewModel.typeAndSubmit("mala")

        assertEquals(DeleteAccountStatus.Failed(DeleteAccountError.InvalidPassword), viewModel.uiState.value.status)
        assertEquals(DeleteAccountError.InvalidPassword, viewModel.uiState.value.error)
        assertEquals(0, eraser.startedCount)
    }

    @Test
    fun `tras un rechazo la contrasena tipeada se conserva para corregirla`() = runTest {
        repository.result = DeleteAccountResult.Failed(DeleteAccountError.InvalidPassword)
        val viewModel = viewModel()

        viewModel.typeAndSubmit("mala")

        assertEquals("mala", viewModel.uiState.value.password)
    }

    @Test
    fun `cada error del dominio llega tal cual a la pantalla y no limpia lo local`() = runTest {
        listOf(
            DeleteAccountError.SessionExpired,
            DeleteAccountError.RateLimited,
            DeleteAccountError.Unavailable,
            DeleteAccountError.Network,
            DeleteAccountError.Unexpected,
        ).forEach { error ->
            repository.result = DeleteAccountResult.Failed(error)
            val viewModel = viewModel()

            viewModel.typeAndSubmit()

            assertEquals("error $error", error, viewModel.uiState.value.error)
        }
        assertEquals(0, eraser.startedCount)
    }

    @Test
    fun `seguir editando tras un error lo limpia`() = runTest {
        repository.result = DeleteAccountResult.Failed(DeleteAccountError.InvalidPassword)
        val viewModel = viewModel()
        viewModel.typeAndSubmit("mala")

        viewModel.onPasswordChange("mala2")

        assertEquals(DeleteAccountStatus.Idle, viewModel.uiState.value.status)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `tras un error reintentable se puede confirmar de nuevo y salir bien`() = runTest {
        repository.result = DeleteAccountResult.Failed(DeleteAccountError.Unavailable)
        val viewModel = viewModel()
        viewModel.typeAndSubmit("mi-clave")
        assertEquals(DeleteAccountError.Unavailable, viewModel.uiState.value.error)

        repository.result = DeleteAccountResult.Deleted
        viewModel.submit()

        assertEquals(DeleteAccountStatus.Deleted, viewModel.uiState.value.status)
        assertEquals(listOf("mi-clave", "mi-clave"), repository.receivedPasswords)
    }

    // --- Doble tap ---

    @Test
    fun `mientras la baja esta en curso isSubmitting es true`() = runTest {
        repository.gate = CompletableDeferred<Unit>()
        val viewModel = viewModel()

        viewModel.typeAndSubmit()

        assertTrue(viewModel.uiState.value.isSubmitting)
        repository.gate?.complete(Unit)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    /** Cada intento gasta uno de los 3 por minuto del endpoint. */
    @Test
    fun `un doble tap no dispara una segunda baja`() = runTest {
        repository.gate = CompletableDeferred<Unit>()
        val viewModel = viewModel()
        viewModel.onPasswordChange("mi-clave")

        viewModel.submit()
        viewModel.submit()
        repository.gate?.complete(Unit)

        assertEquals(1, repository.callCount)
    }

    @Test
    fun `mientras la baja esta en curso tipear no cambia el estado`() = runTest {
        repository.gate = CompletableDeferred<Unit>()
        val viewModel = viewModel()
        viewModel.typeAndSubmit("mi-clave")

        viewModel.onPasswordChange("otra")

        assertEquals("mi-clave", viewModel.uiState.value.password)
        repository.gate?.complete(Unit)
    }

    // --- Datos sensibles ---

    @Test
    fun `la contrasena no aparece en el toString del estado`() {
        val viewModel = viewModel()

        viewModel.onPasswordChange("mi-clave-secreta")

        assertFalse(viewModel.uiState.value.toString().contains("mi-clave-secreta"))
    }
}
