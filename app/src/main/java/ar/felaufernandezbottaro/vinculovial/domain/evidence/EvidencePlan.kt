package ar.felaufernandezbottaro.vinculovial.domain.evidence

import ar.felaufernandezbottaro.vinculovial.domain.scan.AlertType

/** Rol de una foto de evidencia: coincide con el `role` del contrato de `vinculo-vial-alert`. */
enum class EvidenceRole {
    GENERAL,
    PLATE,
}

/**
 * Un paso de la captura guiada.
 *
 * @property maxPhotos cuántas fotos pide la captura guiada en el paso. Hoy todos piden una: el
 * contrato de `vinculo-vial-alert` acepta exactamente una foto por rol.
 */
data class EvidenceStep(
    val role: EvidenceRole,
    val instruction: String,
    val maxPhotos: Int = 1,
)

/** Pasos de la captura guiada según el tipo de alerta. */
object EvidencePlan {

    private val general = EvidenceStep(
        role = EvidenceRole.GENERAL,
        instruction = "Sacá una foto del auto completo, con la patente visible",
    )

    private val plate = EvidenceStep(
        role = EvidenceRole.PLATE,
        instruction = "Sacá un primer plano de la patente",
    )

    /** @throws IllegalArgumentException si [alertType] no se ofrece: no tiene captura guiada. */
    fun stepsFor(alertType: AlertType): List<EvidenceStep> {
        require(alertType.isOffered) { "El tipo $alertType no se ofrece, no tiene captura guiada" }

        return when (alertType) {
            AlertType.CRASHED, AlertType.PARKED_ON_GARAGE -> listOf(general, plate)
            AlertType.PARKED_TOO_CLOSE -> error("Inalcanzable: $alertType no se ofrece")
        }
    }
}
