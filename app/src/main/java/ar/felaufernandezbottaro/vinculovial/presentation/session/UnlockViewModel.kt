package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.data.session.UnlockOutcome
import ar.felaufernandezbottaro.vinculovial.data.session.UnlockPreparation
import ar.felaufernandezbottaro.vinculovial.domain.session.UnlockSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.inject.Inject

/** Por qué el prompt biométrico terminó sin autenticar. */
enum class PromptFailure {
    /** El usuario lo cerró (o el sistema lo canceló): no es un error, se vuelve a ofrecer el botón. */
    CANCELLED,

    /** El sistema bloqueó la huella por demasiados intentos. */
    LOCKED_OUT,

    OTHER,
}

/**
 * ViewModel del Desbloqueo (US-12). No navega: cuando el desbloqueo sale bien, o cuando la
 * sesión se pierde (401, huellas cambiadas), cambia el estado de la sesión y es el nav host
 * quien mueve al usuario -- ver `worldRedirect`.
 *
 * El prompt biométrico es de UI y necesita la Activity, así que lo muestra quien llama a
 * [requestUnlock]; este ViewModel sólo arma el `Cipher` y recibe el resultado.
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val unlockSession: UnlockSessionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra un doble toque en "Usar huella". */
    private val requestInFlight = AtomicBoolean(false)

    /** Prepara el `Cipher` y lo entrega a [showPrompt], que abre el prompt biométrico. */
    fun requestUnlock(showPrompt: (Cipher) -> Unit) {
        if (_uiState.value.isBusy || !requestInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(status = UnlockStatus.Preparing) }
                when (val preparation = unlockSession.prepare()) {
                    is UnlockPreparation.Ready -> {
                        _uiState.update { it.copy(status = UnlockStatus.Authenticating) }
                        showPrompt(preparation.cipher)
                    }
                    // La clave quedó invalidada y ya se borró todo: el nav host lleva a Login.
                    UnlockPreparation.KeyInvalidated -> _uiState.update { it.copy(status = UnlockStatus.Idle) }
                    UnlockPreparation.Unavailable -> fail(UnlockFailure.BIOMETRIC_ERROR)
                }
            } finally {
                requestInFlight.set(false)
            }
        }
    }

    /** La huella se verificó: [cipher] ya puede descifrar el blob. */
    fun onAuthenticated(cipher: Cipher) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = UnlockStatus.Refreshing) }
            when (unlockSession(cipher)) {
                // En ambos casos cambió el estado de la sesión y el nav host se encarga.
                UnlockOutcome.Unlocked,
                UnlockOutcome.Expired,
                -> _uiState.update { it.copy(status = UnlockStatus.Idle) }

                UnlockOutcome.Unavailable -> fail(UnlockFailure.NETWORK)
            }
        }
    }

    fun onPromptFailed(failure: PromptFailure) {
        when (failure) {
            PromptFailure.CANCELLED -> _uiState.update { it.copy(status = UnlockStatus.Idle) }
            PromptFailure.LOCKED_OUT -> fail(UnlockFailure.LOCKED_OUT)
            PromptFailure.OTHER -> fail(UnlockFailure.BIOMETRIC_ERROR)
        }
    }

    private fun fail(failure: UnlockFailure) {
        _uiState.update { it.copy(status = UnlockStatus.Failed(failure)) }
    }
}
