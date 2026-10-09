package ar.felaufernandezbottaro.vinculovial.data.alert

import okhttp3.MultipartBody
import okio.Buffer
import retrofit2.Response

class FakeAlertApi : AlertApi {

    var response: Response<Unit> = Response.success(202, Unit)
    var exceptionToThrow: Throwable? = null

    var lastAuthorization: String? = null
        private set
    var lastParts: List<MultipartBody.Part> = emptyList()
        private set
    var callCount = 0
        private set

    override suspend fun sendAlert(authorization: String, parts: List<MultipartBody.Part>): Response<Unit> {
        callCount++
        lastAuthorization = authorization
        lastParts = parts
        exceptionToThrow?.let { throw it }
        return response
    }
}

/** El `name` del `Content-Disposition` de la parte. */
fun MultipartBody.Part.partName(): String {
    val disposition = checkNotNull(headers?.get("Content-Disposition")) { "La parte no tiene Content-Disposition" }
    return checkNotNull(Regex("""name="([^"]*)"""").find(disposition)?.groupValues?.get(1)) { "La parte no tiene name" }
}

fun MultipartBody.Part.partContentType(): String? = body.contentType()?.toString()

fun MultipartBody.Part.partBytes(): ByteArray = Buffer().also { body.writeTo(it) }.readByteArray()
