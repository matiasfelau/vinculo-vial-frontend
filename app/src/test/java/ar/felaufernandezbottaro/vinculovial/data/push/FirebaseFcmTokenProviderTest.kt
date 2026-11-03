package ar.felaufernandezbottaro.vinculovial.data.push

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FcmTokenTaskSource] y [FcmTokenDeletionSource] son la única costura hacia `FirebaseMessaging`
 * (clase concreta, sin forma de fakear directo): un lambda que implemente esas interfaces
 * alcanza para sustituirla, sin mockk ni Robolectric. Un [Task] que nunca se completa se
 * construye con [TaskCompletionSource], el mismo tipo que usa el SDK real.
 */
class FirebaseFcmTokenProviderTest {

    private val deleteSucceeds = FcmTokenDeletionSource { Tasks.forResult<Void>(null) }

    @Test
    fun `token exitoso dentro del presupuesto se devuelve tal cual`() = runTest {
        val provider = FirebaseFcmTokenProvider(
            FcmTokenTaskSource { Tasks.forResult("un-token-fcm") },
            deleteSucceeds,
        )

        assertEquals("un-token-fcm", provider.getCurrentToken())
    }

    @Test
    fun `una excepcion del SDK se traduce a null, no se propaga`() = runTest {
        val provider = FirebaseFcmTokenProvider(
            FcmTokenTaskSource { Tasks.forException(IllegalStateException("sin google-services.json")) },
            deleteSucceeds,
        )

        assertNull(provider.getCurrentToken())
    }

    /**
     * El caso que motiva este archivo: antes de este timeout, un `Task` que nunca llama a su
     * callback dejaba este método (y a quien lo espera -- sincronización post-login, logout)
     * esperando para siempre. `runTest` avanza el tiempo virtual: este test corta en
     * milisegundos reales aunque `NETWORK_TIMEOUT_MS` sea de varios segundos.
     */
    @Test
    fun `un Task que nunca resuelve corta por NETWORK_TIMEOUT_MS y devuelve null, no cuelga`() = runTest {
        val provider = FirebaseFcmTokenProvider(
            FcmTokenTaskSource { TaskCompletionSource<String>().task },
            deleteSucceeds,
        )

        assertNull(provider.getCurrentToken())
    }

    private fun providerWithDeletion(source: FcmTokenDeletionSource) =
        FirebaseFcmTokenProvider(FcmTokenTaskSource { Tasks.forResult("un-token-fcm") }, source)

    @Test
    fun `deleteCurrentToken llama a FirebaseMessaging deleteToken y devuelve true`() = runTest {
        var calls = 0
        val provider = providerWithDeletion(
            FcmTokenDeletionSource {
                calls++
                Tasks.forResult<Void>(null)
            },
        )

        assertTrue(provider.deleteCurrentToken())
        assertEquals(1, calls)
    }

    @Test
    fun `una excepcion del SDK al borrar el token se traduce a false, no se propaga`() = runTest {
        val provider = providerWithDeletion(
            FcmTokenDeletionSource { Tasks.forException(IllegalStateException("sin google-services.json")) },
        )

        assertFalse(provider.deleteCurrentToken())
    }

    @Test
    fun `un deleteToken que nunca resuelve corta por NETWORK_TIMEOUT_MS y devuelve false, no cuelga`() = runTest {
        val provider = providerWithDeletion(FcmTokenDeletionSource { TaskCompletionSource<Void>().task })

        assertFalse(provider.deleteCurrentToken())
    }
}
