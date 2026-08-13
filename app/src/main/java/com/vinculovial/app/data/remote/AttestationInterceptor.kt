package com.vinculovial.app.data.remote

import com.vinculovial.app.data.crypto.AttestationTokenGenerator
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agrega el mock de App Attestation a cada request. El token se genera por request
 * (TTL de un minuto), así nunca viaja uno vencido ni se reutiliza entre llamadas.
 */
@Singleton
class AttestationInterceptor @Inject constructor(
    private val tokenGenerator: AttestationTokenGenerator,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER, tokenGenerator.generate())
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER = "X-App-Attestation"
    }
}
