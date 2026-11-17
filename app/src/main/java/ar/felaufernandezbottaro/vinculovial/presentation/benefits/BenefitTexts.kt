package ar.felaufernandezbottaro.vinculovial.presentation.benefits

import ar.felaufernandezbottaro.vinculovial.domain.benefits.Benefit
import ar.felaufernandezbottaro.vinculovial.domain.benefits.BenefitError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Los textos de la pantalla de beneficios (US-18), en español rioplatense. */
object BenefitTexts {
    const val SECTION_TITLE = "Beneficios"
    const val GET_CODE = "Obtener código"
    const val COPY = "Copiar"
    const val CODE_COPIED = "Código copiado"
    const val TERMS_TITLE = "Condiciones"
    const val BACK = "Volver"

    const val NO_CODES_LEFT = "Se agotaron los códigos de esta promoción"
    const val NOT_FOUND = "Esta promoción ya no está disponible"
    const val RATE_LIMITED = "Pediste muchos códigos seguidos. Esperá unos minutos y volvé a intentar."
    const val UNAVAILABLE = "No pudimos comunicarnos con el servidor. Intentá de nuevo en unos minutos."
    const val NETWORK = "No pudimos conectarnos. Revisá tu conexión e intentá de nuevo."
    const val UNEXPECTED = "No pudimos obtener el código. Intentá de nuevo en unos minutos."

    /** "Abrir YPF": abre el `open_url` del partner. */
    fun open(partnerName: String): String = "Abrir $partnerName"

    fun enterCodeIn(partnerName: String): String = "Ingresá el código en la app de $partnerName"

    /** La vigencia, siempre en hora argentina: "Hasta el 30/11". */
    fun validUntil(endsAt: Instant, zone: ZoneId = BUENOS_AIRES): String =
        "Hasta el ${endsAt.atZone(zone).format(DAY_MONTH)}"

    fun errorMessage(error: BenefitError): String = when (error) {
        BenefitError.NoCodesLeft -> NO_CODES_LEFT
        BenefitError.NotFound -> NOT_FOUND
        BenefitError.RateLimited -> RATE_LIMITED
        BenefitError.Unavailable -> UNAVAILABLE
        BenefitError.Network -> NETWORK
        BenefitError.Unexpected -> UNEXPECTED
    }

    private val BUENOS_AIRES: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
}

/** Para el lector de pantalla: el banner no tiene texto propio. */
fun Benefit.bannerDescription(): String = "$partnerName: $title"
