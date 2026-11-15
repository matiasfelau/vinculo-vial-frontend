package ar.felaufernandezbottaro.vinculovial.presentation.session

import androidx.lifecycle.ViewModel
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Único punto donde `presentation` toca `SecureTokenStore` directo: no hay lógica de negocio
 * acá, sólo el estado de sesión que necesita el nav host para reaccionar -- ya sea a un
 * logout explícito, a un bloqueo o a una sesión perdida por un RTR fallido -- sin que la
 * pantalla que originó la request tenga que enterarse ni orquestar nada.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    tokenStore: SecureTokenStore,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = tokenStore.sessionState
}
