package ar.felaufernandezbottaro.vinculovial.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

/**
 * La clave de sesión destruible de la baja de cuenta (US-11). Lo que se protege acá es que la
 * clave que está en memoria y la que está persistida nunca diverjan: si lo hicieran, un login
 * posterior a la baja cifraría con una clave que un reinicio de la app ya no encuentra.
 */
class SessionAeadTest {

    private lateinit var provisioner: FakeSessionKeyProvisioner

    private val associatedData = "access_token".toByteArray()
    private val plaintext = "un-token".toByteArray()

    @Before
    fun setUp() {
        provisioner = FakeSessionKeyProvisioner()
    }

    @Test
    fun `provisiona al construirse, no en el primer uso`() {
        SessionAead(provisioner)

        assertEquals(1, provisioner.provisionCount)
        assertTrue(provisioner.hasPersistedKey)
    }

    @Test
    fun `cifra y descifra con la misma clave`() {
        val aead = SessionAead(provisioner)

        val ciphertext = aead.encrypt(plaintext, associatedData)

        assertEquals("un-token", String(aead.decrypt(ciphertext, associatedData)))
    }

    @Test
    fun `en uso normal no vuelve a provisionar`() {
        val aead = SessionAead(provisioner)

        repeat(5) { aead.decrypt(aead.encrypt(plaintext, associatedData), associatedData) }

        assertEquals(1, provisioner.provisionCount)
    }

    @Test
    fun `destroyKeys borra la clave persistida`() {
        val aead = SessionAead(provisioner)

        aead.destroyKeys()

        assertEquals(1, provisioner.destroyCount)
        assertFalse(provisioner.hasPersistedKey)
    }

    @Test
    fun `lo cifrado antes de destroyKeys no se puede descifrar despues`() {
        val aead = SessionAead(provisioner)
        val ciphertext = aead.encrypt(plaintext, associatedData)

        aead.destroyKeys()

        assertThrows(GeneralSecurityException::class.java) { aead.decrypt(ciphertext, associatedData) }
    }

    @Test
    fun `tras destroyKeys el proximo uso provisiona una clave nueva y la persiste`() {
        val aead = SessionAead(provisioner)
        aead.destroyKeys()
        assertFalse(provisioner.hasPersistedKey)

        aead.encrypt(plaintext, associatedData)

        assertEquals(2, provisioner.provisionCount)
        assertTrue(provisioner.hasPersistedKey)
    }

    /**
     * La regresión que justifica la clase: cifrar tras la baja, "reiniciar" (otro SessionAead
     * sobre el mismo Keystore) y poder leer lo que se cifró.
     */
    @Test
    fun `lo cifrado despues de destroyKeys sobrevive a un reinicio de la app`() {
        val beforeRestart = SessionAead(provisioner)
        beforeRestart.destroyKeys()
        val ciphertext = beforeRestart.encrypt(plaintext, associatedData)

        val afterRestart = SessionAead(provisioner)

        assertEquals("un-token", String(afterRestart.decrypt(ciphertext, associatedData)))
    }

    @Test
    fun `destroyKeys no provisiona una clave nueva hasta que alguien la necesita`() {
        val aead = SessionAead(provisioner)

        aead.destroyKeys()

        assertEquals(1, provisioner.provisionCount)
    }

    @Test
    fun `si destruir falla se propaga el error y la clave en memoria se suelta igual`() {
        val aead = SessionAead(provisioner)
        val ciphertext = aead.encrypt(plaintext, associatedData)
        provisioner.destroyFailure = IllegalStateException("Keystore roto")

        assertThrows(IllegalStateException::class.java) { aead.destroyKeys() }

        // Falló antes de borrar: en disco sigue la misma clave, y al soltar la de memoria el
        // próximo uso la recupera de ahí -- nunca queda una en memoria que el disco no tenga.
        assertTrue(provisioner.hasPersistedKey)
        assertEquals("un-token", String(aead.decrypt(ciphertext, associatedData)))
        assertEquals(2, provisioner.provisionCount)
    }

    @Test
    fun `SecureTokenStore tras destroyKeys no lee lo anterior, guarda lo nuevo y lo conserva tras un reinicio`() {
        val prefs = FakeSharedPreferences()
        val aead = SessionAead(provisioner)
        val store = SecureTokenStore(prefs, aead)
        store.save(SessionTokens("access-viejo", "refresh-viejo"))

        aead.destroyKeys()

        // Los tokens viejos siguen en prefs, pero ya no hay clave para leerlos: es "sin sesión".
        assertNull(store.read())

        val renewed = SessionTokens("access-nuevo", "refresh-nuevo")
        store.save(renewed)

        assertEquals(renewed, store.read())
        assertEquals(renewed, SecureTokenStore(prefs, SessionAead(provisioner)).read())
    }
}
