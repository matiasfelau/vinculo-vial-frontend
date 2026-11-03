package ar.felaufernandezbottaro.vinculovial.domain.account

/** Abstrae todo lo que la app guarda del usuario en el dispositivo: `data.account` tiene la única implementación. */
interface LocalAccountDataEraser {

    /**
     * Borra tokens de sesión, las claves de Keystore que los cifraban, el token FCM y cualquier
     * estado pendiente de push. No falla hacia quien llama: cada paso es independiente y uno
     * roto no deja a los demás sin ejecutar.
     */
    suspend fun eraseAll()
}
