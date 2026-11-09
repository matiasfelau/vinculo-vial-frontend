package ar.felaufernandezbottaro.vinculovial.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * La máquina de estados de la sesión (US-12): cada transición del contrato, con la clave
 * biométrica reemplazada por [FakeSessionKeyVault].
 */
class SecureTokenStoreTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var vault: FakeSessionKeyVault

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")
    private val rotated = SessionTokens(accessToken = "access-2", refreshToken = "refresh-2")

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        vault = FakeSessionKeyVault()
    }

    private fun store() = SecureTokenStore(prefs, vault)

    /** Descifra el blob como lo haría el desbloqueo, con la privada. */
    private fun openBlob(store: SecureTokenStore): String =
        String(vault.createUnlockCipher().doFinal(store.readWrappedRefresh()))

    private fun unlockedWithBiometric(): SecureTokenStore = store().also {
        it.startSession(tokens)
        assertTrue(it.enableBiometric())
    }

    // --- Estado inicial ---

    @Test
    fun `sin blob el estado inicial es NONE`() {
        assertEquals(SessionState.NONE, store().sessionState.value)
    }

    @Test
    fun `con blob guardado el estado inicial es LOCKED y no hay nada en memoria`() {
        unlockedWithBiometric()

        val afterProcessDeath = store()

        assertEquals(SessionState.LOCKED, afterProcessDeath.sessionState.value)
        assertTrue(afterProcessDeath.biometricEnabled.value)
        assertNull(afterProcessDeath.read())
        assertFalse(afterProcessDeath.hasSession())
    }

    @Test
    fun `sin biometria, matar el proceso equivale a perder la sesion`() {
        store().startSession(tokens)

        val afterProcessDeath = store()

        assertEquals(SessionState.NONE, afterProcessDeath.sessionState.value)
        assertNull(afterProcessDeath.read())
    }

    // --- Login ---

    @Test
    fun `el login deja la sesion en UNLOCKED con los tokens en memoria`() {
        val store = store()

        store.startSession(tokens)

        assertEquals(SessionState.UNLOCKED, store.sessionState.value)
        assertEquals(tokens, store.read())
        assertTrue(store.hasSession())
    }

    @Test
    fun `sin biometria no se persiste nada de la sesion`() {
        store().startSession(tokens)

        assertTrue("las preferencias tienen que estar vacías", prefs.getAll().isEmpty())
    }

    @Test
    fun `el login activa el ofrecimiento de biometria una vez`() {
        val store = store()

        store.startSession(tokens)
        assertTrue(store.biometricOfferPending.value)

        store.dismissBiometricOffer()
        assertFalse(store.biometricOfferPending.value)
    }

    @Test
    fun `un login con contrasena borra el blob y la clave anteriores`() {
        unlockedWithBiometric()
        val locked = store().also { it.lock() }
        val deletesBefore = vault.deleteCount

        locked.startSession(rotated)

        assertFalse(locked.biometricEnabled.value)
        assertNull(locked.readWrappedRefresh())
        assertFalse(vault.hasKey)
        assertTrue(vault.deleteCount > deletesBefore)
        assertEquals(SessionState.UNLOCKED, locked.sessionState.value)
    }

    // --- Activar y desactivar la biometría ---

    @Test
    fun `activar la biometria crea la clave y envuelve el refresh actual`() {
        val store = store().also { it.startSession(tokens) }

        assertTrue(store.enableBiometric())

        assertTrue(store.biometricEnabled.value)
        assertEquals(1, vault.createCount)
        assertEquals(tokens.refreshToken, openBlob(store))
        assertFalse("el ofrecimiento se consume", store.biometricOfferPending.value)
    }

    @Test
    fun `el blob se guarda en base64 bajo session_refresh_wrapped y no contiene ningun token en claro`() {
        unlockedWithBiometric()

        val stored = requireNotNull(prefs.getString("session_refresh_wrapped", null))
        Base64.getDecoder().decode(stored)
        val raw = prefs.getAll().toString()
        assertFalse(raw.contains(tokens.refreshToken))
        assertFalse(raw.contains(tokens.accessToken))
        assertEquals(1, prefs.getAll().size)
    }

    @Test
    fun `el access token nunca se persiste`() {
        val store = unlockedWithBiometric()
        store.rotate(rotated)

        assertFalse(prefs.getAll().toString().contains(rotated.accessToken))
        assertFalse(prefs.getAll().toString().contains(tokens.accessToken))
    }

    @Test
    fun `activar la biometria sin sesion desbloqueada no hace nada`() {
        val store = store()

        assertFalse(store.enableBiometric())

        assertEquals(0, vault.createCount)
        assertFalse(store.biometricEnabled.value)
    }

    @Test
    fun `si el dispositivo no puede crear la clave queda todo sin biometria`() {
        val store = store().also { it.startSession(tokens) }
        vault.failOnCreate = true

        assertFalse(store.enableBiometric())

        assertFalse(store.biometricEnabled.value)
        assertNull(store.readWrappedRefresh())
        assertFalse("un intento fallido también consume el ofrecimiento", store.biometricOfferPending.value)
        assertEquals(SessionState.UNLOCKED, store.sessionState.value)
    }

    @Test
    fun `desactivar desde Settings borra blob y clave y la sesion sigue desbloqueada`() {
        val store = unlockedWithBiometric()

        store.disableBiometric()

        assertFalse(store.biometricEnabled.value)
        assertNull(store.readWrappedRefresh())
        assertFalse(vault.hasKey)
        assertEquals(SessionState.UNLOCKED, store.sessionState.value)
        assertEquals(tokens, store.read())
    }

    // --- Rotación ---

    @Test
    fun `con la biometria activa la rotacion vuelve a envolver el refresh nuevo`() {
        val store = unlockedWithBiometric()
        val blobBefore = prefs.getString("session_refresh_wrapped", null)

        store.rotate(rotated)

        assertEquals(rotated, store.read())
        assertNotEquals(blobBefore, prefs.getString("session_refresh_wrapped", null))
        assertEquals(rotated.refreshToken, openBlob(store))
    }

    @Test
    fun `sin biometria la rotacion solo actualiza la memoria`() {
        val store = store().also { it.startSession(tokens) }

        store.rotate(rotated)

        assertEquals(rotated, store.read())
        assertTrue(prefs.getAll().isEmpty())
        assertEquals(0, vault.createCount)
    }

    /**
     * Un blob con un refresh ya rotado sería un reúso al desbloquear, y el servidor lo castiga
     * revocando todas las sesiones: ante un fallo al re-envolver, la biometría se desactiva.
     */
    @Test
    fun `si no se puede volver a envolver se desactiva la biometria en vez de dejar un blob viejo`() {
        val store = unlockedWithBiometric()
        vault.failOnWrap = true

        store.rotate(rotated)

        assertEquals(rotated, store.read())
        assertEquals(SessionState.UNLOCKED, store.sessionState.value)
        assertFalse(store.biometricEnabled.value)
        assertNull(store.readWrappedRefresh())
        assertFalse(vault.hasKey)
    }

    // --- Bloqueo ---

    @Test
    fun `bloquear con biometria deja LOCKED, borra la memoria y conserva el blob`() {
        val store = unlockedWithBiometric()

        store.lock()

        assertEquals(SessionState.LOCKED, store.sessionState.value)
        assertNull(store.read())
        assertFalse(store.hasSession())
        assertEquals(tokens.refreshToken, openBlob(store))
    }

    @Test
    fun `bloquear sin biometria deja NONE`() {
        val store = store().also { it.startSession(tokens) }

        store.lock()

        assertEquals(SessionState.NONE, store.sessionState.value)
        assertNull(store.read())
    }

    // --- Desbloqueo ---

    @Test
    fun `desbloquear deja UNLOCKED y reemplaza el blob por el refresh nuevo`() {
        val store = unlockedWithBiometric().also { it.lock() }

        store.unlockWith(rotated)

        assertEquals(SessionState.UNLOCKED, store.sessionState.value)
        assertEquals(rotated, store.read())
        assertEquals(rotated.refreshToken, openBlob(store))
    }

    // --- Logout, baja y sesión vencida ---

    @Test
    fun `clear borra memoria, blob y clave y deja NONE`() {
        val store = unlockedWithBiometric()

        store.clear()

        assertEquals(SessionState.NONE, store.sessionState.value)
        assertNull(store.read())
        assertNull(store.readWrappedRefresh())
        assertFalse(store.biometricEnabled.value)
        assertFalse(vault.hasKey)
        assertTrue(prefs.getAll().isEmpty())
    }

    @Test
    fun `clear desde LOCKED tambien borra blob y clave`() {
        val store = unlockedWithBiometric().also { it.lock() }

        store.clear()

        assertEquals(SessionState.NONE, store.sessionState.value)
        assertNull(store.readWrappedRefresh())
        assertFalse(vault.hasKey)
    }

    // --- Blob ---

    @Test
    fun `un blob que no es base64 se trata como ausente, sin excepcion`() {
        prefs.edit().putString("session_refresh_wrapped", "%%%no-es-base64%%%").apply()

        assertNull(store().readWrappedRefresh())
    }

    @Test
    fun `toString de SessionTokens no expone ningun token`() {
        val rendered = tokens.toString()

        assertEquals("SessionTokens(***)", rendered)
        assertFalse(rendered.contains(tokens.accessToken))
        assertFalse(rendered.contains(tokens.refreshToken))
    }
}
