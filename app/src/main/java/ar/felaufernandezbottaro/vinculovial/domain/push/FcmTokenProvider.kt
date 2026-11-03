package ar.felaufernandezbottaro.vinculovial.domain.push

/** Abstrae el SDK de Firebase: `data.push.FirebaseFcmTokenProvider` es la única implementación real. */
interface FcmTokenProvider {

    /** `null` si el SDK no pudo entregar un token (sin Firebase inicializado, sin red, etc). */
    suspend fun getCurrentToken(): String?

    /**
     * Invalida el token de este dispositivo en el SDK (`FirebaseMessaging.deleteToken()`); la
     * próxima vez que se pida uno, el SDK genera otro. `false` si no se pudo (sin red, sin
     * Firebase inicializado): nunca lanza.
     */
    suspend fun deleteCurrentToken(): Boolean
}
