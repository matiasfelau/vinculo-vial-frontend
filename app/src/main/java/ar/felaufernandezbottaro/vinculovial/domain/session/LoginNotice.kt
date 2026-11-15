package ar.felaufernandezbottaro.vinculovial.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Por qué el usuario llega a Login sin haberlo pedido: se lo explica la pantalla de Login. */
enum class LoginNotice {
    /** El servidor ya no reconoce la sesión guardada (venció o llegó a su vida máxima). */
    SESSION_EXPIRED,

    /** Se enroló una huella nueva en el dispositivo: la clave biométrica quedó invalidada. */
    BIOMETRICS_CHANGED,
}

/**
 * Lleva un [LoginNotice] desde donde se pierde la sesión (el desbloqueo) hasta la pantalla de
 * Login, que no comparten ViewModel ni ruta. Vive en memoria: el aviso no sobrevive al proceso.
 */
@Singleton
class LoginNoticeStore @Inject constructor() {

    private val _notice = MutableStateFlow<LoginNotice?>(null)
    val notice: StateFlow<LoginNotice?> = _notice.asStateFlow()

    fun post(notice: LoginNotice) {
        _notice.value = notice
    }

    fun clear() {
        _notice.value = null
    }
}
