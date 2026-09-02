package ar.felaufernandezbottaro.vinculovial.data.inbox

import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.AlertItemDto
import ar.felaufernandezbottaro.vinculovial.data.inbox.dto.InboxPageDto
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
    fun `500 es un error inesperado`() = runTest {
        api.response = errorResponse(500)

        val result = repository().getInbox()

        assertEquals(GetInboxResult.Failed(InboxError.Unexpected), result)
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
}
