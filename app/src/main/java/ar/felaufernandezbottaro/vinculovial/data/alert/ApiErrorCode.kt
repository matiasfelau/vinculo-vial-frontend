package ar.felaufernandezbottaro.vinculovial.data.alert

import ar.felaufernandezbottaro.vinculovial.data.alert.dto.ApiErrorDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import retrofit2.Response
import java.io.IOException

/**
 * El `code` del cuerpo de error `{ "error": "<mensaje>", "code": "<CODE>" }` de US-08. `null` si
 * el cuerpo no está o no es ese JSON (p. ej. el 413 o el 429 de Kong, que no llevan `code`): un
 * cuerpo mal formado nunca tumba el manejo del error en sí. Nunca se matchea contra `error`,
 * que es texto para mostrar.
 */
internal fun Response<*>.apiErrorCode(adapter: JsonAdapter<ApiErrorDto>): String? {
    val raw = try {
        errorBody()?.string()
    } catch (e: IOException) {
        null
    }
    return try {
        raw?.let { adapter.fromJson(it) }?.code
    } catch (e: IOException) {
        null
    } catch (e: JsonDataException) {
        null
    }
}
