package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * El cuerpo de error `{ "error": "<mensaje>", "code": "<CODE>" }` de US-08. `null` si el cuerpo
 * no está o no es ese JSON (p. ej. el 413 o el 429 de Kong, que no llevan `code`): un cuerpo mal
 * formado nunca tumba el manejo del error en sí. El cuerpo se lee una sola vez, así que quien
 * necesite más de un campo (el `code` y el `available_at` del 409) lo pide por acá en vez de
 * llamar a [apiErrorCode] y después a esto.
 */
internal fun Response<*>.apiError(adapter: JsonAdapter<ApiErrorDto>): ApiErrorDto? {
    val raw = try {
        errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    return try {
        raw?.let { adapter.fromJson(it) }
    } catch (e: IOException) {
        null
    } catch (e: JsonDataException) {
        null
    }
}

/**
 * El `code` del cuerpo de error. Nunca se matchea contra `error`, que es texto para mostrar.
 */
internal fun Response<*>.apiErrorCode(adapter: JsonAdapter<ApiErrorDto>): String? = apiError(adapter)?.code

/** El `available_at` (RFC 3339) del 409 `ALERT_DAILY_LIMIT`; `null` si falta o no se puede leer. */
internal fun ApiErrorDto.availableAtInstant(): Instant? =
    try {
        availableAt?.let { OffsetDateTime.parse(it).toInstant() }
    } catch (e: DateTimeParseException) {
        null
    }

internal const val HTTP_CONFLICT = 409
internal const val CODE_ALERT_DAILY_LIMIT = "ALERT_DAILY_LIMIT"
