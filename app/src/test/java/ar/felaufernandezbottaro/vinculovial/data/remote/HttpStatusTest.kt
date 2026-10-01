package ar.felaufernandezbottaro.vinculovial.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpStatusTest {

    @Test
    fun `502, 503 y 504 son transitorios`() {
        assertTrue(502.isTransientUpstreamFailure())
        assertTrue(503.isTransientUpstreamFailure())
        assertTrue(504.isTransientUpstreamFailure())
    }

    /** 500 es "defecto propio" por contrato, no transitorio -- no se confunde con 502/503/504. */
    @Test
    fun `500 no es transitorio`() {
        assertFalse(500.isTransientUpstreamFailure())
    }

    @Test
    fun `2xx, 4xx y codigos fuera de rango no son transitorios`() {
        listOf(200, 201, 400, 401, 403, 404, 409, 429, 501, 505, 0, -1).forEach { code ->
            assertFalse("esperaba que $code no fuera transitorio", code.isTransientUpstreamFailure())
        }
    }
}
