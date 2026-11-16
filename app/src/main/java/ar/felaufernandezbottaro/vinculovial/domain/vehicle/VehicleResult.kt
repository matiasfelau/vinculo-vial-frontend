package ar.felaufernandezbottaro.vinculovial.domain.vehicle

/**
 * Resultado de una operación sobre los vehículos vinculados, visto desde el dominio.
 * `presentation` nunca ve un código HTTP ni una excepción cruda: sólo estos casos.
 */
sealed interface VehicleResult<out T> {

    data class Success<T>(val value: T) : VehicleResult<T>

    data class Failed(val error: VehicleError) : VehicleResult<Nothing>
}

/** Por qué falló una operación sobre los vehículos. Se mapea por el `code` del backend. */
sealed interface VehicleError {

    /** `INVALID_PLATE` (400): criptograma inválido o patente con formato inválido. */
    data object InvalidPlate : VehicleError

    /** `VEHICLE_NOT_FOUND` (404): el usuario no tiene vinculada esa patente. */
    data object NotFound : VehicleError

    /** `DUPLICATE_VEHICLE` (409): el usuario ya tiene esa patente vinculada. */
    data object Duplicate : VehicleError

    /** `VEHICLE_LIMIT_REACHED` (409): el usuario ya tiene el máximo de vehículos. */
    data object LimitReached : VehicleError

    /** `DEPENDENCY_UNAVAILABLE` (503), 502/504 o circuito abierto. Transitorio. */
    data object Unavailable : VehicleError

    /** Sin conexión, timeout o servidor inalcanzable. */
    data object Network : VehicleError

    /** Falta o está mal la configuración de build (clave HPKE de Vehicle). */
    data object Configuration : VehicleError

    /** Cualquier otra cosa, incluido un 401 que `TokenAuthenticator` no pudo resolver. */
    data object Unexpected : VehicleError

    val isTransient: Boolean
        get() = this is Unavailable || this is Network
}
