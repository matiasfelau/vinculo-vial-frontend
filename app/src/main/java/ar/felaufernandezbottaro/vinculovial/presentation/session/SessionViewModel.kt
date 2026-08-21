package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.lifecycle.ViewModel
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Único punto donde `presentation` toca `SecureTokenStore` directo: no hay lógica de negocio
 * acá, sólo el estado de sesión que necesita el nav host para reaccionar -- ya sea a un
 * logout explícito o a uno forzado por un RTR fallido -- sin que la pantalla que originó la
 * request tenga que enterarse ni orquestar nada.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    tokenStore: SecureTokenStore,
) : ViewModel() {
    val hasSession: StateFlow<Boolean> = tokenStore.sessionState
}
