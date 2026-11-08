package ar.felaufernandezbottaro.vinculovial.data.session

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig

/**
 * El Keystore en memoria: `persisted` hace de keyset + clave maestra en disco. [provision] lo
 * recupera o genera uno si no está, igual que Tink con `AndroidKeysetManager`; [destroy] lo borra.
 * Dos [SessionAead] sobre el mismo fake se comportan como dos arranques de la app sobre el mismo
 * dispositivo.
 */
class FakeSessionKeyProvisioner : SessionKeyProvisioner {

    private var persisted: KeysetHandle? = null

    var provisionCount = 0
        private set
    var destroyCount = 0
        private set

    /** Si se setea, [destroy] falla con esto antes de borrar nada. */
    var destroyFailure: RuntimeException? = null

    val hasPersistedKey: Boolean get() = persisted != null

    override fun provision(): Aead {
        AeadConfig.register()
        provisionCount++
        val handle = persisted ?: KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).also { persisted = it }
        return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun destroy() {
        destroyCount++
        destroyFailure?.let { throw it }
        persisted = null
    }
}
