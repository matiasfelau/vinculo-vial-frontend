package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.ReportRequestDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.FeedbackRequestDto
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.crypto.AttestationTokenGenerator
import ar.felaufernandezbottaro.vinculovial.data.crypto.MockAttestationProvider
import ar.felaufernandezbottaro.vinculovial.data.remote.AttestationInterceptor
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `/alerts/inbox`: sólo la forma de la
 * request y el parseo de la respuesta. El mapeo a resultado de dominio vive en
 * `InboxRepositoryImpl`, no acá.
 */
class InboxApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: InboxApi

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
            .create(InboxApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `consulta el endpoint del contrato con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getInbox("Bearer access-abc")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/alerts/inbox", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `sin cursor no agrega query params`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getInbox("Bearer access-abc")

        assertEquals("/api/v1/alerts/inbox", server.takeRequest().path)
    }

    @Test
    fun `con cursor lo reenvia tal cual, sin interpretarlo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getInbox("Bearer access-abc", cursor = "cursor-opaco-b64")

        assertEquals("/api/v1/alerts/inbox?cursor=cursor-opaco-b64", server.takeRequest().path)
    }

    @Test
    fun `parsea items y next_cursor de una pagina con contenido`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "items": [
                    {"id": "uuid-1", "alert_type": "LUCES_ENCENDIDAS", "created_at": "2026-08-18T10:00:00Z"}
                  ],
                  "next_cursor": "siguiente-cursor"
                }
                """.trimIndent(),
            ),
        )

        val body = api.getInbox("Bearer access-abc").body()!!

        assertEquals(1, body.items.size)
        assertEquals("uuid-1", body.items[0].id)
        assertEquals("LUCES_ENCENDIDAS", body.items[0].alertType)
        assertEquals("2026-08-18T10:00:00Z", body.items[0].createdAt)
        assertEquals("siguiente-cursor", body.nextCursor)
    }

    @Test
    fun `una bandeja vacia es 200 con items vacio y next_cursor null, no un 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        val response = api.getInbox("Bearer access-abc")

        assertEquals(200, response.code())
        assertEquals(emptyList<Any>(), response.body()!!.items)
        assertNull(response.body()!!.nextCursor)
    }

    @Test
    fun `429 no trae cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val response = api.getInbox("Bearer access-abc")

        assertEquals(429, response.code())
    }

    @Test
    fun `parsea los campos de US-08 del item del buzon`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "items": [{
                    "id": "uuid-1", "alert_type": "CRASHED", "created_at": "2026-08-18T10:00:00Z",
                    "requires_review": true,
                    "evidence": [{"role": "GENERAL", "index": 0}, {"role": "PLATE", "index": 0}],
                    "evidence_expires_at": "2026-09-17T10:00:00Z",
                    "my_feedback": "UP",
                    "reported": true
                  }],
                  "next_cursor": null
                }
                """.trimIndent(),
            ),
        )

        val item = api.getInbox("Bearer access-abc").body()!!.items.single()

        assertEquals(true, item.requiresReview)
        assertEquals(listOf("GENERAL" to 0, "PLATE" to 0), item.evidence!!.map { it.role to it.index })
        assertEquals("2026-09-17T10:00:00Z", item.evidenceExpiresAt)
        assertEquals("UP", item.myFeedback)
        assertEquals(true, item.reported)
    }

    @Test
    fun `un item sin los campos de US-08 se parsea con sus defaults`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":"u","alert_type":"CRASHED","created_at":"2026-08-18T10:00:00Z"}],"next_cursor":null}""",
            ),
        )

        val item = api.getInbox("Bearer access-abc").body()!!.items.single()

        assertEquals(false, item.requiresReview)
        assertNull(item.evidence)
        assertNull(item.evidenceExpiresAt)
        assertNull(item.myFeedback)
        assertEquals(false, item.reported)
    }

    @Test
    fun `un item con evidence_expires_at y my_feedback en null se parsea`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":"u","alert_type":"CRASHED","created_at":"2026-08-18T10:00:00Z","evidence":[],"evidence_expires_at":null,"my_feedback":null}],"next_cursor":null}""",
            ),
        )

        val item = api.getInbox("Bearer access-abc").body()!!.items.single()

        assertEquals(emptyList<Any>(), item.evidence)
        assertNull(item.evidenceExpiresAt)
        assertNull(item.myFeedback)
    }

    @Test
    fun `la evidencia se pide por GET con alerta, rol e indice en la ruta y llega como bytes`() = runTest {
        val jpeg = ByteArray(256) { it.toByte() }
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "image/jpeg")
                .setHeader("Cache-Control", "no-store").setBody(okio.Buffer().write(jpeg)),
        )

        val response = api.getEvidence("Bearer access-abc", "alerta-1", "GENERAL", 2)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/alerts/alerta-1/evidence/GENERAL/2", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        org.junit.Assert.assertArrayEquals(jpeg, response.body()!!.bytes())
    }

    @Test
    fun `los segmentos de la ruta de la evidencia se codifican, no se interpretan`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("x"))

        api.getEvidence("Bearer access-abc", "a/b", "RO LE", 0)

        assertEquals("/api/v1/alerts/a%2Fb/evidence/RO%20LE/0", server.takeRequest().path)
    }

    @Test
    fun `la valoracion es un PUT con rating`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = api.putFeedback("Bearer access-abc", "alerta-1", FeedbackRequestDto("DOWN"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/alerts/alerta-1/feedback", request.path)
        assertEquals("""{"rating":"DOWN"}""", request.body.readUtf8())
        assertEquals(204, response.code())
    }

    @Test
    fun `el reporte es un POST con reason`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))

        val response = api.reportSender("Bearer access-abc", "alerta-1", ReportRequestDto("FALSE_ALERT"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/alerts/alerta-1/report", request.path)
        assertEquals("""{"reason":"FALSE_ALERT"}""", request.body.readUtf8())
        assertEquals(202, response.code())
    }
}
