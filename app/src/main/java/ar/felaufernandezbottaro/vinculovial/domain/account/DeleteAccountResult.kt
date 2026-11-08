package ar.felaufernandezbottaro.vinculovial.domain.account

/**
 * Resultado de la baja de cuenta (US-11) visto desde el dominio. `presentation` nunca ve
 * códigos HTTP ni excepciones crudas: sólo estos casos.
 */
sealed interface DeleteAccountResult {

    /**
     * 204: la cuenta ya no existe. El backend responde lo mismo si el usuario ya no existía
     * (es idempotente), así que acá no hay forma de distinguir una baja de un reintento.
     */
    data object Deleted : DeleteAccountResult

    data class Failed(val error: DeleteAccountError) : DeleteAccountResult
}

sealed interface DeleteAccountError {

    /** 401 `INVALID_PASSWORD`: la contraseña no coincide. El usuario puede corregirla. */
    data object InvalidPassword : DeleteAccountError

    /**
     * 401 sin `code`: la sesión venció y el refresh silencioso tampoco la recuperó. Sigue el
     * flujo de sesión vigente (Login), no es un error de la baja en sí.
     */
    data object SessionExpired : DeleteAccountError

    /** 429: límite de la baja (3/min y 10/h por usuario). Reintentable más tarde. */
    data object RateLimited : DeleteAccountError

    /** 502/503/504 o circuito abierto: el Gateway o Identity no están disponibles. Transitorio. */
    data object Unavailable : DeleteAccountError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : DeleteAccountError

    /** Cualquier otra cosa, incluido 400 y 500. */
    data object Unexpected : DeleteAccountError
}
