package ar.felaufernandezbottaro.vinculovial.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxUseCase
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Carga la primera página del buzón al crearse. La paginación por scroll (página siguiente,
 * guarda de reentrada, `ContentWithPageError`) llega en una entrega posterior de US-07.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val getInbox: GetInboxUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.update { InboxUiState.Loading }

            when (val result = getInbox()) {
                is GetInboxResult.Success -> _uiState.update { onFirstPageLoaded(result.page) }
                is GetInboxResult.Failed -> _uiState.update { InboxUiState.Error(result.error) }
            }
        }
    }

    /** Un `200` con `items` vacío es `Empty`, nunca `Error`: no tener incidentes es lo deseable. */
    private fun onFirstPageLoaded(page: InboxPage): InboxUiState =
        if (page.items.isEmpty()) {
            InboxUiState.Empty
        } else {
            InboxUiState.Content(items = page.items, nextCursor = page.nextCursor)
        }
}
