package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.AlertItemDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.SentAlertDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.SentPageDto
import ar.felaufernandezbottaro.vinculovial.data.remote.CircuitBreakerOpenException
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSessionKeyVault
import ar.felaufernandezbottaro.vinculovial.data.session.FakeSharedPreferences
import ar.felaufernandezbottaro.vinculovial.data.session.SecureTokenStore
import ar.felaufernandezbottaro.vinculovial.data.session.SessionTokens
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetInboxResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.GetSentResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.InboxError
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SeenResult
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlert
import ar.felaufernandezbottaro.vinculovial.domain.inbox.SentAlertStatus
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

/** `getSent` y `markSeen` del repositorio: mapeo a dominio y traducción de cada respuesta. */
@OptIn(ExperimentalCoroutinesApi::class)
class SentRepositoryImplTest {

    private lateinit var tokenStore: SecureTokenStore
    private lateinit var api: FakeInboxApi

    @Before
    fun setUp() {
        tokenStore = SecureTokenStore(FakeSharedPreferences(), FakeSessionKeyVault())
        tokenStore.startSession(SessionTokens(accessToken = "access-abc", refreshToken = "refresh-xyz"))
        api = FakeInboxApi()
    }

    private fun repository() = InboxRepositoryImpl(
        api = api,
        tokenStore = tokenStore,
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun dto(id: String = "uuid-1", status: String = "ACTIVE", seenAt: String? = null) = SentAlertDto(
        id = id,
        alertType = "CRASHED",
        createdAt = "2026-10-15T10:00:00Z",
        status = status,
        seenAt = seenAt,
    )

    // -- getSent ------------------------------------------------------------------------------

    @Test
    fun `manda el access token como bearer y reenvia el cursor tal cual`() = runTest {
        repository().getSent(cursor = "cursor-opaco")

        assertEquals("Bearer access-abc", api.lastAuthorization)
        assertEquals("cursor-opaco", api.lastSentCursor)
    }

    @Test
    fun `la primera carga no manda cursor`() = runTest {
        repository().getSent()

        assertNull(api.lastSentCursor)
    }

    @Test
    fun `mapea los cinco campos y el cursor del dto al dominio`() = runTest {
        api.sentResponse = Response.success(
            SentPageDto(items = listOf(dto(seenAt = "2026-10-15T10:41:00-03:00")), nextCursor = "siguiente"),
        )

        val page = (repository().getSent() as GetSentResult.Success).page

        assertEquals(
            SentAlert(
                id = "uuid-1",
                alertType = "CRASHED",
                createdAt = Instant.parse("2026-10-15T10:00:00Z"),
                status = SentAlertStatus.ACTIVE,
                seenAt = Instant.parse("2026-10-15T13:41:00Z"),
            ),
            page.items.single(),
        )
        assertEquals("siguiente", page.nextCursor)
    }

    @Test
    fun `mapea cada status del contrato`() = runTest {
        api.sentResponse = Response.success(
            SentPageDto(
                items = listOf(
                    dto("a", "PENDING_VALIDATION"),
                    dto("b", "ACTIVE"),
                    dto("c", "REJECTED"),
                    dto("d", "EXPIRED"),
                ),
                nextCursor = null,
            ),
        )

        val items = (repository().getSent() as GetSentResult.Success).page.items

        assertEquals(
            listOf(
                SentAlertStatus.PENDING_VALIDATION,
                SentAlertStatus.ACTIVE,
                SentAlertStatus.REJECTED,
                SentAlertStatus.EXPIRED,
            ),
            items.map { it.status },
        )
    }

    /** Un estado que la app no conoce no se adivina: afirmar "En validación" o "Validada" sería inventar. */
    @Test
    fun `un status desconocido se descarta en vez de adivinarse`() = runTest {
        api.sentResponse = Response.success(
            SentPageDto(items = listOf(dto("a", "ESTADO_NUEVO"), dto("b", "ACTIVE")), nextCursor = null),
        )

        val items = (repository().getSent() as GetSentResult.Success).page.items

        assertEquals(listOf("b"), items.map { it.id })
    }

    @Test
    fun `un seen_at ilegible no tumba la lista y queda como no leida`() = runTest {
        api.sentResponse = Response.success(SentPageDto(items = listOf(dto(seenAt = "ayer")), nextCursor = null))

        val item = (repository().getSent() as GetSentResult.Success).page.items.single()

        assertNull(item.seenAt)
    }

    @Test
    fun `un created_at ilegible es un error inesperado`() = runTest {
        api.sentResponse = Response.success(
            SentPageDto(items = listOf(dto().copy(createdAt = "no-es-una-fecha")), nextCursor = null),
        )

        assertEquals(GetSentResult.Failed(InboxError.Unexpected), repository().getSent())
    }

    @Test
    fun `una pagina vacia es Success con items vacio, nunca un error`() = runTest {
        val result = repository().getSent() as GetSentResult.Success

        assertEquals(emptyList<SentAlert>(), result.page.items)
        assertNull(result.page.nextCursor)
    }

    @Test
    fun `cada codigo de error de la lista se traduce`() = runTest {
        mapOf(
            429 to InboxError.RateLimited,
            502 to InboxError.Unavailable,
            503 to InboxError.Unavailable,
            504 to InboxError.Unavailable,
            400 to InboxError.Unexpected,
            401 to InboxError.Unexpected,
            500 to InboxError.Unexpected,
        ).forEach { (status, expected) ->
            api.sentResponse = errorResponseOf(status)

            assertEquals("status $status", GetSentResult.Failed(expected), repository().getSent())
        }
    }

    @Test
    fun `circuito abierto es Unavailable, un fallo de red es Network y lo inesperado es Unexpected`() = runTest {
        api.exceptionToThrow = CircuitBreakerOpenException("sent")
        assertEquals(GetSentResult.Failed(InboxError.Unavailable), repository().getSent())

        api.exceptionToThrow = IOException("sin conexion")
        assertEquals(GetSentResult.Failed(InboxError.Network), repository().getSent())

        api.exceptionToThrow = IllegalStateException("boom")
        assertEquals(GetSentResult.Failed(InboxError.Unexpected), repository().getSent())
    }

    // -- markSeen -----------------------------------------------------------------------------

    @Test
    fun `204 de la vista es Marked y manda alerta y bearer`() = runTest {
        assertEquals(SeenResult.Marked, repository().markSeen("alerta-1"))

        assertEquals("alerta-1", api.lastSeenAlertId)
        assertEquals("Bearer access-abc", api.lastAuthorization)
    }

    @Test
    fun `cada codigo de error de la vista se traduce`() = runTest {
        mapOf(
            404 to InboxError.NotFound,
            429 to InboxError.RateLimited,
            503 to InboxError.Unavailable,
            500 to InboxError.Unexpected,
        ).forEach { (status, expected) ->
            api.seenResponse = errorResponseOf(status)

            assertEquals("status $status", SeenResult.Failed(expected), repository().markSeen("a"))
        }
    }

    @Test
    fun `la vista traduce las excepciones de red`() = runTest {
        api.exceptionToThrow = IOException("sin conexion")
        assertEquals(SeenResult.Failed(InboxError.Network), repository().markSeen("a"))

        api.exceptionToThrow = CircuitBreakerOpenException("seen")
        assertEquals(SeenResult.Failed(InboxError.Unavailable), repository().markSeen("a"))
    }

    @Test
    fun `sin sesion guardada ni la lista ni la vista llaman a la red`() = runTest {
        tokenStore.clear()

        assertEquals(GetSentResult.Failed(InboxError.Unexpected), repository().getSent())
        assertEquals(SeenResult.Failed(InboxError.Unexpected), repository().markSeen("a"))
        assertEquals(0, api.callCount)
    }

    // -- seen en el inbox ---------------------------------------------------------------------

    @Test
    fun `el inbox mapea seen al dominio`() = runTest {
        api.response = Response.success(
            InboxPageDto(
                items = listOf(
                    AlertItemDto("a", "CRASHED", "2026-10-15T10:00:00Z", seen = true),
                    AlertItemDto("b", "CRASHED", "2026-10-15T10:00:00Z"),
                ),
                nextCursor = null,
            ),
        )

        val items = (repository().getInbox() as GetInboxResult.Success).page.items

        assertEquals(listOf(true, false), items.map { it.seen })
    }

    private fun <T : Any> errorResponseOf(code: Int): Response<T> =
        Response.error(code, "".toResponseBody("application/json".toMediaType()))
}
