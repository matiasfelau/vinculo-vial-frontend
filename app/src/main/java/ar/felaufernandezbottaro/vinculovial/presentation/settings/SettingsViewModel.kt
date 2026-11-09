package ar.felaufernandezbottaro.vinculovial.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.session.BiometricUnlockSettings
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
 *
 * También maneja el desbloqueo con biometría (US-12): el interruptor de esta pantalla y el
 * ofrecimiento que aparece una vez tras cada login con contraseña.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
    private val biometricSettings: BiometricUnlockSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isBiometricAvailable = biometricSettings.isEnabled.value || biometricSettings.isAvailable(),
            isBiometricEnabled = biometricSettings.isEnabled.value,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra un doble tap en "Cerrar sesión". */
    private val logoutInFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            biometricSettings.isEnabled.collect { enabled ->
                _uiState.update {
                    it.copy(
                        isBiometricEnabled = enabled,
                        isBiometricAvailable = enabled || biometricSettings.isAvailable(),
                    )
                }
            }
        }
        viewModelScope.launch {
            biometricSettings.shouldOffer.collect { offer ->
                _uiState.update { it.copy(showBiometricOffer = offer) }
            }
        }
    }

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

    /** El interruptor de Settings y el "Activar" del ofrecimiento. */
    fun onBiometricToggle(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                if (!biometricSettings.enable()) _uiState.update { it.copy(biometricActivationFailed = true) }
            } else {
                biometricSettings.disable()
            }
        }
    }

    fun onBiometricOfferDismissed() = biometricSettings.dismissOffer()

    fun onBiometricActivationFailureDismissed() {
        _uiState.update { it.copy(biometricActivationFailed = false) }
    }
}
