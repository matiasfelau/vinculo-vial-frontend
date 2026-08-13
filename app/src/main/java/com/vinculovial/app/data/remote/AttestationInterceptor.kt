package com.vinculovial.app.data.remote

import com.vinculovial.app.data.crypto.AttestationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agrega el veredicto de integridad a cada request. El token se pide por request y se
 * liga al cuerpo, así nunca viaja uno vencido ni reutilizable en otra llamada.
 */
@Singleton
class AttestationInterceptor @Inject constructor(
    private val attestationProvider: AttestationProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Los interceptores de OkHttp son bloqueantes y ya corren fuera del hilo
        // principal, así que acá `runBlocking` es la forma habitual de consumir un
        // proveedor suspendido (Play Integrity expone su API como Task asíncrona).
        val token = runBlocking { attestationProvider.tokenFor(request.bodyBytes()) }

        return chain.proceed(request.newBuilder().header(HEADER, token).build())
    }

    /** Cuerpo ya serializado; es lo que se usa como `requestHash` del token. */
    private fun okhttp3.Request.bodyBytes(): ByteArray {
        val body = body ?: return ByteArray(0)
        return Buffer().use { buffer ->
            body.writeTo(buffer)
            buffer.readByteArray()
        }
    }

    companion object {
        const val HEADER = "X-App-Attestation"
    }
}
