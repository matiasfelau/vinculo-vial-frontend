package ar.felaufernandezbottaro.vinculovial.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountResult
import ar.felaufernandezbottaro.vinculovial.domain.account.DeleteAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ViewModel de la pantalla "Eliminar cuenta". Sólo habla con `domain`: nunca ve códigos HTTP ni
 * excepciones de red. Tampoco navega -- publica [DeleteAccountStatus] y es el nav host quien
 * decide a dónde ir (bienvenida tras la baja, Login si la sesión venció).
 */
@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState.asStateFlow()

    /** Barrera sincrónica contra un doble tap en "Eliminar mi cuenta". Ver [submit]. */
    private val submitInFlight = AtomicBoolean(false)

    fun onPasswordChange(value: String) {
        _uiState.update { state ->
            if (state.isSubmitting || state.status is DeleteAccountStatus.Deleted) return@update state
            state.copy(
                password = value,
                passwordMissing = false,
                // Seguir editando tras un rechazo limpia el mensaje: si no, "Contraseña
                // incorrecta" queda pegado en pantalla mientras el usuario corrige.
                status = if (state.status is DeleteAccountStatus.Failed) DeleteAccountStatus.Idle else state.status,
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || state.status is DeleteAccountStatus.Deleted) return

        if (state.password.isBlank()) {
            _uiState.update { it.copy(passwordMissing = true) }
            return
        }

        // compareAndSet corta un doble tap antes de que la recomposición refleje
        // isSubmitting = true: cada intento gasta uno de los 3 por minuto de la baja.
        if (!submitInFlight.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(status = DeleteAccountStatus.Submitting, passwordMissing = false) }

                _uiState.update {
                    when (val result = deleteAccount(state.password)) {
                        // La contraseña ya cumplió su función: no se la deja en el estado.
                        is DeleteAccountResult.Deleted -> it.copy(password = "", status = DeleteAccountStatus.Deleted)
                        is DeleteAccountResult.Failed -> it.copy(status = DeleteAccountStatus.Failed(result.error))
                    }
                }
            } finally {
                submitInFlight.set(false)
            }
        }
    }
}
