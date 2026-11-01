package ar.felaufernandezbottaro.vinculovial.data.alert

import okhttp3.mockwebserver.RecordedRequest

/** Una parte de un multipart/form-data tal como salió por el cable. */
class WirePart(
    val name: String,
    val filename: String?,
    /** El header `Content-Type` de la parte, tal cual (sin interpretar). */
    val contentType: String?,
    val content: ByteArray,
)

/** Parser mínimo de multipart/form-data, binario-seguro (trabaja en ISO-8859-1). */
fun RecordedRequest.parseMultipartWire(): List<WirePart> {
    val boundary = "--" + getHeader("Content-Type").orEmpty().substringAfter("boundary=")
    val text = String(body.readByteArray(), Charsets.ISO_8859_1)

    return text.split(boundary)
        .drop(1)
        .filterNot { it.startsWith("--") }
        .map { section ->
            val content = section.removePrefix("\r\n").removeSuffix("\r\n")
            val headers = content.substringBefore("\r\n\r\n")
            val payload = content.substringAfter("\r\n\r\n")
            WirePart(
                name = Regex("""name="([^"]*)"""").find(headers)?.groupValues?.get(1).orEmpty(),
                filename = Regex("""filename="([^"]*)"""").find(headers)?.groupValues?.get(1),
                contentType = Regex("""Content-Type: ([^\r\n]*)""").find(headers)?.groupValues?.get(1),
                content = payload.toByteArray(Charsets.ISO_8859_1),
            )
        }
}
