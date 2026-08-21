package ar.felaufernandezbottaro.vinculovial.data.session

import ar.felaufernandezbottaro.vinculovial.data.remote.auth.RefreshApi
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshResponseDto
import ar.felaufernandezbottaro.vinculovial.data.remote.auth.dto.RefreshTokenRequestDto
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * Registra cada refresh token con el que se la llamó, para poder afirmar en los tests de
 * concurrencia que nunca se reenvía uno ya rotado.
 */
class FakeRefreshApi : RefreshApi {

    var response: Response<RefreshResponseDto> = Response.success(
        RefreshResponseDto(accessToken = "access-nuevo", refreshToken = "refresh-nuevo"),
    )
    var exceptionToThrow: Throwable? = null

    private val counter = AtomicInteger(0)
    val callCount: Int get() = counter.get()

    private val received = mutableListOf<String>()
    val receivedRefreshTokens: List<String>
        get() = synchronized(received) { received.toList() }

    override suspend fun refresh(body: RefreshTokenRequestDto): Response<RefreshResponseDto> {
        counter.incrementAndGet()
        synchronized(received) { received.add(body.refreshToken) }
        exceptionToThrow?.let { throw it }
        return response
    }
}
