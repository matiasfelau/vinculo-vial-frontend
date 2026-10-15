package ar.felaufernandezbottaro.vinculovial.data.inbox

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Test de contrato contra el Gateway simulado para `GET /alerts/sent` y `PUT /alerts/{id}/seen`:
 * la forma de la request y el parseo de la respuesta. El mapeo a dominio vive en
 * `InboxRepositoryImpl`.
 */
class SentApiTest {

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

        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(AttestationInterceptor(attestationProvider)).build())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(InboxApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -- GET /alerts/sent ---------------------------------------------------------------------

    @Test
    fun `consulta el endpoint del contrato con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getSent("Bearer access-abc")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/alerts/sent", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
    }

    @Test
    fun `sin cursor no agrega query params, y nunca manda limit`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getSent("Bearer access-abc")

        assertEquals("/api/v1/alerts/sent", server.takeRequest().path)
    }

    @Test
    fun `con cursor lo reenvia tal cual, sin interpretarlo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        api.getSent("Bearer access-abc", cursor = "cursor-opaco-b64")

        assertEquals("/api/v1/alerts/sent?cursor=cursor-opaco-b64", server.takeRequest().path)
    }

    @Test
    fun `parsea los cinco campos del contrato y next_cursor`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "items": [
                    {"id": "uuid-1", "alert_type": "CRASHED", "created_at": "2026-10-15T10:00:00Z",
                     "status": "ACTIVE", "seen_at": "2026-10-15T13:41:00Z"},
                    {"id": "uuid-2", "alert_type": "PARKED_ON_GARAGE", "created_at": "2026-10-14T10:00:00Z",
                     "status": "PENDING_VALIDATION", "seen_at": null}
                  ],
                  "next_cursor": "siguiente"
                }
                """.trimIndent(),
            ),
        )

        val body = api.getSent("Bearer access-abc").body()!!

        assertEquals(listOf("uuid-1", "uuid-2"), body.items.map { it.id })
        assertEquals("CRASHED", body.items[0].alertType)
        assertEquals("2026-10-15T10:00:00Z", body.items[0].createdAt)
        assertEquals("ACTIVE", body.items[0].status)
        assertEquals("2026-10-15T13:41:00Z", body.items[0].seenAt)
        assertNull(body.items[1].seenAt)
        assertEquals("siguiente", body.nextCursor)
    }

    @Test
    fun `un item sin seen_at se parsea con seen_at en null`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":"u","alert_type":"CRASHED","created_at":"2026-10-15T10:00:00Z","status":"REJECTED"}],"next_cursor":null}""",
            ),
        )

        assertNull(api.getSent("Bearer access-abc").body()!!.items.single().seenAt)
    }

    /** Anonimato: aunque el servidor mandara de más, el DTO no tiene dónde guardarlo. */
    @Test
    fun `los campos que no son del contrato se ignoran y no llegan al modelo`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"items":[{"id":"u","alert_type":"CRASHED","created_at":"2026-10-15T10:00:00Z","status":"REJECTED",
                 "seen_at":null,"verdict":"RED","reason_codes":["PLATE_MISMATCH"],"owner_ids":["x"],
                 "requires_review":true,"evidence":[{"role":"GENERAL","index":0}]}],"next_cursor":null}
                """.trimIndent(),
            ),
        )

        val item = api.getSent("Bearer access-abc").body()!!.items.single()

        assertEquals("REJECTED", item.status)
        val fields = item.javaClass.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet()
        assertEquals(setOf("id", "alertType", "createdAt", "status", "seenAt"), fields)
    }

    @Test
    fun `una pagina vacia se parsea`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"next_cursor":null}"""))

        val body = api.getSent("Bearer access-abc").body()!!

        assertTrue(body.items.isEmpty())
        assertNull(body.nextCursor)
    }

    // -- PUT /alerts/{id}/seen ----------------------------------------------------------------

    @Test
    fun `la vista es un PUT sin cuerpo a la ruta de la alerta con el bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = api.markSeen("Bearer access-abc", "alerta-1")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/alerts/alerta-1/seen", request.path)
        assertEquals("Bearer access-abc", request.getHeader("Authorization"))
        assertEquals(0L, request.bodySize)
        assertEquals(204, response.code())
    }

    @Test
    fun `el 404 de la vista llega como codigo, sin cuerpo que parsear`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"x","code":"NOT_FOUND"}"""))

        assertEquals(404, api.markSeen("Bearer access-abc", "alerta-1").code())
    }

    @Test
    fun `el inbox parsea seen y lo toma como false si falta`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[
                    {"id":"a","alert_type":"CRASHED","created_at":"2026-10-15T10:00:00Z","seen":true},
                    {"id":"b","alert_type":"CRASHED","created_at":"2026-10-15T10:00:00Z"}
                ],"next_cursor":null}""",
            ),
        )

        val items = api.getInbox("Bearer access-abc").body()!!.items

        assertEquals(listOf(true, false), items.map { it.seen })
    }
}
