package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitError
import ar.felaufernandezbottaro.vinculovial.domain.benefits.ClaimResult
import ar.felaufernandezbottaro.vinculovial.domain.benefits.FakeBenefitsRepository
import ar.felaufernandezbottaro.vinculovial.domain.benefits.benefit
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** El carrusel y el detalle de US-18: qué estado muestra cada paso y qué hace cada error. */
@OptIn(ExperimentalCoroutinesApi::class)
class BenefitsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBenefitsRepository()

    private val first = benefit(id = "uno", title = "Uno")
    private val second = benefit(id = "dos", title = "Dos")

    private fun viewModel() = BenefitsViewModel(repository)

    // -- Carrusel -----------------------------------------------------------------------------

    @Test
    fun `al crearse carga las promociones`() = runTest {
        repository.benefits = listOf(first, second)

        val viewModel = viewModel()

        assertEquals(listOf(first, second), viewModel.uiState.value.benefits)
        assertEquals(1, repository.listCalls)
    }

    @Test
    fun `con la lista vacia no hay nada que mostrar`() = runTest {
        repository.benefits = emptyList()

        assertEquals(emptyList<Any>(), viewModel().uiState.value.benefits)
    }

    @Test
    fun `si la carga falla la lista queda vacia, sin error`() = runTest {
        repository.listFails = true

        val state = viewModel().uiState.value

        assertEquals(BenefitsUiState(), state)
    }

    @Test
    fun `una recarga fallida esconde el carrusel que se veia`() = runTest {
        repository.benefits = listOf(first)
        val viewModel = viewModel()

        repository.listFails = true
        viewModel.refresh()

        assertEquals(emptyList<Any>(), viewModel.uiState.value.benefits)
    }

    @Test
    fun `volver a cargar trae las promociones nuevas y saca las que vencieron`() = runTest {
        repository.benefits = listOf(first, second)
        val viewModel = viewModel()

        repository.benefits = listOf(second)
        viewModel.refresh()

        assertEquals(listOf(second), viewModel.uiState.value.benefits)
        assertEquals(2, repository.listCalls)
    }

    @Test
    fun `el banner lo trae el repositorio por el id`() = runTest {
        repository.banners["uno"] = byteArrayOf(1, 2, 3)
        val viewModel = viewModel()

        assertArrayEquals(byteArrayOf(1, 2, 3), viewModel.banner("uno"))
        assertNull(viewModel.banner("otro"))
        assertEquals(listOf("uno", "otro"), repository.bannerRequests)
    }

    // -- Detalle ------------------------------------------------------------------------------

    @Test
    fun `abrir una promocion la deja seleccionada, sin pedido en curso`() = runTest {
        repository.benefits = listOf(first, second)
        val viewModel = viewModel()

        viewModel.open("dos")

        assertEquals(second, viewModel.uiState.value.selected)
        assertEquals(ClaimState.Idle, viewModel.uiState.value.claim)
    }

    @Test
    fun `abrir una promocion que no esta en la lista no hace nada`() = runTest {
        repository.benefits = listOf(first)
        val viewModel = viewModel()

        viewModel.open("fantasma")

        assertNull(viewModel.uiState.value.selected)
    }

    @Test
    fun `cerrar el detalle limpia la seleccion y cualquier error`() = runTest {
        repository.benefits = listOf(first)
        repository.claimResult = ClaimResult.Failed(BenefitError.RateLimited)
        val viewModel = viewModel()
        viewModel.open("uno")
        viewModel.claim()

        viewModel.closeDetail()

        assertNull(viewModel.uiState.value.selected)
        assertEquals(ClaimState.Idle, viewModel.uiState.value.claim)
    }

    @Test
    fun `reabrir el detalle no arrastra el error de la vez anterior`() = runTest {
        repository.benefits = listOf(first)
        repository.claimResult = ClaimResult.Failed(BenefitError.Network)
        val viewModel = viewModel()
        viewModel.open("uno")
        viewModel.claim()

        viewModel.open("uno")

        assertEquals(ClaimState.Idle, viewModel.uiState.value.claim)
    }

    // -- Obtener código -----------------------------------------------------------------------

    @Test
    fun `obtener el codigo lo guarda en la promocion abierta y en la lista`() = runTest {
        repository.benefits = listOf(first, second)
        repository.claimResult = ClaimResult.Success("ABCD-1234")
        val viewModel = viewModel()
        viewModel.open("uno")

        viewModel.claim()

        val state = viewModel.uiState.value
        assertEquals("ABCD-1234", state.selected?.code)
        assertEquals("ABCD-1234", state.benefits.first { it.id == "uno" }.code)
        assertNull(state.benefits.first { it.id == "dos" }.code)
        assertEquals(ClaimState.Idle, state.claim)
        assertEquals(listOf("uno"), repository.claimRequests)
    }

    @Test
    fun `con el codigo ya a la vista no se vuelve a pedir`() = runTest {
        repository.benefits = listOf(first.copy(code = "YA-TENIA"))
        val viewModel = viewModel()
        viewModel.open("uno")

        viewModel.claim()

        assertEquals(emptyList<String>(), repository.claimRequests)
        assertEquals("YA-TENIA", viewModel.uiState.value.selected?.code)
    }

    @Test
    fun `sin una promocion abierta pedir el codigo no hace nada`() = runTest {
        repository.benefits = listOf(first)
        val viewModel = viewModel()

        viewModel.claim()

        assertEquals(emptyList<String>(), repository.claimRequests)
    }

    @Test
    fun `mientras el pedido esta en curso se ve Claiming y un segundo toque no pide de nuevo`() = runTest {
        repository.benefits = listOf(first)
        val gate = CompletableDeferred<Unit>()
        repository.claimGate = gate
        val viewModel = viewModel()
        viewModel.open("uno")

        viewModel.claim()
        viewModel.claim()

        assertEquals(ClaimState.Claiming, viewModel.uiState.value.claim)
        assertEquals(1, repository.claimRequests.size)

        gate.complete(Unit)
        assertEquals(ClaimState.Idle, viewModel.uiState.value.claim)
    }

    @Test
    fun `NO_CODES_LEFT deja el error y la promocion sin codigo`() = runTest {
        repository.benefits = listOf(first)
        repository.claimResult = ClaimResult.Failed(BenefitError.NoCodesLeft)
        val viewModel = viewModel()
        viewModel.open("uno")

        viewModel.claim()

        val state = viewModel.uiState.value
        assertEquals(ClaimState.Failed(BenefitError.NoCodesLeft), state.claim)
        assertNull(state.selected?.code)
        assertEquals(1, repository.listCalls)
    }

    @Test
    fun `BENEFIT_NOT_FOUND deja el error y recarga la lista, sin perder el detalle abierto`() = runTest {
        repository.benefits = listOf(first, second)
        repository.claimResult = ClaimResult.Failed(BenefitError.NotFound)
        val viewModel = viewModel()
        viewModel.open("uno")

        // El servidor ya no la trae: venció mientras el usuario miraba el detalle.
        repository.benefits = listOf(second)
        viewModel.claim()

        val state = viewModel.uiState.value
        assertEquals(ClaimState.Failed(BenefitError.NotFound), state.claim)
        assertEquals(2, repository.listCalls)
        assertEquals(listOf(second), state.benefits)
        assertEquals(first, state.selected)
    }

    @Test
    fun `los errores transitorios se pueden reintentar`() = runTest {
        repository.benefits = listOf(first)
        repository.claimResult = ClaimResult.Failed(BenefitError.Unavailable)
        val viewModel = viewModel()
        viewModel.open("uno")
        viewModel.claim()
        assertEquals(ClaimState.Failed(BenefitError.Unavailable), viewModel.uiState.value.claim)

        repository.claimResult = ClaimResult.Success("OTRA-VEZ")
        viewModel.claim()

        assertEquals("OTRA-VEZ", viewModel.uiState.value.selected?.code)
        assertEquals(ClaimState.Idle, viewModel.uiState.value.claim)
        assertEquals(2, repository.claimRequests.size)
    }

    @Test
    fun `una recarga actualiza la promocion abierta con el codigo que trae`() = runTest {
        repository.benefits = listOf(first)
        val viewModel = viewModel()
        viewModel.open("uno")

        repository.benefits = listOf(first.copy(code = "DE-OTRO-DISPOSITIVO"))
        viewModel.refresh()

        assertEquals("DE-OTRO-DISPOSITIVO", viewModel.uiState.value.selected?.code)
    }

    @Test
    fun `los textos de cada error son los del contrato`() {
        assertEquals("Se agotaron los códigos de esta promoción", BenefitTexts.errorMessage(BenefitError.NoCodesLeft))
        assertEquals("Esta promoción ya no está disponible", BenefitTexts.errorMessage(BenefitError.NotFound))
        listOf(BenefitError.RateLimited, BenefitError.Unavailable, BenefitError.Network, BenefitError.Unexpected)
            .map { BenefitTexts.errorMessage(it) }
            .also { messages -> assertEquals(messages.size, messages.toSet().size) }
    }
}
