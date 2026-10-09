package ar.felaufernandezbottaro.vinculovial.domain.evidence

import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType

/** Rol de una foto de evidencia: coincide con el `role` del contrato de `vinculo-vial-alert`. */
enum class EvidenceRole {
    GENERAL,
    PLATE,
    DETAIL,
    CONTEXT,
}

/**
 * Un paso de la captura guiada.
 *
 * @property maxPhotos cuántas fotos admite el paso: sólo `DETAIL` admite más de una (hasta 3).
 * @property overlayHint texto del encuadre guiado que se dibuja sobre la cámara, si el paso lo tiene.
 */
data class EvidenceStep(
    val role: EvidenceRole,
    val instruction: String,
    val maxPhotos: Int = 1,
    val overlayHint: String? = null,
)

/** Pasos de la captura guiada según el tipo de alerta. */
object EvidencePlan {

    const val MAX_DETAIL_PHOTOS = 3

    private val general = EvidenceStep(
        role = EvidenceRole.GENERAL,
        instruction = "Sacá una foto del auto completo, con la patente visible",
    )

    private val plate = EvidenceStep(
        role = EvidenceRole.PLATE,
        instruction = "Sacá un primer plano de la patente",
    )

    private val detail = EvidenceStep(
        role = EvidenceRole.DETAIL,
        instruction = "Sacá un primer plano del daño",
        maxPhotos = MAX_DETAIL_PHOTOS,
    )

    private val context = EvidenceStep(
        role = EvidenceRole.CONTEXT,
        instruction = "Sacá la foto desde la vereda",
        overlayHint = "Que entren el rebaje y la rueda más cercana",
    )

    /** @throws IllegalArgumentException si [alertType] no se ofrece: no tiene captura guiada. */
    fun stepsFor(alertType: AlertType): List<EvidenceStep> {
        require(alertType.isOffered) { "El tipo $alertType no se ofrece, no tiene captura guiada" }

        return when (alertType) {
            AlertType.CRASHED -> listOf(general, plate, detail)
            AlertType.PARKED_ON_GARAGE -> listOf(general, plate, context)
            AlertType.PARKED_TOO_CLOSE -> error("Inalcanzable: $alertType no se ofrece")
        }
    }
}
