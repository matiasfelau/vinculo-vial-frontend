package ar.felaufernandezbottaro.vinculovial.domain.session

import ar.felaufernandezbottaro.vinculovial.data.session.SessionManager
import javax.inject.Inject

/**
 * Punto de entrada desde `presentation` hacia el logout. `SessionManager` ya centraliza la
 * limpieza y la llamada a `/logout`; esto es sólo el orquestador delgado que el resto de los
 * casos de uso de esta app también expone hacia la UI.
 */
class LogoutUseCase @Inject constructor(
    private val sessionManager: SessionManager,
) {
    suspend operator fun invoke() = sessionManager.logout()
}
