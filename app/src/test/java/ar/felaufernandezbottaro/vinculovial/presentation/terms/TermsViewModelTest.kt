package ar.felaufernandezbottaro.vinculovial.presentation.terms

import ar.felaufernandezbottaro.vinculovial.domain.terms.GetTermsUseCase
import ar.felaufernandezbottaro.vinculovial.domain.terms.LegalInfo
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsBlock
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsSource
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsSpan
import ar.felaufernandezbottaro.vinculovial.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TermsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeTermsSource(var markdown: String = "# T {{VERSION}}\n\nResp: {{RESPONSABLE}}.") : TermsSource {
        var failure: Exception? = null
        var reads = 0

        override suspend fun markdown(): String {
            reads++
            failure?.let { throw it }
            return markdown
        }
    }

    private val legal = LegalInfo(responsibleName = "ACME SA", contactEmail = "a@b.com", address = "Calle 1", version = "1.0")
    private val source = FakeTermsSource()

    private fun viewModel() = TermsViewModel(GetTermsUseCase(source, legal))

    @Test
    fun `al abrir carga el documento con los marcadores completados`() = runTest {
        val state = viewModel().uiState.value

        assertEquals(
            TermsUiState.Ready(
                listOf(
                    TermsBlock.Heading(1, "T 1.0"),
                    TermsBlock.Paragraph(listOf(TermsSpan("Resp: ACME SA."))),
                ),
            ),
            state,
        )
    }

    @Test
    fun `si el texto no se puede leer queda Failed, sin tumbar la pantalla`() = runTest {
        source.failure = RuntimeException("recurso ausente")

        assertEquals(TermsUiState.Failed, viewModel().uiState.value)
    }

    @Test
    fun `reintentar vuelve a leer y se recupera`() = runTest {
        source.failure = RuntimeException("recurso ausente")
        val viewModel = viewModel()

        source.failure = null
        viewModel.load()

        assertEquals(2, source.reads)
        assertEquals(true, viewModel.uiState.value is TermsUiState.Ready)
    }
}
