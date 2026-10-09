package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.ReportReason
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.FeedbackRating
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.EvidenceRef
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.EvidenceRefDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.AlertItemDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxPage
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InboxRepositoryImplTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeInboxApi

    @Before
    fun setUp() {
        AeadConfig.register()
        val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        tokenStore = SecureTokenStore(FakeSharedPreferences(), aead)
        tokenStore.save(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeInboxApi()
    }

    private fun repository() = InboxRepositoryImpl(
        api = api,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `manda el access token como bearer`() = runTest {
        repository().getInbox()

        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `reenvia el cursor recibido tal cual, sin interpretarlo`() = runTest {
        repository().getInbox(cursor = "cursor-opaco")

        assertEquals("cursor-opaco", api.lastCursor)
    }

    @Test
    fun `primera carga no manda cursor`() = runTest {
        repository().getInbox()

        assertNull(api.lastCursor)
    }

    @Test
    fun `una pagina vacia es Success con items vacio, nunca un error`() = runTest {
        api.response = Response.success(InboxPageDto(items = emptyList(), nextCursor = null))

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Success(page = InboxPage(emptyList(), null)), result)
    }

    @Test
    fun `mapea items y next_cursor del dto al dominio`() = runTest {
        api.response = Response.success(
            InboxPageDto(
                items = listOf(AlertItemDto(id = "uuid-1", alertType = "CRASHED", createdAt = "2026-08-18T10:00:00Z")),
                nextCursor = "siguiente-cursor",
            ),
        )

        val result = repository().getInbox() as GetInboxResult.Success

        assertEquals(1, result.page.items.size)
        assertEquals("uuid-1", result.page.items[0].id)
        assertEquals("CRASHED", result.page.items[0].alertType)
        assertEquals(Instant.parse("2026-08-18T10:00:00Z"), result.page.items[0].createdAt)
        assertEquals("siguiente-cursor", result.page.nextCursor)
    }

    @Test
    fun `429 es RateLimited`() = runTest {
        api.response = errorResponse(429)

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Failed(InboxError.RateLimited), result)
    }

    @Test
    fun `400 por un cursor mal formado es Unexpected`() = runTest {
        api.response = errorResponse(400)

        val result = repository().getInbox(cursor = "cursor-corrupto")

        assertEquals(GetInboxResult.Failed(InboxError.Unexpected), result)
    }

    @Test
    fun `503 es Unavailable, transitorio`() = runTest {
        api.response = errorResponse(503)

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Failed(InboxError.Unavailable), result)
    }

    @Test
    fun `500 es un error inesperado`() = runTest {
        api.response = errorResponse(500)

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Failed(InboxError.Unexpected), result)
    }

    @Test
    fun `circuito abierto es Unavailable, no Network`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("alerts")

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Failed(InboxError.Unavailable), result)
    }

    @Test
    fun `un fallo de red es Network`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Failed(InboxError.Network), result)
    }

    @Test
    fun `sin sesion guardada no llama a la red`() = runTest {
        tokenStore.clear()

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Failed(InboxError.Unexpected), result)
        assertEquals(0, api.callCount)
    }

    private fun errorResponse(code: Int) =
        Response.error<InboxPageDto>(code, "".toResponseBody("application/json".toMediaType()))

    // -- Campos de US-08 --------------------------------------------------------------------

    @Test
    fun `mapea los campos de US-08 del dto al dominio`() = runTest {
        api.response = Response.success(
            InboxPageDto(
                items = listOf(
                    AlertItemDto(
                        id = "uuid-1",
                        alertType = "CRASHED",
                        createdAt = "2026-08-18T10:00:00Z",
                        requiresReview = true,
                        evidence = listOf(EvidenceRefDto("GENERAL", 0), EvidenceRefDto("DETAIL", 1)),
                        evidenceExpiresAt = "2026-09-17T07:00:00-03:00",
                        myFeedback = "DOWN",
                        reported = true,
                    ),
                ),
                nextCursor = null,
            ),
        )

        val item = (repository().getInbox() as GetInboxResult.Success).page.items.single()

        assertEquals(true, item.requiresReview)
        assertEquals(listOf(EvidenceRef("GENERAL", 0), EvidenceRef("DETAIL", 1)), item.evidence)
        assertEquals(Instant.parse("2026-09-17T10:00:00Z"), item.evidenceExpiresAt)
        assertEquals(FeedbackRating.DOWN, item.myFeedback)
        assertEquals(true, item.reported)
    }

    @Test
    fun `sin los campos de US-08 el item queda sin revision, sin evidencia, sin valoracion y sin reportar`() = runTest {
        api.response = Response.success(
            InboxPageDto(items = listOf(AlertItemDto("u", "CRASHED", "2026-08-18T10:00:00Z")), nextCursor = null),
        )

        val item = (repository().getInbox() as GetInboxResult.Success).page.items.single()

        assertEquals(false, item.requiresReview)
        assertEquals(emptyList<EvidenceRef>(), item.evidence)
        assertNull(item.evidenceExpiresAt)
        assertNull(item.myFeedback)
        assertEquals(false, item.reported)
    }

    @Test
    fun `una valoracion desconocida o un vencimiento ilegible no tumban la lista`() = runTest {
        api.response = Response.success(
            InboxPageDto(
                items = listOf(
                    AlertItemDto("u", "CRASHED", "2026-08-18T10:00:00Z", myFeedback = "MAYBE", evidenceExpiresAt = "manana"),
                ),
                nextCursor = null,
            ),
        )

        val item = (repository().getInbox() as GetInboxResult.Success).page.items.single()

        assertNull(item.myFeedback)
        assertNull(item.evidenceExpiresAt)
    }

    // -- Evidencia --------------------------------------------------------------------------

    @Test
    fun `la evidencia llega como bytes y se pide con alerta, rol e indice`() = runTest {
        api.evidenceResponse = Response.success(byteArrayOf(9, 8, 7).toResponseBody("image/jpeg".toMediaType()))

        val result = repository().getEvidence("alerta-1", "PLATE", 0)

        assertEquals(listOf<Byte>(9, 8, 7), (result as EvidenceResult.Loaded).bytes.toList())
        assertEquals(Triple("alerta-1", "PLATE", 0), api.lastEvidenceCall)
        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `410 en la evidencia es Expired, el placeholder de retencion vencida`() = runTest {
        api.evidenceResponse = errorResponseOf(410)

        assertEquals(EvidenceResult.Expired, repository().getEvidence("alerta-1", "PLATE", 0))
    }

    @Test
    fun `errores de la evidencia se traducen`() = runTest {
        val cases = mapOf(
            404 to InboxError.NotFound,
            429 to InboxError.RateLimited,
            502 to InboxError.Unavailable,
            503 to InboxError.Unavailable,
            504 to InboxError.Unavailable,
            500 to InboxError.Unexpected,
            401 to InboxError.Unexpected,
        )
        cases.forEach { (status, expected) ->
            api.evidenceResponse = errorResponseOf(status)

            assertEquals("status $status", EvidenceResult.Failed(expected), repository().getEvidence("a", "PLATE", 0))
        }
    }

    @Test
    fun `una evidencia 200 sin cuerpo es un error inesperado`() = runTest {
        api.evidenceResponse = Response.success<okhttp3.ResponseBody>(null)

        assertEquals(EvidenceResult.Failed(InboxError.Unexpected), repository().getEvidence("a", "PLATE", 0))
    }

    @Test
    fun `fallas de red en la evidencia`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("alerts")
        assertEquals(EvidenceResult.Failed(InboxError.Unavailable), repository().getEvidence("a", "PLATE", 0))

        api.exceptionToThrow = IOException("sin conexion")
        assertEquals(EvidenceResult.Failed(InboxError.Network), repository().getEvidence("a", "PLATE", 0))
    }

    // -- Valoración -------------------------------------------------------------------------

    @Test
    fun `la valoracion manda UP o DOWN y 204 es Saved`() = runTest {
        FeedbackRating.entries.forEach { rating ->
            assertEquals(FeedbackResult.Saved, repository().setFeedback("alerta-1", rating))
            assertEquals("alerta-1" to rating.name, api.lastFeedback?.let { it.first to it.second.rating })
        }
    }

    @Test
    fun `errores de la valoracion se traducen`() = runTest {
        mapOf(404 to InboxError.NotFound, 429 to InboxError.RateLimited, 503 to InboxError.Unavailable, 500 to InboxError.Unexpected)
            .forEach { (status, expected) ->
                api.feedbackResponse = errorResponseOf(status)

                assertEquals("status $status", FeedbackResult.Failed(expected), repository().setFeedback("a", FeedbackRating.UP))
            }
    }

    // -- Reporte ----------------------------------------------------------------------------

    @Test
    fun `el reporte manda el motivo y 202 es Reported`() = runTest {
        ReportReason.entries.forEach { reason ->
            assertEquals(ReportResult.Reported, repository().reportSender("alerta-1", reason))
            assertEquals("alerta-1" to reason.name, api.lastReport?.let { it.first to it.second.reason })
        }
    }

    @Test
    fun `409 en el reporte es AlreadyReported`() = runTest {
        api.reportResponse = errorResponseOf(409)

        assertEquals(ReportResult.AlreadyReported, repository().reportSender("a", ReportReason.SPAM))
    }

    @Test
    fun `errores del reporte se traducen`() = runTest {
        mapOf(404 to InboxError.NotFound, 429 to InboxError.RateLimited, 503 to InboxError.Unavailable, 500 to InboxError.Unexpected)
            .forEach { (status, expected) ->
                api.reportResponse = errorResponseOf(status)

                assertEquals("status $status", ReportResult.Failed(expected), repository().reportSender("a", ReportReason.SPAM))
            }
    }

    @Test
    fun `sin sesion guardada ninguna operacion llama a la red`() = runTest {
        tokenStore.clear()

        assertEquals(EvidenceResult.Failed(InboxError.Unexpected), repository().getEvidence("a", "PLATE", 0))
        assertEquals(FeedbackResult.Failed(InboxError.Unexpected), repository().setFeedback("a", FeedbackRating.UP))
        assertEquals(ReportResult.Failed(InboxError.Unexpected), repository().reportSender("a", ReportReason.SPAM))
        assertEquals(0, api.callCount)
    }

    private fun <T : Any> errorResponseOf(code: Int): Response<T> =
        Response.error(code, "".toResponseBody("application/json".toMediaType()))
}
