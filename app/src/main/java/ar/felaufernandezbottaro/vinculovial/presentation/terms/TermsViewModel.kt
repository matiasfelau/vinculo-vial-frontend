package ar.felaufernandezbottaro.vinculovial.presentation.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.terms.GetTermsUseCase
import ar.felaufernandezbottaro.vinculovial.domain.terms.TermsBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TermsUiState {
    data object Loading : TermsUiState

    /** El documento ya con los marcadores completados. */
    data class Ready(val blocks: List<TermsBlock>) : TermsUiState

    /** El texto no se pudo leer. No debería pasar (va dentro del APK), pero la pantalla no se rompe. */
    data object Failed : TermsUiState
}

/** La pantalla pública "Términos y Condiciones" (US-19): no requiere sesión, no toca la red. */
@HiltViewModel
class TermsViewModel @Inject constructor(
    private val getTerms: GetTermsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TermsUiState>(TermsUiState.Loading)
    val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = TermsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                TermsUiState.Ready(getTerms())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TermsUiState.Failed
            }
        }
    }
}
