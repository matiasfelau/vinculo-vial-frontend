package ar.felaufernandezbottaro.vinculovial.domain.push

/** Abstrae el SDK de Firebase: `data.push.FirebaseFcmTokenProvider` es la única implementación real. */
interface FcmTokenProvider {

    /** `null` si el SDK no pudo entregar un token (sin Firebase inicializado, sin red, etc). */
    suspend fun getCurrentToken(): String?
}
