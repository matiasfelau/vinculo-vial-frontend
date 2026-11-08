package ar.felaufernandezbottaro.vinculovial.data.session

import com.google.crypto.tink.Aead
import javax.inject.Inject
import javax.inject.Singleton

/**
 * De dónde sale la clave que cifra los tokens de sesión, y cómo se destruye. Separado de
 * [SessionAead] para que la lógica de destruir y regenerar se pruebe en JVM: el Android
 * Keystore sólo existe en un dispositivo.
 */
interface SessionKeyProvisioner {

    /** El [Aead] respaldado por la clave persistida; si no existe todavía, genera una y la persiste. */
    fun provision(): Aead

    /** Borra la clave persistida. Nada cifrado con ella vuelve a poder leerse. */
    fun destroy()
}

/**
 * El [Aead] de `SecureTokenStore`, con una clave que se puede destruir (US-11, baja de cuenta).
 *
 * Tink arma el [Aead] una sola vez y lo mantiene en memoria. Si la baja borrara la clave del
 * Keystore y dejara ese objeto vivo, el próximo login de este mismo proceso cifraría con una
 * clave que ya no existe en disco: al reiniciar la app, la sesión quedaría ilegible y el
 * usuario perdería el acceso sin explicación. Por eso [destroyKeys] suelta también el objeto en
 * memoria, y el próximo uso provisiona una clave nueva.
 *
 * Quien llama [destroyKeys] tiene que haber borrado antes los tokens: nada cifrado con la clave
 * vieja sobrevive a esta llamada.
 */
@Singleton
class SessionAead @Inject constructor(
    private val provisioner: SessionKeyProvisioner,
) : Aead {

    // Se provisiona al construirse, igual que antes de esta clase: un Keystore roto tiene que
    // fallar al arrancar, no la primera vez que se lee un token.
    @Volatile
    private var delegate: Aead? = provisioner.provision()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        current().encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        current().decrypt(ciphertext, associatedData)

    /**
     * Destruye la clave persistida y suelta la de memoria. La de memoria se suelta aun si
     * destruir falla a medias: el próximo uso la reconstruye desde lo que haya quedado en
     * disco, así que nunca hay una clave en memoria que el disco no tenga.
     */
    @Synchronized
    fun destroyKeys() {
        try {
            provisioner.destroy()
        } finally {
            delegate = null
        }
    }

    private fun current(): Aead = delegate ?: synchronized(this) {
        delegate ?: provisioner.provision().also { delegate = it }
    }
}
