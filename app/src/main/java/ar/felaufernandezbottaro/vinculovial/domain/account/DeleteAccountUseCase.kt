package ar.felaufernandezbottaro.vinculovial.domain.account

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Baja de cuenta (US-11): primero el backend, después el dispositivo.
 *
 * La limpieza local corre sólo ante un 204 y no es cancelable: con la cuenta ya borrada del
 * lado del servidor, que el usuario salga de la pantalla a mitad de camino no puede dejar
 * tokens ni claves de una cuenta que ya no existe. Ante cualquier fallo no se borra nada
 * local -- el usuario sigue con su sesión y puede reintentar.
 */
class DeleteAccountUseCase @Inject constructor(
    private val repository: AccountRepository,
    private val localDataEraser: LocalAccountDataEraser,
) {
    suspend operator fun invoke(password: String): DeleteAccountResult {
        val result = repository.deleteAccount(password)
        if (result is DeleteAccountResult.Deleted) {
            withContext(NonCancellable) { localDataEraser.eraseAll() }
        }
        return result
    }
}
