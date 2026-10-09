package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `POST /alerts`: la forma del multipart en el
 * cable (partes, `Content-Type` de cada una y bytes binarios intactos). Qué partes lleva cada
 * tipo y el mapeo de respuestas viven en `AlertRepositoryImpl`, no acá.
 */
class AlertApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AlertApi

    @Before
    fun setUp() {
        val attestationProvider: AttestationProvider = MockAttestationProvider(
            AttestationTokenGenerator(
                psk = "psk-de-desarrollo-de-al-menos-32-bytes",
                ttlSeconds = 60,
                packageName = "ar.felaufernandezbottaro.vinculovial",
            ),
        )

        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .addInterceptor(AttestationInterceptor(attestationProvider))
            .build()

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(AlertApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Todos los valores de byte, para que ninguna transformación de texto pase desapercibida. */
    private val binary = ByteArray(256) { it.toByte() }

    private val parts = listOf(
        MultipartBody.Part.createFormData(
            "metadata",
            null,
            """{"attempt_id":"a-1","alert_type":"CRASHED"}""".toByteArray().toRequestBody("application/json".toMediaType()),
        ),
        MultipartBody.Part.createFormData(
            "general",
            "general-0.bin",
            binary.toRequestBody("application/octet-stream".toMediaType()),
        ),
        MultipartBody.Part.createFormData(
            "detail",
            "detail-0.bin",
            binary.toRequestBody("application/octet-stream".toMediaType()),
        ),
    )

    @Test
    fun `publica en el endpoint del contrato con el bearer token y como multipart`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        api.sendAlert("Bearer access-abc", parts)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/alerts", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
    }

    @Test
    fun `las partes salen en orden con el content-type del contrato`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        api.sendAlert("Bearer access-abc", parts)

        val wire = parseMultipart(server.takeRequest().let { it.getHeader("Content-Type").orEmpty() to it.body.readByteArray() })
        assertEquals(listOf("metadata", "general", "detail"), wire.map { it.name })
        assertEquals(
            listOf("application/json", "application/octet-stream", "application/octet-stream"),
            wire.map { it.contentType },
        )
    }

    @Test
    fun `los bytes binarios viajan intactos y el JSON de metadata tambien`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        api.sendAlert("Bearer access-abc", parts)

        val wire = parseMultipart(server.takeRequest().let { it.getHeader("Content-Type").orEmpty() to it.body.readByteArray() })
        assertEquals("""{"attempt_id":"a-1","alert_type":"CRASHED"}""", String(wire[0].content, Charsets.UTF_8))
        assertArrayEquals(binary, wire[1].content)
        assertArrayEquals(binary, wire[2].content)
    }

    @Test
    fun `202 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"alert_id":"a-1","status":"PENDING_VALIDATION"}"""))

        val response = api.sendAlert("Bearer access-abc", parts)

        assertEquals(202, response.code())
    }

    @Test
    fun `400 con code no se parsea acá`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"x","code":"INVALID_EVIDENCE"}"""))

        val response = api.sendAlert("Bearer access-abc", parts)

        assertEquals(400, response.code())
    }

    private class WirePart(val name: String, val contentType: String?, val content: ByteArray)

    /** Parser mínimo de multipart/form-data, binario-seguro (trabaja en ISO-8859-1). */
    private fun parseMultipart(request: Pair<String, ByteArray>): List<WirePart> {
        val (contentType, body) = request
        val boundary = "--" + contentType.substringAfter("boundary=")
        val text = String(body, Charsets.ISO_8859_1)

        return text.split(boundary)
            .drop(1)
            .filterNot { it.startsWith("--") }
            .map { section ->
                val content = section.removePrefix("\r\n").removeSuffix("\r\n")
                val headers = content.substringBefore("\r\n\r\n")
                val payload = content.substringAfter("\r\n\r\n")
                WirePart(
                    name = Regex("""name="([^"]*)"""").find(headers)?.groupValues?.get(1).orEmpty(),
                    contentType = Regex("""Content-Type: ([^\r\n]*)""").find(headers)?.groupValues?.get(1),
                    content = payload.toByteArray(Charsets.ISO_8859_1),
                )
            }
    }
}
