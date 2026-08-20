package ar.felaufernandezbottaro.vinculovial.data.session

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecureTokenStoreTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var aead: Aead

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")

    @Before
    fun setUp() {
        AeadConfig.register()
        prefs = FakeSharedPreferences()
        // Keyset en memoria, sin Keystore: acá lo que se prueba es el round-trip de
        // SecureTokenStore, no la integración real con Android Keystore (eso exige un
        // dispositivo/emulador y queda fuera de un test JVM).
        aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun store() = SecureTokenStore(prefs, aead)

    @Test
    fun `guardar y leer devuelve el mismo par`() {
        val store = store()

        store.save(tokens)

        assertEquals(tokens, store.read())
    }

    @Test
    fun `sin sesion guardada read devuelve null`() {
        assertNull(store().read())
    }

    @Test
    fun `hasSession refleja si hay un par guardado`() {
        val store = store()
        assertFalse(store.hasSession())

        store.save(tokens)

        assertTrue(store.hasSession())
    }

    @Test
    fun `clear borra el par`() {
        val store = store()
        store.save(tokens)

        store.clear()

        assertNull(store.read())
        assertFalse(store.hasSession())
    }

    @Test
    fun `guardar un par nuevo reemplaza al anterior por completo`() {
        val store = store()
        store.save(tokens)

        val nuevo = SessionTokens(accessToken = "access-2", refreshToken = "refresh-2")
        store.save(nuevo)

        assertEquals(nuevo, store.read())
    }

    @Test
    fun `los tokens no viajan en claro en las preferencias`() {
        store().save(tokens)

        val crudo = prefs.getAll().values.joinToString()
        assertFalse("el access token quedó en claro", crudo.contains(tokens.accessToken))
        assertFalse("el refresh token quedó en claro", crudo.contains(tokens.refreshToken))
    }

    @Test
    fun `un valor corrupto se trata como si no hubiera sesion, sin excepcion`() {
        val store = store()
        store.save(tokens)
        // Simula un ciphertext dañado (p. ej. un restore parcial o un downgrade de la app).
        prefs.edit().putString("access_token", "no-es-un-ciphertext-valido").apply()

        assertNull(store.read())
    }

    @Test
    fun `toString de SessionTokens no expone ningun token`() {
        val rendered = tokens.toString()

        assertEquals("SessionTokens(***)", rendered)
        assertFalse(rendered.contains(tokens.accessToken))
        assertFalse(rendered.contains(tokens.refreshToken))
    }
}
