package ar.felaufernandezbottaro.vinculovial.data.push

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [FcmTokenTaskSource] es la única costura hacia `FirebaseMessaging` (clase concreta, sin
 * forma de fakear directo): un lambda que implementa esa interfaz alcanza para sustituirla,
 * sin mockk ni Robolectric. Un [com.google.android.gms.tasks.Task] que nunca se completa se
 * construye con [TaskCompletionSource], el mismo tipo que usa el SDK real.
 */
class FirebaseFcmTokenProviderTest {

    @Test
    fun `token exitoso dentro del presupuesto se devuelve tal cual`() = runTest {
        val provider = FirebaseFcmTokenProvider(FcmTokenTaskSource { Tasks.forResult("un-token-fcm") })

        assertEquals("un-token-fcm", provider.getCurrentToken())
    }

    @Test
    fun `una excepcion del SDK se traduce a null, no se propaga`() = runTest {
        val provider = FirebaseFcmTokenProvider(
            FcmTokenTaskSource { Tasks.forException(IllegalStateException("sin google-services.json")) },
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
        val provider = FirebaseFcmTokenProvider(FcmTokenTaskSource { TaskCompletionSource<String>().task })

        assertNull(provider.getCurrentToken())
    }
}
