package ar.felaufernandezbottaro.vinculovial.data.crypto

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityDialogRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [StandardIntegrityManager] y [StandardIntegrityTokenProvider] son interfaces del SDK de
 * Play Integrity: un objeto que las implemente alcanza para sustituirlas acá, sin mockk ni
 * Robolectric. Un [Task] que nunca se completa se construye con [TaskCompletionSource], el
 * mismo tipo que usa el SDK real -- no hace falta un Play Services corriendo de verdad.
 *
 * La rama de reintento por proveedor caducado (`StandardIntegrityException` con
 * `INTEGRITY_TOKEN_PROVIDER_INVALID`) queda sin cubrir acá: esa excepción sólo tiene un
 * constructor, package-private dentro de `com.google.android.play.core.integrity`, así que
 * no hay forma de instanciarla desde este paquete sin mockk/Robolectric.
 */
class PlayIntegrityAttestationProviderTest {

    private val requestBody = "hola".toByteArray()

    @Test
    fun `token exitoso dentro del presupuesto se devuelve tal cual`() = runTest {
        val manager = FakeIntegrityManager { Tasks.forResult(FakeTokenProvider("token-de-play-integrity")) }
        val provider = PlayIntegrityAttestationProvider(manager, cloudProjectNumber = 123L)

        assertEquals("token-de-play-integrity", provider.tokenFor(requestBody))
    }

    @Test
    fun `el proveedor preparado se cachea -- una segunda llamada no vuelve a prepararlo`() = runTest {
        val manager = FakeIntegrityManager { Tasks.forResult(FakeTokenProvider("token")) }
        val provider = PlayIntegrityAttestationProvider(manager, cloudProjectNumber = 123L)

        provider.tokenFor(requestBody)
        provider.tokenFor(requestBody)

        assertEquals(1, manager.prepareCallCount)
    }

    @Test
    fun `sin cloudProjectNumber configurado falla sin llamar al manager`() = runTest {
        val manager = FakeIntegrityManager { Tasks.forResult(FakeTokenProvider("no debería usarse")) }
        val provider = PlayIntegrityAttestationProvider(manager, cloudProjectNumber = 0L)

        val exception = provider.tokenForOrNull(requestBody)

        assertEquals(AttestationUnavailableException.Reason.MISCONFIGURED, exception?.reason)
        assertEquals(0, manager.prepareCallCount)
    }

    /**
     * El caso que motiva este archivo: antes de `ATTESTATION_TIMEOUT_MS`, un `Task` que
     * nunca llama a su callback dejaba `runBlocking` en `AttestationInterceptor` esperando
     * para siempre -- ni `connectTimeout` ni `readTimeout` de OkHttp lo cubren, porque esto
     * corre antes de abrir el socket. `runTest` avanza el tiempo virtual: este test corta
     * en milisegundos reales aunque `ATTESTATION_TIMEOUT_MS` sea de varios segundos.
     */
    @Test
    fun `un Task que nunca resuelve corta por ATTESTATION_TIMEOUT_MS, no cuelga indefinidamente`() = runTest {
        val neverCompletes = TaskCompletionSource<StandardIntegrityTokenProvider>().task
        val manager = FakeIntegrityManager { neverCompletes }
        val provider = PlayIntegrityAttestationProvider(manager, cloudProjectNumber = 123L)

        val exception = provider.tokenForOrNull(requestBody)

        assertEquals(AttestationUnavailableException.Reason.TRANSIENT, exception?.reason)
    }

    private suspend fun PlayIntegrityAttestationProvider.tokenForOrNull(
        requestBody: ByteArray,
    ): AttestationUnavailableException? = try {
        tokenFor(requestBody)
        null
    } catch (e: AttestationUnavailableException) {
        e
    }

    private class FakeIntegrityManager(
        private val prepareResult: () -> Task<StandardIntegrityTokenProvider>,
    ) : StandardIntegrityManager {
        var prepareCallCount = 0
            private set

        override fun prepareIntegrityToken(
            request: PrepareIntegrityTokenRequest,
        ): Task<StandardIntegrityTokenProvider> {
            prepareCallCount++
            return prepareResult()
        }

        override fun showDialog(request: StandardIntegrityDialogRequest): Task<Int> =
            throw UnsupportedOperationException("no usado por esta app")
    }

    private class FakeTokenProvider(private val resultToken: String) : StandardIntegrityTokenProvider {
        override fun request(request: StandardIntegrityTokenRequest): Task<StandardIntegrityToken> =
            Tasks.forResult(FakeToken(resultToken))
    }

    private class FakeToken(private val value: String) : StandardIntegrityToken() {
        override fun token(): String = value

        // Deprecado en el SDK y nunca ejercitado por esta app -- sólo satisface el método
        // abstracto para poder instanciar el fake.
        @Suppress("OVERRIDE_DEPRECATION")
        override fun showDialog(activity: Activity, requestCode: Int): Task<Int> =
            throw UnsupportedOperationException("no usado por esta app")
    }
}
