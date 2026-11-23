package ar.felaufernandezbottaro.vinculovial.domain.recovery

/**
 * Lo que hay que borrar del dispositivo cuando se recupera la contraseña: el servidor ya revocó
 * todas las sesiones de la cuenta, así que el refresh envuelto con la clave biométrica (el blob) ya
 * no sirve y su clave del Keystore tampoco. `data.recovery` tiene la única implementación.
 */
interface RecoverySessionCleaner {

    /** Borra tokens en memoria, blob y clave biométrica. La sesión queda en NONE. */
    suspend fun clear()
}
