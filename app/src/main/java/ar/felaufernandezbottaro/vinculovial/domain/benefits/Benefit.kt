package ar.felaufernandezbottaro.vinculovial.domain.benefits

import java.net.URI
import java.net.URISyntaxException
import java.time.Instant

/**
 * Una promoción de un partner (US-18). [code] es `null` mientras el usuario no lo pidió: es un
 * secreto de un solo uso, así que [toString] no lo imprime.
 */
data class Benefit(
    val id: String,
    val partnerName: String,
    val title: String,
    val description: String,
    val terms: String,
    val openUrl: String,
    val endsAt: Instant,
    val code: String?,
) {

    /**
     * `open_url` solo se abre si es `https://` con host: App Link del partner o su ficha de Play
     * Store. Cualquier otra cosa que mande el servidor (`http`, `intent:`, `javascript:`) no se
     * ofrece nunca.
     */
    val canOpen: Boolean
        get() = try {
            val uri = URI(openUrl)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        } catch (e: URISyntaxException) {
            false
        }

    override fun toString(): String =
        "Benefit(id=$id, partnerName=$partnerName, title=$title, endsAt=$endsAt, code=${if (code == null) "null" else "***"})"
}
