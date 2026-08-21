package ar.felaufernandezbottaro.vinculovial.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.session.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Una vez que el logout limpia la sesión, es el nav host quien saca al usuario a Login
 * (observa `SecureTokenStore.sessionState` vía `SessionViewModel`, ver
 * `VinculoVialNavHost`) -- este ViewModel no navega, sólo dispara el caso de uso.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra un doble tap en "Cerrar sesión". */
    private val logoutInFlight = AtomicBoolean(false)

    fun logout() {
        if (!logoutInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            try {
                logoutUseCase()
            } finally {
                logoutInFlight.set(false)
                _uiState.update { it.copy(isLoggingOut = false) }
            }
        }
    }
}
