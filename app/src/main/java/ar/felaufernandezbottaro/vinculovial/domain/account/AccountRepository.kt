package ar.felaufernandezbottaro.vinculovial.domain.account

interface AccountRepository {

    /**
     * `DELETE /api/v1/users/me` confirmado con la contraseña actual. Sólo habla con el backend:
     * la limpieza local es de [LocalAccountDataEraser] y la dispara [DeleteAccountUseCase].
     */
    suspend fun deleteAccount(password: String): DeleteAccountResult
}
