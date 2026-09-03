package ar.felaufernandezbottaro.vinculovial.data.push

import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.push.FakeFcmTokenProvider
import ar.felaufernandezbottaro.vinculovial.domain.push.FakePushTokenRepository
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenError
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenResult
import ar.felaufernandezbottaro.vinculovial.domain.push.SyncFcmTokenUseCase
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Cubre los casos obligatorios de CLAUDE.md: los tres momentos de sincronización terminan en
 * un `POST`, y `onNewToken` sin sesión activa encola en vez de perder o descartar el token.
 * El manager usa un `CoroutineScope` propio (no `viewModelScope`): acá se le inyecta uno
 * respaldado por `UnconfinedTestDispatcher` para que cada `launch` resuelva en el momento,
 * igual que el resto de los tests de este proyecto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenSyncManagerTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var pendingStore: PendingFcmTokenStore
    private lateinit var repository: FakePushTokenRepository
    private lateinit var fcmTokenProvider: FakeFcmTokenProvider

    private val tokens = SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz")

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        pendingStore = PendingFcmTokenStore(FakeSharedPreferences())
        repository = FakePushTokenRepository()
        fcmTokenProvider = FakeFcmTokenProvider()
    }

    private fun manager(): TokenSyncManager = TokenSyncManager(
        tokenStore = tokenStore,
        pendingStore = pendingStore,
        syncFcmToken = SyncFcmTokenUseCase(repository),
        fcmTokenProvider = fcmTokenProvider,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
    )

    @Test
    fun `arranque con sesion activa sincroniza el token vigente`() = runTest {
        tokenStore.save(tokens)
        fcmTokenProvider.token = "fcm-arranque"

        manager()

        assertEquals(listOf("fcm-arranque"), repository.registeredTokens)
    }

    @Test
    fun `arranque sin sesion no dispara ningun POST`() = runTest {
        manager()

        assertEquals(emptyList<String>(), repository.registeredTokens)
    }

    @Test
    fun `login exitoso sincroniza el token vigente`() = runTest {
        manager()
        fcmTokenProvider.token = "fcm-login"

        tokenStore.save(tokens)

        assertEquals(listOf("fcm-login"), repository.registeredTokens)
    }

    @Test
    fun `onNewToken sin sesion encola el token, no lo pierde ni lo manda`() {
        val syncManager = manager()

        syncManager.onTokenRotated("fcm-rotado-sin-sesion")

        assertEquals(emptyList<String>(), repository.registeredTokens)
        assertEquals("fcm-rotado-sin-sesion", pendingStore.readPendingRegistration())
    }

    @Test
    fun `onNewToken con sesion activa sincroniza el token nuevo`() {
        tokenStore.save(tokens)
        val syncManager = manager()
        // El arranque con sesión activa ya generó un POST con el token por defecto del fake;
        // esto aísla que la rotación posterior dispara uno propio, con el token nuevo.

        syncManager.onTokenRotated("fcm-rotado-con-sesion")

        assertEquals(
            listOf(fcmTokenProvider.token, "fcm-rotado-con-sesion"),
            repository.registeredTokens,
        )
    }

    @Test
    fun `un registro fallido deja el token pendiente para el proximo intento`() {
        repository.registerResult = SyncFcmTokenResult.Failed(SyncFcmTokenError.Network)
        tokenStore.save(tokens)

        manager()

        assertEquals(fcmTokenProvider.token, pendingStore.readPendingRegistration())
    }

    @Test
    fun `un registro exitoso limpia el token pendiente`() {
        tokenStore.save(tokens)

        manager()

        assertNull(pendingStore.readPendingRegistration())
    }

    @Test
    fun `el token pendiente se reintenta en la siguiente sincronizacion, no se pierde`() {
        pendingStore.savePendingRegistration("fcm-pendiente-de-antes")
        tokenStore.save(tokens)
        fcmTokenProvider.token = "fcm-otro-distinto"

        // Simula el próximo arranque: una instancia nueva del manager sobre el mismo store.
        manager()

        assertEquals(listOf("fcm-pendiente-de-antes"), repository.registeredTokens)
    }
}
